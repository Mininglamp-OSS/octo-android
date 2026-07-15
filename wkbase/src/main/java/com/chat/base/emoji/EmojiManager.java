/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import androidx.collection.LruCache;

import com.chat.base.BuildConfig;
import com.chat.base.WKBaseApplication;
import com.chat.base.common.WKCommonModel;
import com.chat.base.utils.WKLogUtils;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class EmojiManager {

    private static final String TAG = "EmojiManager";
    private final String EMOT_DIR = "emoji/";

    // max cache size
    private final int CACHE_MAX_SIZE = 1024;

    // Hot-path 字段全部 volatile：applyManifest 通过 copy-on-write 构建新引用后原子 swap，
    // 消费端（MoonUtil / WKTextProvider / SelectTextHelper / WKUIChatMsgItemEntity）
    // 无锁读安全。原 field 直接 mutate 的模式在只 init 一次时安全，但 refreshFromServer
    // 引入后会与读者并发，必须改成不可变引用替换。
    private volatile Pattern pattern;
    private volatile Map<String, Entry> text2entry = Collections.emptyMap();
    private volatile List<Entry> defaultEntries = Collections.emptyList();
    private volatile String currentSig = "";

    //  (P-04) — init() 幂等标记，供 ensureInitialized() 使用。
    private volatile boolean initialized = false;

    // 内置 xml 加载的一份原始副本（applyManifest 的 base），init 后不再改。
    private Map<String, Entry> xmlText2entry = Collections.emptyMap();
    private List<Entry> xmlDefaultEntries = Collections.emptyList();

    // asset bitmap cache, key: asset path
    private LruCache<String, Bitmap> drawableCache;

    private EmojiManager() {

    }

    private static class EmojiManagerBinder {
        final static EmojiManager emoji = new EmojiManager();
    }

    public static EmojiManager getInstance() {
        return EmojiManagerBinder.emoji;
    }

    /**
     *  (P-04) · 空载 / 重复调用幂等化。
     *
     * 旧路径是 {@code WKBaseApplication.init} 里把本方法塞进同一个 {@code new Thread()}
     * 跟 Bugly 抢 CPU。新 AppStartup 把它挪到 Phase-C（idle 后），
     * 但文本渲染（{@code WKTextProvider}、{@code MoonUtil}、{@code SelectTextHelper}、
     * {@code WKUIChatMsgItemEntity}）在 Phase-C 之前就可能读 {@link #getPattern()}，
     * 所以这里做 double-checked locking：
     *
     * <ul>
     *   <li>Phase-C 主动调用一次 {@link #init()}；</li>
     *   <li>任何 hot path 在需要 {@code pattern} / entries 之前调用
     *       {@link #ensureInitialized()}，若 Phase-C 还没跑就同步补齐，
     *       否则直接 no-op。</li>
     * </ul>
     */
    public synchronized void init() {
        if (initialized) {
            return;
        }
        Context context = WKBaseApplication.getInstance().getContext();
        if (context == null) {
            // Application 还没 attach（极罕见）——下一次再补。
            return;
        }

        // 1) 从 xml 加载出内置真源（保留一份不可变副本，applyManifest 时以此为 base）
        List<Entry> xmlEntries = new ArrayList<>();
        Map<String, Entry> xmlMap = new LinkedHashMap<>();
        new EntryLoader(xmlMap, xmlEntries).load(context, EMOT_DIR + "emoji.xml");
        xmlDefaultEntries = Collections.unmodifiableList(xmlEntries);
        xmlText2entry = Collections.unmodifiableMap(xmlMap);

        // 2) 首屏立即可用：先按 xml 状态跑一次 applyManifest（无 manifest = 纯 xml）
        applyManifestInternal(Collections.<EmojiManifestItem>emptyList());

        // 3) SP 里若有上次缓存的 manifest，立刻 apply 一次（避免"首屏没有服务端新增表情"）
        EmojiManifestResp cached = EmojiManifestCache.load();
        if (cached != null && cached.list != null) {
            applyManifestInternal(EmojiManifestSanitizer.sanitize(cached.list));
        }

        drawableCache = new LruCache<String, Bitmap>(CACHE_MAX_SIZE) {
            @Override
            protected void entryRemoved(boolean evicted, @NotNull String key, @NotNull Bitmap oldValue, Bitmap newValue) {
                if (oldValue != newValue)
                    oldValue.recycle();
            }
        };
        initialized = true;
    }

    /**
     * Hot path 守卫：文本渲染 / emoji 面板在 Phase-C 之前就可能触达 EmojiManager，
     * 该方法用 double-checked locking 做懒初始化。常态（已初始化）下是一个 volatile 读。
     */
    public void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    /**
     * 拉取服务端最新 emoji 清单，成功后合并进 text2entry + defaultEntries 并重建 pattern。
     * fire-and-forget 语义——失败保留当前状态（xml + 上次缓存），静默 log。
     *
     * <p>调用时机：{@link com.chat.base.WKBaseApplication} 的 {@code AppStartup.postPhaseC}
     * 里 {@link #init()} 后紧跟一次。运行时其它场景不主动重刷（服务端 clean install 稀有事件，
     * 冷启动一次覆盖足够；后续用户看到的表情池自然是"当前 session 起点 + 未来的合并"）。
     */
    public void refreshFromServer() {
        ensureInitialized();
        WKCommonModel.getInstance().getEmojis(new WKCommonModel.IEmojiManifest() {
            @Override
            public void onResult(EmojiManifestResp manifest) {
                if (manifest == null || manifest.list == null) {
                    // 网络失败 / 反序列化失败——保留当前状态。呼叫方无 UI 需要通知。
                    return;
                }
                List<EmojiManifestItem> clean = EmojiManifestSanitizer.sanitize(manifest.list);
                if (clean.isEmpty()) {
                    // 服务端理论上不会下发空 list（parseEmojiManifest 会 reject），
                    // 走到这里意味着 sanitize 把所有条目都 drop 了——保留当前状态更安全。
                    return;
                }
                boolean changed = applyManifestInternal(clean);
                if (changed) {
                    EmojiManifestCache.save(manifest);
                }
            }
        });
    }

    /**
     * 合并 manifest 到内部数据结构（copy-on-write），返回是否发生实际变化。
     * 变化用 sig（内容签名）判断——服务端下发跟本地 sig 一致时短路，避免无谓重建 pattern。
     *
     * <p>合并策略（merge, 不删）：
     * <ol>
     *   <li>base = 内置 xml 全部条目</li>
     *   <li>manifest 中的 key：若 xml 里已有同 key，用 xml 的 id + assetPath；若无（未来新增），
     *       id 走 {@link #deriveIdFromKey(String)}，assetPath = null，remoteUrl = item.url</li>
     *   <li>defaultEntries 排序：manifest customs 按 manifest 顺序在前 → xml customs 未在
     *       manifest 的（保留兜底）→ 全部非 custom（Unicode）按 xml 顺序在后</li>
     * </ol>
     */
    synchronized boolean applyManifestInternal(List<EmojiManifestItem> sanitizedItems) {
        String newSig = signatureOf(sanitizedItems);
        if (newSig.equals(currentSig) && !text2entry.isEmpty()) {
            return false;
        }

        // 1) 起点是 xml 的完整副本（可变）
        Map<String, Entry> newMap = new LinkedHashMap<>(xmlText2entry);

        // 2) manifest 覆盖同 key（url 空则退化成 xml 已有 asset；url 非空则记 remoteUrl）
        //    manifest 新 key 直接加进 map（xml 里没有）
        List<Entry> manifestCustoms = new ArrayList<>(sanitizedItems.size());
        Set<String> seenManifestKeys = new HashSet<>();
        for (EmojiManifestItem item : sanitizedItems) {
            Entry existing = xmlText2entry.get(item.key);
            String id = existing != null ? existing.id : deriveIdFromKey(item.key);
            String assetPath = existing != null ? existing.assetPath : null;
            String remoteUrl = item.url == null ? "" : item.url;
            String name = item.name;
            Entry merged = new Entry(id, item.key, assetPath, remoteUrl, name);
            newMap.put(item.key, merged);
            manifestCustoms.add(merged);
            seenManifestKeys.add(item.key);
        }

        // 3) defaultEntries 排序：manifest customs 在前 → xml-only customs → Unicode
        List<Entry> newDefaults = new ArrayList<>(manifestCustoms.size() + xmlDefaultEntries.size());
        newDefaults.addAll(manifestCustoms);
        for (Entry e : xmlDefaultEntries) {
            boolean isCustom = e.id != null && e.id.startsWith("custom_");
            if (isCustom && !seenManifestKeys.contains(e.text)) {
                // xml 里有但 manifest 没下发——保留（"merge 不删"语义）
                newDefaults.add(e);
            }
        }
        for (Entry e : xmlDefaultEntries) {
            boolean isCustom = e.id != null && e.id.startsWith("custom_");
            if (!isCustom) {
                newDefaults.add(e);
            }
        }

        // 4) 重建 pattern（用新的 defaults）
        Pattern newPattern = buildPattern(newDefaults);

        // 5) volatile swap（唯一"发布"点，happens-before 保证消费端看到一致状态）
        this.text2entry = Collections.unmodifiableMap(newMap);
        this.defaultEntries = Collections.unmodifiableList(newDefaults);
        this.pattern = newPattern;
        this.currentSig = newSig;

        Log.i(TAG, "applyManifest: manifestItems=" + sanitizedItems.size()
                + " panelEntries=" + newDefaults.size() + " sig=" + newSig);
        // 每 item 逐行 log 只在 debug 打——release 场景没必要每次冷启动灌 N 行；
        // 顶层那条汇总日志（manifestItems + panelEntries）保留 release 也有，
        // 出问题时能立刻看出 manifest 拉没拉到、数量对不对。
        if (BuildConfig.DEBUG) {
            for (EmojiManifestItem item : sanitizedItems) {
                Log.d(TAG, "  item key=" + item.key + " name=" + item.name
                        + " url=" + (item.url == null || item.url.isEmpty() ? "(none)" : item.url));
            }
        }
        return true;
    }

    /** manifest 里新增的 key（xml 无兜底）派生一个稳定 id：{@code custom_<hex>}。
     *  必须以 {@code custom_} 开头以满足 {@link #isCustomEmoji(String)} 的判定。 */
    private static String deriveIdFromKey(String key) {
        // 简单稳定哈希——冲突概率极低（几个新增 emoji），无需 MD5 复杂度
        long h = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) h = 31 * h + key.charAt(i);
        return "custom_" + Long.toHexString(h & 0x7fffffffffffffffL);
    }

    /** 内容签名：manifest items 完全一致 → 相同 sig，短路 apply。 */
    private static String signatureOf(List<EmojiManifestItem> items) {
        StringBuilder sb = new StringBuilder(64 + items.size() * 24);
        sb.append("n=").append(items.size());
        for (EmojiManifestItem it : items) {
            sb.append('').append(it.key).append('').append(it.name).append('').append(it.url);
        }
        return sb.toString();
    }

    private static Pattern buildPattern(List<Entry> entries) {
        StringBuilder sb = new StringBuilder(entries.size() * 8);
        sb.append("(");
        boolean first = true;
        for (Entry e : entries) {
            if (e.text == null || e.text.isEmpty()) continue;
            if (!first) sb.append("|");
            sb.append(Pattern.quote(e.text));
            first = false;
        }
        sb.append(")");
        return Pattern.compile(sb.toString());
    }

    static final class Entry {
        final String text;
        final String assetPath;
        final String id;
        /** 服务端 manifest 下发的图片 URL。空 = 用 {@link #assetPath} 本地兜底；
         *  非空 = 未来 Layer 3 走 Glide 加载（当前 MVP 期不消费此字段，仅存储）。 */
        final String remoteUrl;
        /** 服务端 manifest 下发的人类可读名；xml 加载时留空，仅 manifest 派生的条目有值。
         *  选择器 title / 无障碍文本可以用（当前消费方主要用 text/id，为未来预留）。 */
        final String name;

        Entry(String id, String text, String assetPath) {
            this(id, text, assetPath, "", "");
        }

        Entry(String id, String text, String assetPath, String remoteUrl, String name) {
            this.text = text;
            this.id = id;
            this.assetPath = assetPath;
            this.remoteUrl = remoteUrl == null ? "" : remoteUrl;
            this.name = name == null ? "" : name;
        }
    }

    public int getDisplayCount() {
        ensureInitialized();
        return defaultEntries.size();
    }

    public Drawable getDisplayDrawable(Context context, int index) {
        ensureInitialized();
        List<Entry> list = defaultEntries;
        String text = (index >= 0 && index < list.size() ? list.get(index).text : null);
        return text == null ? null : getDrawable(context, text);
    }

    public String getDisplayText(int index) {
        ensureInitialized();
        List<Entry> list = defaultEntries;
        return index >= 0 && index < list.size() ? list.get(index).text : null;
    }

    public Pattern getPattern() {
        ensureInitialized();
        return pattern;
    }

    public Drawable getDrawableWithTag(Context context, String tag) {
        ensureInitialized();
        List<Entry> list = defaultEntries;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(tag)) {
                return getDrawable(context, list.get(i).text);
            }
        }
        return null;
    }

    public EmojiEntry getEmojiWithTag(String tag) {
        ensureInitialized();
        List<Entry> list = defaultEntries;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(tag)) {
                return new EmojiEntry(list.get(i).id, list.get(i).text, safeAssetPath(list.get(i).assetPath));
            }
        }
        return null;
    }

    public EmojiEntry getEmojiEntry(String text) {
        ensureInitialized();
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }
        return new EmojiEntry(entry.id, entry.text, safeAssetPath(entry.assetPath));
    }

    /** Unicode-only entry 的内部 assetPath 是 null；对外传给 Kotlin 的 EmojiEntry
     *  时统一替换成空串，避免非空 String 字段 NPE。 */
    private static String safeAssetPath(String assetPath) {
        return assetPath == null ? "" : assetPath;
    }

    public Drawable getDrawable(Context context, String text) {
        ensureInitialized();
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }

        // 服务端 manifest 下发但客户端未打包 asset 的情况（remoteUrl 非空且 assetPath 为 null）：
        // 当前 MVP 不支持远程 URL 加载（web PR #492 的 Layer 3 能力），直接返 null 让消息渲染成
        // [xxx] 文本降级。Glide 预下载 + ImageSpan 异步刷新是独立 PR 的工作量。
        if (entry.assetPath == null && !TextUtils.isEmpty(entry.remoteUrl)) {
            return null;
        }

        // Unicode-only emoji（如 🎉 / 🎊）: XML 中 File="" 时 assetPath 为 null，
        // 用系统字体把字形画到 Bitmap 上当 drawable，避免新增 PNG 资源。
        if (entry.assetPath == null) {
            Bitmap unicodeCache = drawableCache.get(unicodeCacheKey(entry.text));
            if (unicodeCache == null || unicodeCache.isRecycled()) {
                unicodeCache = renderUnicodeBitmap(context, entry.text);
            }
            if (unicodeCache != null) {
                return new BitmapDrawable(context.getResources(), unicodeCache);
            }
            return null;
        }

        Bitmap cache = drawableCache.get(entry.assetPath);
        if (cache == null) {
            cache = loadAssetBitmap(context, entry.assetPath);
        }
        return new BitmapDrawable(context.getResources(), cache);
    }

    private String unicodeCacheKey(String text) {
        return "__unicode__/" + text;
    }

    /**
     * 把 Unicode emoji 字符渲染到一张 60×60dp 的 Bitmap 上，给 ImageView 用。
     * 字形来自系统的 emoji 字体（Android Q+ 自带 NotoColorEmoji，旧机型回退到
     * 厂商字体）。结果按 emoji.xml 字典文本入 LRU 缓存，键带 "__unicode__/" 前缀
     * 与 PNG 路径冲突。
     */
    private Bitmap renderUnicodeBitmap(Context context, String text) {
        try {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int sizePx = Math.round(60f * metrics.density);
            if (sizePx <= 0) sizePx = 60;

            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setSubpixelText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sizePx * 0.78f);

            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(text, sizePx / 2f, baseline, paint);

            drawableCache.put(unicodeCacheKey(text), bitmap);
            return bitmap;
        } catch (Throwable t) {
            WKLogUtils.e("渲染 Unicode emoji 失败: " + text);
            return null;
        }
    }

    private Bitmap loadAssetBitmap(Context context, String assetPath) {
        InputStream is = null;
        try {
            Resources resources = context.getResources();
            Options options = new Options();
            options.inDensity = android.util.DisplayMetrics.DENSITY_HIGH;
            options.inScreenDensity = resources.getDisplayMetrics().densityDpi;
            options.inTargetDensity = resources.getDisplayMetrics().densityDpi;
            is = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is, new Rect(), options);
            if (bitmap != null) {
                drawableCache.put(assetPath, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            WKLogUtils.e("解析emoji错误");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    //
    // load emoticons from asset
    //
    private class EntryLoader extends DefaultHandler {
        private String catalog = "";
        private final Map<String, Entry> outMap;
        private final List<Entry> outDefaults;

        EntryLoader(Map<String, Entry> outMap, List<Entry> outDefaults) {
            this.outMap = outMap;
            this.outDefaults = outDefaults;
        }

        void load(Context context, String assetPath) {
            InputStream is = null;
            try {
                is = context.getAssets().open(assetPath);
                Xml.parse(is, Xml.Encoding.UTF_8, this);
            } catch (IOException | SAXException e) {
                e.printStackTrace();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("Catalog")) {
                catalog = attributes.getValue(uri, "Title");
            } else if (localName.equals("Emoticon")) {
                String tag = attributes.getValue(uri, "Tag");
                String id = attributes.getValue(uri, "ID");
                String fileName = attributes.getValue(uri, "File");
                // File="" 表示走系统 Unicode 字体渲染，不绑 PNG 资源。
                String assetPath = TextUtils.isEmpty(fileName)
                        ? null
                        : EMOT_DIR + catalog + "/" + fileName;
                Entry entry = new Entry(id, tag, assetPath);
                outMap.put(entry.text, entry);
                if (catalog.equals("default")) {
                    outDefaults.add(entry);
                }
            }
        }
    }

    /**
     * 判断是否为自定义表情（ID 以 custom_ 开头），通过内部注册的 entry ID 判断，
     * 不依赖用户输入的文本内容，确保不会误判。
     */
    public boolean isCustomEmoji(String text) {
        Entry entry = text2entry.get(text);
        return entry != null && entry.id.startsWith("custom_");
    }

    public boolean isHeart(String tag) {
        Map<String, Entry> map = text2entry;
        if (!map.containsKey(tag)) return false;
        return Objects.requireNonNull(map.get(tag)).id.equals("2_0")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_1")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_2")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_3")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_4")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_5")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_6")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_7")
                || Objects.requireNonNull(map.get(tag)).id.equals("2_8");
    }


    public List<EmojiEntry> getEmojiWithType(String type) {
        ensureInitialized();
        List<Entry> source = defaultEntries;
        List<EmojiEntry> list = new ArrayList<>();
        for (int i = 0, size = source.size(); i < size; i++) {
            if (source.get(i).id.contains("color")) {
                continue;
            }
            boolean isAdd = true;
            for (EmojiEntry entry : list) {
                if (entry.getText().equals(source.get(i).text)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                if (source.get(i).id.startsWith(type)) {
                    EmojiEntry entry = new EmojiEntry(source.get(i).id, source.get(i).text, safeAssetPath(source.get(i).assetPath));
                    list.add(entry);
                }
            }
        }
        return list;
    }
}
