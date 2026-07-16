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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端 emoji 清单的**客户端 sanitize 防线**——纯函数、无 IO、无 Android 依赖，便于 JVM 单测。
 *
 * <p>虽然服务端已经在 {@code parseEmojiManifest}（{@code modules/common/emoji.go}）
 * 做了强校验（{@code [xxx]} token、name 非空、key 唯一），客户端仍守这一层，是为了防：
 * <ul>
 *   <li>不同版本服务端的契约不一致（本地是 vN，服务端已 vN+1 加了新校验松紧变化）</li>
 *   <li>中间人 / 代理注入非法数据</li>
 *   <li>本地缓存的 JSON 被外部工具改坏</li>
 * </ul>
 *
 * <p><b>核心防护目标：Pattern.quote 空 branch 零宽死循环</b>——
 * {@link com.chat.base.emoji.EmojiManager} 的 {@code getPattern()} 会把所有 token
 * {@code Pattern.quote()} 后用 {@code |} 拼成 alternation。如果 token 为空或纯空白，
 * 就会产生 {@code (a|b|)} 这种带空 branch 的正则——消费端 {@code Matcher} 在
 * {@code find() + slice} 循环里遇到零宽匹配会永不前进，渲染死循环（web 端 PR #492 同样点）。
 *
 * <p>URL 白名单：只放行 {@code https://} 和相对路径（后者由呼叫方拼到 {@code WKApiConfig.baseUrl}）。
 * {@code http://}（明文）和其它 scheme（{@code file:} {@code javascript:} {@code intent:} 等）
 * 一律拒绝——emoji URL 是被 Glide 加载的资源引用，不应放开任意 scheme。
 */
public final class EmojiManifestSanitizer {

    /** 单个 key 允许的最大字节长度（防超长恶意 token 拖慢正则匹配）。32 覆盖任何合理表情名。 */
    static final int MAX_KEY_LEN = 32;

    /** 单个 name 允许的最大长度。宽松些——name 只用作显示，不进正则。 */
    static final int MAX_NAME_LEN = 64;

    /** 单个 url 允许的最大长度。整体 SP 落盘要一次序列化整份 manifest，单 url 太长会撑爆存储；
     *  2048 覆盖任何合理 CDN URL（含 query），且远小于典型 SP 单值上限。 */
    static final int MAX_URL_LEN = 2048;

    /** 整份清单最多接受的条目数（防 OOM / 拼出巨大正则）。500 远超任何合理内置表情量。 */
    static final int MAX_ITEMS = 500;

    private EmojiManifestSanitizer() {}

    /**
     * 过滤并规范化服务端返回的 items 列表。
     *
     * @param items 原始 items（可为 null）
     * @return 已 sanitize 的不可变列表；输入非法或全部条目被 drop 时返回空列表
     */
    public static List<EmojiManifestItem> sanitize(List<EmojiManifestItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        int cap = Math.min(items.size(), MAX_ITEMS);
        List<EmojiManifestItem> out = new ArrayList<>(cap);
        Set<String> seen = new HashSet<>(cap * 2);
        for (int i = 0, n = items.size(); i < n && out.size() < MAX_ITEMS; i++) {
            EmojiManifestItem it = items.get(i);
            if (it == null) continue;
            String key = it.key == null ? "" : it.key.trim();
            if (!isValidToken(key)) continue;
            if (key.length() > MAX_KEY_LEN) continue;
            if (seen.contains(key)) continue; // 服务端保证唯一但客户端仍防
            seen.add(key);

            String url = it.url == null ? "" : it.url.trim();
            if (!url.isEmpty() && !isSafeUrl(url)) continue;

            // name 空 → 用 key 去掉 `[` `]` 兜底（宁可显示 key 也不 drop）；超长直接截断
            String name = it.name == null ? "" : it.name.trim();
            if (name.isEmpty()) name = key.substring(1, key.length() - 1);
            if (name.length() > MAX_NAME_LEN) name = name.substring(0, MAX_NAME_LEN);

            EmojiManifestItem clean = new EmojiManifestItem();
            clean.key = key;
            clean.name = name;
            clean.url = url;
            out.add(clean);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * token 是否形如 {@code [xxx]}（跟服务端 {@code isEmojiToken} 保持一致）：
     * {@code [} 开头、{@code ]} 结尾，中间非空且不含 {@code ]}。
     */
    public static boolean isValidToken(String s) {
        if (s == null || s.length() < 3) return false;
        if (s.charAt(0) != '[' || s.charAt(s.length() - 1) != ']') return false;
        // 中间不含 ']' —— 排除嵌套；顺带排除中间全空白（首尾字符本来就非空白就够，但强化下）。
        String inner = s.substring(1, s.length() - 1);
        if (inner.trim().isEmpty()) return false;
        return inner.indexOf(']') < 0;
    }

    /**
     * URL 白名单：只放行 {@code https://} 或**严格相对路径**。
     *
     * <p>为**允许列表 (allow-list) 而非拒绝列表 (deny-list)**：不匹配任何允许模式一律拒绝，
     * 防止将来 URL parser 差异漏掉的奇葩 scheme（{@code file:} {@code javascript:}
     * {@code intent:} {@code content:} {@code data:} 等），以及 backslash / 嵌入凭证等灰色区域。
     *
     * <p>{@code https://} 分支额外拒绝**嵌入凭证** ({@code https://user:pass@host/...})——
     * 攻击者可以借此把凭证泄漏给日志、代理或 phishing 到攻击者控制的 host（RFC 3986 允许但不安全）。
     *
     * <p>相对路径分支要求：首字符必须 {@code /} 或 ASCII 字母数字；全串不能含 {@code \}
     * （Windows 风格路径/URL 转义灰色）；不能含 {@code @}；不能有 scheme
     * （{@code xxx:} 出现在首个 {@code /} 前视为 scheme）。
     *
     * <p>{@link #MAX_URL_LEN} 上限统一在此处收——超长 URL 一并拒绝。
     */
    public static boolean isSafeUrl(String url) {
        if (url == null || url.isEmpty()) return true; // 空 = 用本地打包 asset，允许
        if (url.length() > MAX_URL_LEN) return false;
        if (url.indexOf('\\') >= 0) return false; // backslash 一律拒
        String lower = url.toLowerCase();
        if (lower.startsWith("https://")) {
            // 拒绝嵌入凭证 user:pass@host —— @ 出现在 authority 段（第一个 / 之前，或无 /）时拒
            int schemeEnd = 8; // "https://".length()
            int pathSlash = url.indexOf('/', schemeEnd);
            int at = url.indexOf('@', schemeEnd);
            if (at >= 0 && (pathSlash < 0 || at < pathSlash)) return false;
            return true;
        }
        // 相对路径 allow-list
        if (url.startsWith("//")) return false; // 协议相对拒
        char first = url.charAt(0);
        boolean okFirst = first == '/'
                || (first >= 'A' && first <= 'Z')
                || (first >= 'a' && first <= 'z')
                || (first >= '0' && first <= '9');
        if (!okFirst) return false;
        if (url.indexOf('@') >= 0) return false; // 相对路径不该有 @
        // scheme 检测：`xxx:` 出现在首个 `/` 之前视为 scheme
        int colon = url.indexOf(':');
        int slash = url.indexOf('/');
        if (colon >= 0 && (slash < 0 || colon < slash)) return false;
        // 路径穿越拒：`..` 组件（防将来 Layer 3 拼到 baseUrl 后被服务端错误解析出目录穿越）。
        // 严格匹配 `../` `..\` 首尾 `..`，不匹配文件名里含 `..` 的普通字符（如 `file..v2.png`）。
        if (containsPathTraversal(url)) return false;
        return true;
    }

    /** URL 里是否存在 {@code ..} 路径组件（不是简单子串——{@code file..v2.png} 不算）。 */
    private static boolean containsPathTraversal(String url) {
        int len = url.length();
        for (int i = 0; i <= len - 2; i++) {
            if (url.charAt(i) != '.' || url.charAt(i + 1) != '.') continue;
            boolean leftBoundary = (i == 0) || url.charAt(i - 1) == '/' || url.charAt(i - 1) == '\\';
            boolean rightEnd = (i + 2 == len);
            boolean rightSlash = !rightEnd && (url.charAt(i + 2) == '/' || url.charAt(i + 2) == '\\');
            if (leftBoundary && (rightEnd || rightSlash)) return true;
        }
        return false;
    }
}
