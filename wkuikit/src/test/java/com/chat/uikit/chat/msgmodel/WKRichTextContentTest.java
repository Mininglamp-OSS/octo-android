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

package com.chat.uikit.chat.msgmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Locks the RichText (ContentType=14) decode / encode / plain-fallback contract.
 *
 * <p>RichText is a cross-platform <em>receive</em> path whose wire format is
 * pinned by octo-lib/common/richtext.go + octo-matter richtext.go: an ordered
 * {@code content} block array plus a top-level server-authoritative {@code plain}
 * mirror. If any of these decode invariants regress (block order lost, missing
 * {@code plain} not rebuilt, unknown block text dropped, encode round-trip
 * breaking the payload), one of these tests fires.
 *
 * <p>Runs under plain JVM (no Robolectric). The model deliberately avoids
 * {@code TextUtils.isEmpty} in its logic paths so the {@code plain}-fallback
 * branch is actually exercised here instead of being stubbed to a no-op by
 * {@code unitTests.returnDefaultValues=true}.
 */
public class WKRichTextContentTest {

    private static JSONObject textBlock(String text) throws JSONException {
        JSONObject b = new JSONObject();
        b.put("type", WKRichTextContent.BLOCK_TYPE_TEXT);
        b.put("text", text);
        return b;
    }

    private static JSONObject imageBlock(String url, int w, int h) throws JSONException {
        JSONObject b = new JSONObject();
        b.put("type", WKRichTextContent.BLOCK_TYPE_IMAGE);
        b.put("url", url);
        b.put("width", w);
        b.put("height", h);
        return b;
    }

    @Test
    public void decode_preservesOrderedTextAndImageBlocks() throws JSONException {
        JSONArray content = new JSONArray();
        content.put(textBlock("上线方案"));
        content.put(imageBlock("https://x/y.png", 10, 20));
        content.put(textBlock("收尾"));
        JSONObject payload = new JSONObject();
        payload.put("content", content);
        payload.put("plain", "上线方案[图片]收尾");

        WKRichTextContent c = new WKRichTextContent();
        c.decodeMsg(payload);

        assertEquals(3, c.blocks.size());
        // 顺序保留：text → image → text。
        assertTrue(c.blocks.get(0).isText());
        assertEquals("上线方案", c.blocks.get(0).text);
        assertTrue(c.blocks.get(1).isImage());
        assertEquals("https://x/y.png", c.blocks.get(1).url);
        assertEquals(10, c.blocks.get(1).width);
        assertEquals(20, c.blocks.get(1).height);
        assertTrue(c.blocks.get(2).isText());
        assertEquals("收尾", c.blocks.get(2).text);
        // 顶层 plain（server 权威）原样保留。
        assertEquals("上线方案[图片]收尾", c.plain);
        assertEquals("上线方案[图片]收尾", c.getDisplayContent());
        assertEquals("上线方案[图片]收尾", c.getSearchableWord());
    }

    @Test
    public void decode_missingPlain_rebuildsFromBlocks() throws JSONException {
        JSONArray content = new JSONArray();
        content.put(textBlock("封面"));
        content.put(imageBlock("https://x/cover.png", 8, 8));
        JSONObject payload = new JSONObject();
        payload.put("content", content);
        // 不带 plain 字段。

        WKRichTextContent c = new WKRichTextContent();
        c.decodeMsg(payload);

        // image 注入占位符，text 取 text，按序拼接。
        assertEquals("封面" + WKRichTextContent.IMAGE_PLACEHOLDER, c.plain);
    }

    @Test
    public void decode_blankPlain_rebuildsFromBlocks() throws JSONException {
        JSONArray content = new JSONArray();
        content.put(textBlock("正文"));
        JSONObject payload = new JSONObject();
        payload.put("content", content);
        payload.put("plain", "");

        WKRichTextContent c = new WKRichTextContent();
        c.decodeMsg(payload);

        assertEquals("正文", c.plain);
    }

