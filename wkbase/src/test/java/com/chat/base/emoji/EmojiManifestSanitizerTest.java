/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 服务端已经在 parseEmojiManifest 做过强校验，客户端 sanitize 是防契约破坏 / 中间人 / 缓存改坏。
 * 核心目标：**防 Pattern.quote 空 branch 零宽死循环**——空 key 绝不能进 defaultEntries。
 */
public class EmojiManifestSanitizerTest {

    private static EmojiManifestItem item(String key, String name, String url) {
        EmojiManifestItem it = new EmojiManifestItem();
        it.key = key;
        it.name = name;
        it.url = url;
        return it;
    }

    @Test
    public void sanitize_null_returns_empty() {
        assertTrue(EmojiManifestSanitizer.sanitize(null).isEmpty());
    }

    @Test
    public void sanitize_empty_returns_empty() {
        assertTrue(EmojiManifestSanitizer.sanitize(Collections.<EmojiManifestItem>emptyList()).isEmpty());
    }

    @Test
    public void sanitize_valid_items_pass_through() {
        List<EmojiManifestItem> in = Arrays.asList(
                item("[使命必达]", "使命必达", ""),
                item("[尚方宝剑]", "尚方宝剑", "")
        );
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(in);
        assertEquals(2, out.size());
        assertEquals("[使命必达]", out.get(0).key);
        assertEquals("[尚方宝剑]", out.get(1).key);
    }

    // ---- KILL bug: 空 key / 空白 key 必须 drop（否则正则零宽死循环）----

    @Test
    public void sanitize_drops_null_item() {
        List<EmojiManifestItem> in = new ArrayList<>();
        in.add(null);
        in.add(item("[a]", "A", ""));
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(in);
        assertEquals(1, out.size());
    }

