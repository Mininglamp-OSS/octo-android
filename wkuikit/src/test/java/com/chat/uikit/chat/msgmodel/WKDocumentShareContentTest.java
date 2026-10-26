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

import org.json.JSONObject;
import org.junit.Test;

/**
 * Locks the DocumentShareCard (ContentType=18, 对齐 octo-web docShareCard) decode /
 * encode / 容错契约。纯 JVM 断言路径：不 stub Context / TextUtils，类别与摘要的
 * 真实资源断言放在 androidTest（WKDocumentShareContentResourceTest）。
 */
public class WKDocumentShareContentTest {

    /** 相对 URL 兼容样例（含 type=18）。 */
    private static final String REAL_SAMPLE = "{"
            + "\"type\":18,"
            + "\"doc_id\":\"7f3a2b1c\","
            + "\"space_id\":\"sp-9\","
            + "\"kind\":\"doc\","
            + "\"title\":\"无标题文档\","
            + "\"owner_name\":\"\","
            + "\"updated_at\":\"\","
            + "\"url\":\"/d/7f3a2b1c?from=share\","
            + "\"permission\":\"reader\""
            + "}";

    /** 实抓样例：web 端 DocumentShareCardContent 的线上载荷形状（含 type=18）。 */
    private static final String REAL_CAPTURED_SAMPLE = "{"
            + "\"type\":18,"
            + "\"doc_id\":\"d_1e51f08eb62a34e40a102d7c\","
            + "\"space_id\":\"minglue_default\","
            + "\"kind\":\"doc\","
            + "\"title\":\"无标题文档\","
            + "\"owner_name\":\"\","
            + "\"updated_at\":\"\","
            + "\"url\":\"https://im.deepminer.com.cn/d/d_1e51f08eb62a34e40a102d7c\","
            + "\"permission\":\"reader\""
            + "}";

    private static JSONObject realSample() throws Exception {
        return new JSONObject(REAL_SAMPLE);
    }

    @Test
    public void decode_realSample_allNineFieldsMapped() throws Exception {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(realSample());

        assertEquals(18, c.type);
        assertEquals("7f3a2b1c", c.docId);
        assertEquals("sp-9", c.spaceId);
        assertEquals("doc", c.kind);
        assertEquals("无标题文档", c.title);
        assertEquals("", c.ownerName);
        assertEquals("", c.updatedAt);
        assertEquals("/d/7f3a2b1c?from=share", c.url);
        assertEquals("reader", c.permission);
    }

    @Test
    public void encodeDecode_roundTripsAllFields() throws Exception {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(realSample());

        JSONObject wire = c.encodeMsg();
        assertNotNull(wire);

        WKDocumentShareContent reparsed = new WKDocumentShareContent();
        reparsed.decodeMsg(wire);

        assertEquals(18, reparsed.type);
        assertEquals("7f3a2b1c", reparsed.docId);
        assertEquals("sp-9", reparsed.spaceId);
        assertEquals("doc", reparsed.kind);
        assertEquals("无标题文档", reparsed.title);
        assertEquals("", reparsed.ownerName);
        assertEquals("", reparsed.updatedAt);
        assertEquals("/d/7f3a2b1c?from=share", reparsed.url);
        assertEquals("reader", reparsed.permission);
    }

    @Test
    public void decode_realCapturedSample_allNineFieldsMapped() throws Exception {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(new JSONObject(REAL_CAPTURED_SAMPLE));

        assertEquals(18, c.type);
        assertEquals("d_1e51f08eb62a34e40a102d7c", c.docId);
        assertEquals("minglue_default", c.spaceId);
        assertEquals("doc", c.kind);
        assertEquals("无标题文档", c.title);
        assertEquals("", c.ownerName);
        assertEquals("", c.updatedAt);
        assertEquals("https://im.deepminer.com.cn/d/d_1e51f08eb62a34e40a102d7c", c.url);
        assertEquals("reader", c.permission);
    }

    @Test
    public void encodeDecode_realCapturedSample_roundTripsAllFields() throws Exception {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(new JSONObject(REAL_CAPTURED_SAMPLE));

        JSONObject wire = c.encodeMsg();
        assertNotNull(wire);

        WKDocumentShareContent reparsed = new WKDocumentShareContent();
        reparsed.decodeMsg(wire);

        assertEquals(18, reparsed.type);
        assertEquals("d_1e51f08eb62a34e40a102d7c", reparsed.docId);
        assertEquals("minglue_default", reparsed.spaceId);
        assertEquals("doc", reparsed.kind);
        assertEquals("无标题文档", reparsed.title);
        assertEquals("", reparsed.ownerName);
        assertEquals("", reparsed.updatedAt);
        assertEquals("https://im.deepminer.com.cn/d/d_1e51f08eb62a34e40a102d7c", reparsed.url);
        assertEquals("reader", reparsed.permission);
    }

