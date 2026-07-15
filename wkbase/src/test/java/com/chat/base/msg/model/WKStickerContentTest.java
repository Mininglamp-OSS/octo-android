/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.msg.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.xinbida.wukongim.message.type.WKMsgContentType;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * WK_VECTOR_STICKER (12) / WK_EMOJI_STICKER (13) 消息回归保护。
 *
 * 关键回归点：
 *  - type 常量必须 = 12 / 13（服务端 Web / iOS 接收都要按这个 type 路由）
 *  - encode → decode 往返一致（字段结构与 iOS WKLottieStickerContent 对齐）
 *  - format 未指定时默认 "lim"（对齐 iOS 行为，避免 null 字符串引发下游 NPE）
 *  - getDisplayContent / getSearchableWord 有默认值（子区最后一条消息摘要用）
 */
public class WKStickerContentTest {

    @Test
    public void vectorSticker_typeIs12() {
        WKVectorStickerContent content = new WKVectorStickerContent();
        assertEquals(WKMsgContentType.WK_VECTOR_STICKER, content.type);
        assertEquals(12, content.type);
    }

    @Test
    public void emojiSticker_typeIs13() {
        WKEmojiStickerContent content = new WKEmojiStickerContent();
        assertEquals(WKMsgContentType.WK_EMOJI_STICKER, content.type);
        assertEquals(13, content.type);
    }

    @Test
    public void vectorSticker_encodeDecodeRoundTrip() throws JSONException {
        WKVectorStickerContent origin = new WKVectorStickerContent();
        origin.url = "https://cdn.example.com/sticker/uid123/abc.lim";
        origin.width = 256;
        origin.height = 256;
        origin.category = "user";
        origin.placeholder = "<svg>...</svg>";
        origin.format = "lim";

        JSONObject json = origin.encodeMsg();
        assertNotNull(json);
        assertEquals("https://cdn.example.com/sticker/uid123/abc.lim", json.optString("url"));
        assertEquals(256, json.optInt("width"));
        assertEquals(256, json.optInt("height"));
        assertEquals("user", json.optString("category"));
        assertEquals("<svg>...</svg>", json.optString("placeholder"));
        assertEquals("lim", json.optString("format"));

        WKVectorStickerContent decoded = new WKVectorStickerContent();
        decoded.decodeMsg(json);
        assertEquals(origin.url, decoded.url);
        assertEquals(origin.width, decoded.width);
        assertEquals(origin.height, decoded.height);
        assertEquals(origin.category, decoded.category);
        assertEquals(origin.placeholder, decoded.placeholder);
        assertEquals(origin.format, decoded.format);
    }

    @Test
    public void vectorSticker_formatDefaultsToLimWhenMissing() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("url", "https://cdn.example.com/sticker/uid/x.lim");
        // 注意：没有 format 字段

        WKVectorStickerContent decoded = new WKVectorStickerContent();
        decoded.decodeMsg(json);

        assertEquals("lim", decoded.format);
    }

    @Test
    public void vectorSticker_encodeWithNullFieldsProducesEmptyStrings() {
        WKVectorStickerContent content = new WKVectorStickerContent();
        // 所有字段默认 null；encode 时要转成 "" 避免服务端 fastjson decode NPE
        JSONObject json = content.encodeMsg();

        assertEquals("", json.optString("url"));
        assertEquals("", json.optString("category"));
        assertEquals("", json.optString("placeholder"));
        assertEquals("lim", json.optString("format"));
    }

    @Test
    public void emojiSticker_inheritsAllFields() throws JSONException {
        WKEmojiStickerContent origin = new WKEmojiStickerContent();
        origin.url = "https://cdn.example.com/sticker/uid456/emoji.png";
        origin.width = 128;
        origin.height = 128;
        origin.category = "user";
        origin.format = "png";

        JSONObject json = origin.encodeMsg();
        WKEmojiStickerContent decoded = new WKEmojiStickerContent();
        decoded.decodeMsg(json);

        assertEquals(WKMsgContentType.WK_EMOJI_STICKER, decoded.type);
        assertEquals(origin.url, decoded.url);
        assertEquals(origin.width, decoded.width);
        assertEquals(origin.format, decoded.format);
    }

    @Test
    public void vectorSticker_displayContentAndSearchableWord() {
        WKVectorStickerContent v = new WKVectorStickerContent();
        assertEquals("[贴图]", v.getDisplayContent());
        assertEquals("[贴图]", v.getSearchableWord());

        WKEmojiStickerContent e = new WKEmojiStickerContent();
        assertEquals("[emoji表情]", e.getDisplayContent());
        assertEquals("[emoji表情]", e.getSearchableWord());
    }
}