    @Test
    public void sanitize_drops_null_key() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(item(null, "x", ""), item("[a]", "A", "")));
        assertEquals(1, out.size());
        assertEquals("[a]", out.get(0).key);
    }

    @Test
    public void sanitize_drops_empty_key() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(item("", "x", ""), item("[a]", "A", "")));
        assertEquals(1, out.size());
    }

    @Test
    public void sanitize_drops_whitespace_only_key() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(item("   ", "x", ""), item("[  ]", "y", ""), item("[a]", "A", "")));
        // "   " trim 后为空 → drop
        // "[  ]" trim 后仍是 "[  ]"，内部是纯空白 → isValidToken 需 drop
        assertEquals(1, out.size());
        assertEquals("[a]", out.get(0).key);
    }

    // ---- token 格式校验 ----

    @Test
    public void isValidToken_requires_bracket_wrap() {
        assertTrue(EmojiManifestSanitizer.isValidToken("[a]"));
        assertTrue(EmojiManifestSanitizer.isValidToken("[使命必达]"));
        assertFalse(EmojiManifestSanitizer.isValidToken("a"));
        assertFalse(EmojiManifestSanitizer.isValidToken("[a"));
        assertFalse(EmojiManifestSanitizer.isValidToken("a]"));
        assertFalse(EmojiManifestSanitizer.isValidToken("[]"));
        assertFalse(EmojiManifestSanitizer.isValidToken(""));
        assertFalse(EmojiManifestSanitizer.isValidToken(null));
    }

    @Test
    public void isValidToken_rejects_nested_bracket() {
        // 内含 ']' 会破坏简单正则匹配（跟服务端 isEmojiToken 一致）
        assertFalse(EmojiManifestSanitizer.isValidToken("[a]b]"));
    }

    @Test
    public void sanitize_drops_overlong_key() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < EmojiManifestSanitizer.MAX_KEY_LEN + 5; i++) sb.append('a');
        sb.append("]");
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(item(sb.toString(), "x", ""), item("[a]", "A", "")));
        assertEquals(1, out.size());
        assertEquals("[a]", out.get(0).key);
    }

    // ---- 唯一性（服务端保证但客户端 double-check）----

    @Test
    public void sanitize_drops_duplicate_key_keeping_first() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(item("[a]", "A1", ""), item("[a]", "A2", ""), item("[b]", "B", "")));
        assertEquals(2, out.size());
        assertEquals("A1", out.get(0).name);
    }

    // ---- name 兜底：空 name 用 key 去括号，不 drop ----

    @Test
    public void sanitize_empty_name_falls_back_to_key_inner() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Collections.singletonList(item("[使命必达]", "", "")));
        assertEquals(1, out.size());
        assertEquals("使命必达", out.get(0).name);
    }

    @Test
    public void sanitize_overlong_name_truncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < EmojiManifestSanitizer.MAX_NAME_LEN + 10; i++) sb.append('a');
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Collections.singletonList(item("[k]", sb.toString(), "")));
        assertEquals(EmojiManifestSanitizer.MAX_NAME_LEN, out.get(0).name.length());
    }

    // ---- URL 白名单：只放 https + 相对路径 ----

    @Test
    public void isSafeUrl_empty_ok() {
        assertTrue(EmojiManifestSanitizer.isSafeUrl(""));
        assertTrue(EmojiManifestSanitizer.isSafeUrl(null));
    }

    @Test
    public void isSafeUrl_https_ok() {
        assertTrue(EmojiManifestSanitizer.isSafeUrl("https://cdn.example.com/e.png"));
        assertTrue(EmojiManifestSanitizer.isSafeUrl("HTTPS://cdn.example.com/e.png"));
    }

    @Test
    public void isSafeUrl_http_rejected() {
        assertFalse(EmojiManifestSanitizer.isSafeUrl("http://cdn.example.com/e.png"));
    }

    @Test
    public void isSafeUrl_relative_ok() {
        assertTrue(EmojiManifestSanitizer.isSafeUrl("emojis/e.png"));
        assertTrue(EmojiManifestSanitizer.isSafeUrl("/emojis/e.png"));
    }

    @Test
    public void isSafeUrl_protocol_relative_rejected() {
        assertFalse(EmojiManifestSanitizer.isSafeUrl("//cdn.example.com/e.png"));
    }

    @Test
    public void isSafeUrl_unknown_scheme_rejected() {
        assertFalse(EmojiManifestSanitizer.isSafeUrl("file:///etc/passwd"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("javascript:alert(1)"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("intent://xxx#Intent;end"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("data:image/png;base64,aaa"));
    }

    // ---- B4：URL allow-list 加固 ----

    @Test
    public void isSafeUrl_backslash_rejected() {
        // Windows-style path / URL 转义灰色区域，一律拒
        assertFalse(EmojiManifestSanitizer.isSafeUrl("\\evil.com/x"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("https://cdn.example.com/e\\bad.png"));
    }

    @Test
    public void isSafeUrl_https_embedded_credentials_rejected() {
        // RFC 3986 允许 user:pass@host 但不安全（可能被日志/代理泄漏）
        assertFalse(EmojiManifestSanitizer.isSafeUrl("https://user:pass@evil.com/e.png"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("https://user@evil.com/e.png"));
    }

    @Test
    public void isSafeUrl_https_at_in_path_ok() {
        // @ 出现在 path 部分（第一个 / 之后）是合法的，不该被拒
        assertTrue(EmojiManifestSanitizer.isSafeUrl("https://cdn.example.com/path@version/e.png"));
    }

    @Test
    public void isSafeUrl_relative_at_rejected() {
        // 相对路径不该含 @——防被误解析成 user:pass@host
        assertFalse(EmojiManifestSanitizer.isSafeUrl("emojis@evil.com/e.png"));
    }

    @Test
    public void isSafeUrl_relative_non_alnum_first_rejected() {
        // allow-list：首字符必须 / 或 ASCII 字母数字
        assertFalse(EmojiManifestSanitizer.isSafeUrl(".hidden/e.png"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("-dash/e.png"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl(" emojis/e.png"));
    }

    // ---- B2：MAX_URL_LEN 上限 ----

    @Test
    public void isSafeUrl_overlong_rejected() {
        StringBuilder sb = new StringBuilder("https://cdn.example.com/");
        while (sb.length() < EmojiManifestSanitizer.MAX_URL_LEN + 10) sb.append('a');
        assertFalse(EmojiManifestSanitizer.isSafeUrl(sb.toString()));
    }

    @Test
    public void sanitize_drops_item_with_overlong_url() {
        StringBuilder sb = new StringBuilder("https://cdn.example.com/");
        while (sb.length() < EmojiManifestSanitizer.MAX_URL_LEN + 10) sb.append('a');
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(
                        item("[a]", "A", sb.toString()),
                        item("[b]", "B", "https://cdn.example.com/short.png")));
        assertEquals(1, out.size());
        assertEquals("[b]", out.get(0).key);
    }

    // ---- P2-1：`..` 路径穿越拒绝（防未来 Layer 3 拼到 baseUrl 后被误解析） ----

    @Test
    public void isSafeUrl_relative_path_traversal_rejected() {
        assertFalse(EmojiManifestSanitizer.isSafeUrl("../secret/e.png"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("emojis/../../secret/e.png"));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("emojis/.."));
        assertFalse(EmojiManifestSanitizer.isSafeUrl("/emojis/../etc"));
    }

    @Test
    public void isSafeUrl_dot_dot_in_filename_ok() {
        // `.` `..` 只在作为完整路径组件时才拒，文件名里含 `..` 的普通字符不拒
        assertTrue(EmojiManifestSanitizer.isSafeUrl("emojis/file..v2.png"));
        assertTrue(EmojiManifestSanitizer.isSafeUrl("emojis/..hidden.png")); // 首字符是 `.` 但整段不是 `..`
    }

    @Test
    public void sanitize_drops_item_with_unsafe_url_but_keeps_others() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Arrays.asList(
                        item("[a]", "A", "javascript:alert(1)"),
                        item("[b]", "B", "https://cdn.example.com/b.png"),
                        item("[c]", "C", "")));
        assertEquals(2, out.size());
        assertEquals("[b]", out.get(0).key);
        assertEquals("[c]", out.get(1).key);
    }

    // ---- 输入长度上限 ----

    @Test
    public void sanitize_caps_at_max_items() {
        List<EmojiManifestItem> in = new ArrayList<>();
        for (int i = 0; i < EmojiManifestSanitizer.MAX_ITEMS + 10; i++) {
            in.add(item("[k" + i + "]", "n" + i, ""));
        }
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(in);
        assertEquals(EmojiManifestSanitizer.MAX_ITEMS, out.size());
    }

    // ---- 输出不可变（防呼叫方误改）----

    @Test(expected = UnsupportedOperationException.class)
    public void sanitize_result_is_immutable() {
        List<EmojiManifestItem> out = EmojiManifestSanitizer.sanitize(
                Collections.singletonList(item("[a]", "A", "")));
        assertNotNull(out);
        out.add(item("[b]", "B", ""));
    }
}