    @Test
    public void encodeDecode_roundTripsFormattedUpdatedAt() throws Exception {
        JSONObject payload = realSample().put("updated_at", "2026-10-26 14:30");
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(payload);

        JSONObject wire = c.encodeMsg();
        assertEquals("2026-10-26 14:30", wire.optString("updated_at"));

        WKDocumentShareContent reparsed = new WKDocumentShareContent();
        reparsed.decodeMsg(wire);
        assertEquals("2026-10-26 14:30", reparsed.updatedAt);
    }

    @Test
    public void decode_missingFields_defaultsToEmptyStrings() throws Exception {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(new JSONObject());

        assertEquals("", c.docId);
        assertEquals("", c.spaceId);
        assertEquals("doc", c.kind);
        assertEquals("", c.title);
        assertEquals("", c.ownerName);
        assertEquals("", c.updatedAt);
        assertEquals("", c.url);
        assertEquals("reader", c.permission);
    }

    @Test
    public void decode_wrongFieldTypes_defaultToEmptyStrings() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("doc_id", 12345);
        payload.put("space_id", true);
        payload.put("kind", false);
        payload.put("title", new JSONObject());
        payload.put("owner_name", new org.json.JSONArray());
        payload.put("updated_at", 1735689600000L);
        payload.put("url", JSONObject.NULL);
        payload.put("permission", 3.14);

        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(payload);

        // 非 JSON 字符串一律空串，不做 toString() 强制转换。
        assertEquals("", c.docId);
        assertEquals("", c.spaceId);
        assertEquals("doc", c.kind);
        assertEquals("", c.title);
        assertEquals("", c.ownerName);
        assertEquals("", c.updatedAt);
        assertEquals("", c.url);
        assertEquals("reader", c.permission);

        // 合法字符串保持原值。
        JSONObject strPayload = realSample();
        strPayload.put("doc_id", 12345);
        strPayload.put("doc_id", "abc");
        WKDocumentShareContent s = new WKDocumentShareContent();
        s.decodeMsg(strPayload);
        assertEquals("abc", s.docId);
    }

    @Test
    public void decode_nullJson_isSafe() {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(null);
        assertEquals(18, c.type);
        assertEquals("", c.title);
    }

    @Test
    public void decode_kindVariants_onlyKnownValuesAccepted() throws Exception {
        for (String kind : new String[]{"doc", "sheet", "board", "html"}) {
            JSONObject payload = realSample().put("kind", kind);
            WKDocumentShareContent c = new WKDocumentShareContent();
            c.decodeMsg(payload);
            assertEquals(kind, c.kind);
        }
        WKDocumentShareContent unknown = new WKDocumentShareContent();
        unknown.decodeMsg(realSample().put("kind", "future-kind").put("permission", "future-role"));
        assertEquals("doc", unknown.kind);
        assertEquals("reader", unknown.permission);
        WKDocumentShareContent nulls = new WKDocumentShareContent();
        nulls.decodeMsg(new JSONObject().put("kind", JSONObject.NULL).put("permission", JSONObject.NULL));
        assertEquals("doc", nulls.kind);
        assertEquals("reader", nulls.permission);
    }

    @Test
    public void decode_permissionVariants_onlyKnownValuesAccepted() throws Exception {
        for (String permission : new String[]{"reader", "commenter", "writer"}) {
            WKDocumentShareContent c = new WKDocumentShareContent();
            c.decodeMsg(realSample().put("permission", permission));
            assertEquals(permission, c.permission);
        }
        WKDocumentShareContent empty = new WKDocumentShareContent();
        empty.decodeMsg(realSample().put("kind", "").put("permission", ""));
        assertEquals("doc", empty.kind);
        assertEquals("reader", empty.permission);
    }

    @Test public void encode_nullEnumsFallBackToProtocolDefaults() {
        WKDocumentShareContent c = new WKDocumentShareContent(); c.kind = null; c.permission = null;
        assertEquals("doc", c.encodeMsg().optString("kind")); assertEquals("reader", c.encodeMsg().optString("permission"));
    }
}
