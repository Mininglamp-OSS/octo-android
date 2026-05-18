package com.xinbida.wukongim.msgmodel;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *  · Reply 引用预览 @SpaceName 字段单元测试。
 *
 * <p>验证 {@link WKReply} 在 wire 层 round-trip 新增的四个外部群字段
 * ({@code from_is_external / from_source_space_name / from_home_space_id /
 * from_home_space_name})，对齐 Web PR#1073 和 iOS。
 *
 * <p>测试策略：
 * <ul>
 *   <li>无 payload 场景覆盖 decode 路径（encode 需要 payload，另起用例）。</li>
 *   <li>full-roundtrip 场景通过 {@link WKTextContent} stub payload 验证
 *       encode → decode 的字段不丢失。</li>
 *   <li>字段缺失场景（老 server / 同 Space）保持默认值（0/null），避免污染老数据。</li>
 * </ul>
 */
public class WKReplyExternalFieldsTest {

    @Test
    public void decode_parsesAllFourExternalSourceFields() throws Exception {
        JSONObject json = new JSONObject()
                .put("root_mid", "rm1")
                .put("message_id", "mid1")
                .put("message_seq", 42L)
                .put("from_uid", "uid_b")
                .put("from_name", "Bob")
                .put("from_is_external", 1)
                .put("from_source_space_name", "Source Space")
                .put("from_home_space_id", "space_b")
                .put("from_home_space_name", "Space B");

        WKReply reply = new WKReply().decodeMsg(json);

        assertEquals("Bob", reply.from_name);
        assertEquals(1, reply.from_is_external);
        assertEquals("Source Space", reply.from_source_space_name);
        assertEquals("space_b", reply.from_home_space_id);
        assertEquals("Space B", reply.from_home_space_name);
    }

    @Test
    public void decode_missingExternalFieldsLeavesDefaults() throws Exception {
        JSONObject json = new JSONObject()
                .put("root_mid", "rm1")
                .put("message_id", "mid1")
                .put("from_uid", "uid_b")
                .put("from_name", "Bob");

        WKReply reply = new WKReply().decodeMsg(json);

        assertEquals(0, reply.from_is_external);
        assertNull(reply.from_source_space_name);
        assertNull(reply.from_home_space_id);
        assertNull(reply.from_home_space_name);
    }

    @Test
    public void encode_writesExternalFields_whenPopulated() {
        WKReply reply = new WKReply();
        reply.root_mid = "rm1";
        reply.message_id = "mid1";
        reply.message_seq = 42;
        reply.from_uid = "uid_b";
        reply.from_name = "Bob";
        reply.payload = new WKTextContent("hello");
        reply.from_is_external = 1;
        reply.from_source_space_name = "Source Space";
        reply.from_home_space_id = "space_b";
        reply.from_home_space_name = "Space B";

        JSONObject json = reply.encodeMsg();

        assertTrue(json.has("from_is_external"));
        assertEquals(1, json.optInt("from_is_external"));
        assertEquals("Source Space", json.optString("from_source_space_name"));
        assertEquals("space_b", json.optString("from_home_space_id"));
        assertEquals("Space B", json.optString("from_home_space_name"));
    }

    @Test
    public void encode_omitsExternalFields_whenAbsent() {
        WKReply reply = new WKReply();
        reply.root_mid = "rm1";
        reply.message_id = "mid1";
        reply.from_uid = "uid_b";
        reply.from_name = "Bob";
        reply.payload = new WKTextContent("hello");

        JSONObject json = reply.encodeMsg();

        assertFalse("encoder should not emit from_is_external when zero + no names",
                json.has("from_is_external"));
        assertFalse(json.has("from_source_space_name"));
        assertFalse(json.has("from_home_space_id"));
        assertFalse(json.has("from_home_space_name"));
    }

    @Test
    public void roundTrip_preservesAllFourFields() throws Exception {
        WKReply source = new WKReply();
        source.root_mid = "rm1";
        source.message_id = "mid1";
        source.message_seq = 42;
        source.from_uid = "uid_b";
        source.from_name = "Bob";
        source.payload = new WKTextContent("hello");
        source.from_is_external = 1;
        source.from_source_space_name = "Source Space";
        source.from_home_space_id = "space_b";
        source.from_home_space_name = "Space B";

        JSONObject wire = source.encodeMsg();

        // 跳过 payload 的 decode 路径（需要 MsgManager 单例），删除后直接 decode。
        wire.remove("payload");
        WKReply decoded = new WKReply().decodeMsg(wire);

        assertEquals(source.from_is_external, decoded.from_is_external);
        assertEquals(source.from_source_space_name, decoded.from_source_space_name);
        assertEquals(source.from_home_space_id, decoded.from_home_space_id);
        assertEquals(source.from_home_space_name, decoded.from_home_space_name);
    }

    @Test
    public void decode_onlyHomeSpaceIdPresent_leavesOthersAtDefault() throws Exception {
        JSONObject json = new JSONObject()
                .put("root_mid", "rm1")
                .put("message_id", "mid1")
                .put("from_uid", "uid_b")
                .put("from_home_space_id", "space_b");

        WKReply reply = new WKReply().decodeMsg(json);

        assertEquals("space_b", reply.from_home_space_id);
        assertEquals(0, reply.from_is_external);
        assertNull(reply.from_home_space_name);
        assertNull(reply.from_source_space_name);
    }
}
