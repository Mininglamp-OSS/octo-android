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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;

/**
 * SP 层不测（依赖 Android + KeyStore），只测 serialize / deserialize 纯函数——
 * 这是"缓存丢失的话到底会不会 crash"的核心防线。
 */
public class EmojiManifestCacheTest {

    private static EmojiManifestItem item(String key, String name, String url) {
        EmojiManifestItem it = new EmojiManifestItem();
        it.key = key;
        it.name = name;
        it.url = url;
        return it;
    }

    @Test
    public void round_trip_preserves_all_fields() {
        EmojiManifestResp in = new EmojiManifestResp();
        in.version = 3;
        in.list = new ArrayList<>();
        in.list.add(item("[使命必达]", "使命必达", ""));
        in.list.add(item("[新表情]", "新表情", "https://cdn.example.com/x.png"));

        String json = EmojiManifestCache.serialize(in);
        assertNotNull(json);

        EmojiManifestResp out = EmojiManifestCache.deserialize(json);
        assertNotNull(out);
        assertEquals(3, out.version);
        assertEquals(2, out.list.size());
        assertEquals("[使命必达]", out.list.get(0).key);
        assertEquals("使命必达", out.list.get(0).name);
        assertEquals("", out.list.get(0).url);
        assertEquals("[新表情]", out.list.get(1).key);
        assertEquals("https://cdn.example.com/x.png", out.list.get(1).url);
    }

    @Test
    public void serialize_null_returns_empty() {
        assertEquals("", EmojiManifestCache.serialize(null));
    }

    @Test
    public void serialize_null_list_returns_empty() {
        EmojiManifestResp r = new EmojiManifestResp();
        r.version = 1;
        r.list = null;
        assertEquals("", EmojiManifestCache.serialize(r));
    }

    @Test
    public void deserialize_null_returns_null() {
        assertNull(EmojiManifestCache.deserialize(null));
    }

    @Test
    public void deserialize_empty_returns_null() {
        assertNull(EmojiManifestCache.deserialize(""));
    }

    @Test
    public void deserialize_corrupt_returns_null() {
        // 损坏 JSON——不能 crash，返 null 走内置兜底
        assertNull(EmojiManifestCache.deserialize("{not valid json"));
        assertNull(EmojiManifestCache.deserialize("]["));
        assertNull(EmojiManifestCache.deserialize("just text"));
    }

    @Test
    public void deserialize_empty_list_ok() {
        // 服务端理论上不会下发 empty list（parseEmojiManifest reject），但客户端解析要能吃
        EmojiManifestResp out = EmojiManifestCache.deserialize("{\"version\":1,\"list\":[]}");
        assertNotNull(out);
        assertEquals(1, out.version);
        assertEquals(0, out.list.size());
    }
}