    @Test
    public void decode_unknownBlockWithText_keepsTextInPlain() throws JSONException {
        JSONArray content = new JSONArray();
        content.put(textBlock("前"));
        JSONObject unknown = new JSONObject();
        unknown.put("type", "quote"); // 二期才支持的新类型
        unknown.put("text", "引用内容");
        content.put(unknown);
        content.put(textBlock("后"));
        JSONObject payload = new JSONObject();
        payload.put("content", content);
        // 不带 plain → 走 buildPlainFromBlocks 前向兼容路径。

        WKRichTextContent c = new WKRichTextContent();
        c.decodeMsg(payload);

        // 未知 block 带 text 仍拼进 plain，老端不丢字。
        assertEquals("前引用内容后", c.plain);
        assertEquals(3, c.blocks.size());
    }

    @Test
    public void encode_roundTripsBlocksAndPlain() throws JSONException {
        JSONArray content = new JSONArray();
        content.put(textBlock("hello"));
        content.put(imageBlock("https://x/z.png", 30, 40));
        JSONObject payload = new JSONObject();
        payload.put("content", content);
        payload.put("plain", "hello[图片]");

        WKRichTextContent decoded = new WKRichTextContent();
        decoded.decodeMsg(payload);

        JSONObject encoded = decoded.encodeMsg();
        assertNotNull(encoded);

        // 再解一次，断言关键字段对称（content 块 + plain）。
        WKRichTextContent reparsed = new WKRichTextContent();
        reparsed.decodeMsg(encoded);

        assertEquals(2, reparsed.blocks.size());
        assertTrue(reparsed.blocks.get(0).isText());
        assertEquals("hello", reparsed.blocks.get(0).text);
        assertTrue(reparsed.blocks.get(1).isImage());
        assertEquals("https://x/z.png", reparsed.blocks.get(1).url);
        assertEquals(30, reparsed.blocks.get(1).width);
        assertEquals(40, reparsed.blocks.get(1).height);
        assertEquals("hello[图片]", reparsed.plain);
    }

    @Test
    public void decode_nullJson_isSafe() {
        WKRichTextContent c = new WKRichTextContent();
        // 不应抛异常。
        c.decodeMsg(null);
        assertNotNull(c.blocks);
    }

    // ---------------------------------------------------------------------
    // 发送侧（Phase 1 图文混排）：工厂 / URL 安全 / plain 占位 / encode↔decode 往返
    // ---------------------------------------------------------------------

    @Test
    public void send_create_interleavesTextThenImagesAndFillsPlaceholderPlain() {
        java.util.List<WKRichTextContent.RichTextBlock> blocks = new java.util.ArrayList<>();
        blocks.add(WKRichTextContent.makeTextBlock("看图"));
        blocks.add(WKRichTextContent.makeImageBlock("https://x/a.png", 100, 200));
        blocks.add(WKRichTextContent.makeImageBlock("https://x/b.png", 50, 60));

        WKRichTextContent c = WKRichTextContent.create(blocks);

        assertEquals(3, c.blocks.size());
        assertTrue(c.blocks.get(0).isText());
        assertTrue(c.blocks.get(1).isImage());
        assertTrue(c.blocks.get(2).isImage());
        // plain 仅本地占位（image → 占位 wire token），非权威，server #232 覆盖。
        assertEquals("看图" + WKRichTextContent.IMAGE_PLACEHOLDER
                + WKRichTextContent.IMAGE_PLACEHOLDER, c.plain);
    }

