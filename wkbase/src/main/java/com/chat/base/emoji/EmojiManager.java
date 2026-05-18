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
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Xml;

import androidx.collection.LruCache;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKLogUtils;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class EmojiManager {

    private final String EMOT_DIR = "emoji/";

    // max cache size
    private final int CACHE_MAX_SIZE = 1024;

    private Pattern pattern;
    //  (P-04) — init() 幂等标记，供 ensureInitialized() 使用。
    private volatile boolean initialized = false;

    // default entries
    private final List<Entry> defaultEntries = new ArrayList<>();
    // text to entry
    private final Map<String, Entry> text2entry = new HashMap<>();
    // asset bitmap cache, key: asset path
    private LruCache<String, Bitmap> drawableCache;

    private String patternStr = "";

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
     * 跟 Bugly / RLottie 抢 CPU。新 AppStartup 把它挪到 Phase-C（idle 后），
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

        load(context, EMOT_DIR + "emoji.xml");
        pattern = makePattern();
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

    private static class Entry {
        String text;
        String assetPath;
        String id;

        Entry(String id, String text, String assetPath) {
            this.text = text;
            this.id = id;
            this.assetPath = assetPath;
        }
    }

    public int getDisplayCount() {
        ensureInitialized();
        return defaultEntries.size();
    }

    public Drawable getDisplayDrawable(Context context, int index) {
        ensureInitialized();
        String text = (index >= 0 && index < defaultEntries.size() ?
                defaultEntries.get(index).text : null);
        return text == null ? null : getDrawable(context, text);
    }

    public String getDisplayText(int index) {
        ensureInitialized();
        return index >= 0 && index < defaultEntries.size() ? defaultEntries
                .get(index).text : null;
    }

    public Pattern getPattern() {
        ensureInitialized();
        return pattern;
    }

    public Drawable getDrawableWithTag(Context context, String tag) {
        ensureInitialized();
        Drawable drawable = null;
        for (int i = 0; i < defaultEntries.size(); i++) {
            if (defaultEntries.get(i).id.equals(tag)) {
                drawable = getDrawable(context, defaultEntries.get(i).text);
                break;
            }
        }
        return drawable;
    }
    public EmojiEntry getEmojiWithTag(String tag){
        ensureInitialized();
        EmojiEntry entry = null;
        for (int i = 0; i < defaultEntries.size(); i++) {
            if (defaultEntries.get(i).id.equals(tag)) {
                entry = new EmojiEntry(defaultEntries.get(i).id,defaultEntries.get(i).text,defaultEntries.get(i).assetPath);
                break;
            }
        }
        return entry;
    }
    public EmojiEntry getEmojiEntry(String text) {
        ensureInitialized();
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }
        return new EmojiEntry(entry.id, entry.text, entry.assetPath);
    }

    public Drawable getDrawable(Context context, String text) {
        ensureInitialized();
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }

        Bitmap cache = drawableCache.get(entry.assetPath);
        if (cache == null) {
            cache = loadAssetBitmap(context, entry.assetPath);
        }
        return new BitmapDrawable(context.getResources(), cache);
    }

    //
    // internal
    //

    private Pattern makePattern() {
        return Pattern.compile(patternOfDefault());
    }

    private String patternOfDefault() {
//        return "\\[[^\\[]{1,10}\\]";
        if (TextUtils.isEmpty(patternStr)) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0, size = defaultEntries.size(); i < size; i++) {
                if (!sb.toString().endsWith("(")) {
                    sb.append("|");
                }
                sb.append(Pattern.quote(defaultEntries.get(i).text));
            }
            sb.append(")");
            patternStr = sb.toString();
        }
        return patternStr;
        // return "[^\\u0000-\\uFFFF]";
    }

    private Bitmap loadAssetBitmap(Context context, String assetPath) {
        InputStream is = null;
        try {
            Resources resources = context.getResources();
            Options options = new Options();
            options.inDensity = DisplayMetrics.DENSITY_HIGH;
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

    private void load(Context context, String xmlPath) {
        new EntryLoader().load(context, xmlPath);
    }

    //
    // load emoticons from asset
    //
    private class EntryLoader extends DefaultHandler {
        private String catalog = "";

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
                Entry entry = new Entry(id, tag, EMOT_DIR + catalog + "/" + fileName);
                text2entry.put(entry.text, entry);
                if (catalog.equals("default")) {
                    defaultEntries.add(entry);
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
        if (!text2entry.containsKey(tag)) return false;
        return Objects.requireNonNull(text2entry.get(tag)).id.equals("2_0")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_1")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_2")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_3")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_4")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_5")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_6")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_7")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_8");
    }


    public List<EmojiEntry> getEmojiWithType(String type) {
        ensureInitialized();
        List<EmojiEntry> list = new ArrayList<>();
        for (int i = 0, size = defaultEntries.size(); i < size; i++) {
            if (defaultEntries.get(i).id.contains("color")) {
                continue;
            }
            boolean isAdd = true;
            for (EmojiEntry entry : list) {
                if (entry.getText().equals(defaultEntries.get(i).text)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                if (defaultEntries.get(i).id.startsWith(type)) {
                    EmojiEntry entry = new EmojiEntry(defaultEntries.get(i).id, defaultEntries.get(i).text, defaultEntries.get(i).assetPath);
                    list.add(entry);
                }
            }
        }
        return list;
    }
}