    @Test
    public void send_encodeDecode_roundTripsMixedPayload() throws JSONException {
        java.util.List<WKRichTextContent.RichTextBlock> blocks = new java.util.ArrayList<>();
        blocks.add(WKRichTextContent.makeTextBlock("hi"));
        blocks.add(WKRichTextContent.makeImageBlock("https://x/z.png", 30, 40));
        WKRichTextContent sent = WKRichTextContent.create(blocks);

        // 发送侧 encode → wire JSON。
        JSONObject wire = sent.encodeMsg();
        assertNotNull(wire);
        assertTrue(wire.has("content"));

        // 接收侧 decode 同一 wire，断言字段逐项对称。
        WKRichTextContent received = new WKRichTextContent();
        received.decodeMsg(wire);

        assertEquals(2, received.blocks.size());
        assertTrue(received.blocks.get(0).isText());
        assertEquals("hi", received.blocks.get(0).text);
        assertTrue(received.blocks.get(1).isImage());
        assertEquals("https://x/z.png", received.blocks.get(1).url);
        assertEquals(30, received.blocks.get(1).width);
        assertEquals(40, received.blocks.get(1).height);
        assertEquals("hi" + WKRichTextContent.IMAGE_PLACEHOLDER, received.plain);
    }

    @Test
    public void send_encode_imageBlockOmitsTextField_byteHygiene() throws JSONException {
        // image block 不应在 wire 写出 text 字段（字节卫生，与接收侧/server 契约对齐）。
        java.util.List<WKRichTextContent.RichTextBlock> blocks = new java.util.ArrayList<>();
        blocks.add(WKRichTextContent.makeImageBlock("https://x/i.png", 10, 10));
        WKRichTextContent c = WKRichTextContent.create(blocks);

        JSONObject wire = c.encodeMsg();
        JSONArray content = wire.optJSONArray("content");
        assertNotNull(content);
        JSONObject imgJson = content.optJSONObject(0);
        assertEquals(WKRichTextContent.BLOCK_TYPE_IMAGE, imgJson.optString("type"));
        assertTrue(imgJson.has("url"));
        assertTrue(imgJson.has("width"));
        assertTrue(imgJson.has("height"));
        // image block 不写 text 键。
        assertTrue(!imgJson.has("text"));
    }

    @Test
    public void send_isSafeImageUrl_allowsHttpAndRelative_blocksInjection() {
        // 放行 http/https 与以 / 开头的 server 相对路径。
        assertTrue(WKRichTextContent.isSafeImageUrl("https://cdn/x.png"));
        assertTrue(WKRichTextContent.isSafeImageUrl("http://cdn/x.png"));
        assertTrue(WKRichTextContent.isSafeImageUrl("HTTPS://cdn/x.png"));
        assertTrue(WKRichTextContent.isSafeImageUrl("/0/123/abc.png"));

        // 阻断带 // 的注入向量。
        assertTrue(!WKRichTextContent.isSafeImageUrl("javascript:alert(1)"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("data:image/png;base64,AAAA"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("file:///etc/passwd"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("vbscript:msgbox"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("ftp://host/x.png"));
        assertTrue(!WKRichTextContent.isSafeImageUrl(""));
        assertTrue(!WKRichTextContent.isSafeImageUrl(null));

        // YUJ-2832 🟡：白名单——不带 :// 的伪 scheme 旧黑名单逻辑会误放行，现必须拒绝。
        assertTrue(!WKRichTextContent.isSafeImageUrl("mailto:a@b.com"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("content://media/external/images/1"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("tel:10086"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("sms:10086"));
        // 不以 / 开头的裸相对名（无 scheme）也拒绝——server 资源路径恒以 / 开头。
        assertTrue(!WKRichTextContent.isSafeImageUrl("abc.png"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("../../etc/passwd"));

        // YUJ-2872 🟡：收紧为单斜杠相对路径——以 // 开头的 protocol-relative URL 会被
        // 解析成外站绝对地址（//evil.host/x → https://evil.host/x），与「仅 server 相对
        // 路径」契约不符，必须拒绝。单斜杠相对路径仍放行。
        assertTrue(!WKRichTextContent.isSafeImageUrl("//evil.host/x.png"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("//cdn.attacker.com/a/b.png"));
        assertTrue(!WKRichTextContent.isSafeImageUrl("  //evil.host/x.png"));
        assertTrue(WKRichTextContent.isSafeImageUrl("/single/slash/ok.png"));
    }
}
