package com.chat.uikit.chat.msgmodel;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 外部群 Phase 1 — YUJ-86 EP1 合并转发 users 透传测试（对齐 web #981）。
 *
 * 合并转发消息里的 users 列表在二次转发时容易丢字段（web 侧历史就是这样踩过坑）。
 * 该测试锁死 decode / encode 两侧对 is_external / source_space_name /
 * home_space_id / home_space_name 的透传行为。
 */
public class WKMultiForwardContentExternalFieldsTest {

    private static JSONObject userJson(String uid, String name,
                                       Integer isExternal,
                                       String sourceSpaceId,
                                       String sourceSpaceName,
                                       String homeSpaceId,
                                       String homeSpaceName) throws JSONException {
        JSONObject u = new JSONObject();
        u.put("uid", uid);
        u.put("name", name);
        u.put("avatar", "");
        if (isExternal != null) u.put("is_external", isExternal);
        if (sourceSpaceId != null) u.put("source_space_id", sourceSpaceId);
        if (sourceSpaceName != null) u.put("source_space_name", sourceSpaceName);
        if (homeSpaceId != null) u.put("home_space_id", homeSpaceId);
        if (homeSpaceName != null) u.put("home_space_name", homeSpaceName);
        return u;
    }

    @Test
    public void decodeMsg_readsAllExternalFieldsIntoRemoteExtraMap() throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("channel_type", 2);
        payload.put("msgs", new JSONArray());
        JSONArray users = new JSONArray();
        users.put(userJson("uid_bob", "Bob", 1, "space_beta", "Beta", "space_alpha", "Alpha"));
        users.put(userJson("uid_alice", "Alice", 0, null, null, null, null));
        payload.put("users", users);

        WKMultiForwardContent c = new WKMultiForwardContent();
        c.decodeMsg(payload);

        assertEquals(2, c.userList.size());

        WKChannel bob = c.userList.get(0);
        assertEquals("uid_bob", bob.channelID);
        assertNotNull(bob.remoteExtraMap);
        assertEquals(1, bob.remoteExtraMap.get(WKChannelMemberExtras.isExternal));
        assertEquals("space_beta", bob.remoteExtraMap.get(WKChannelMemberExtras.sourceSpaceID));
        assertEquals("Beta", bob.remoteExtraMap.get(WKChannelMemberExtras.sourceSpaceName));
        assertEquals("space_alpha", bob.remoteExtraMap.get(WKChannelMemberExtras.homeSpaceID));
        assertEquals("Alpha", bob.remoteExtraMap.get(WKChannelMemberExtras.homeSpaceName));

        // Alice 内部成员：is_external=0 应落库（UI 据此决定是否渲染徽章），
        // 但 source/home 都为空 → 不应有对应 key。
        WKChannel alice = c.userList.get(1);
        assertNotNull(alice.remoteExtraMap);
        assertEquals(0, alice.remoteExtraMap.get(WKChannelMemberExtras.isExternal));
        assertFalse(alice.remoteExtraMap.containsKey(WKChannelMemberExtras.sourceSpaceID));
        assertFalse(alice.remoteExtraMap.containsKey(WKChannelMemberExtras.sourceSpaceName));
        assertFalse(alice.remoteExtraMap.containsKey(WKChannelMemberExtras.homeSpaceID));
        assertFalse(alice.remoteExtraMap.containsKey(WKChannelMemberExtras.homeSpaceName));
    }

    @Test
    public void decodeMsg_leavesExtraMapNull_whenNoExternalFields() throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("channel_type", 2);
        payload.put("msgs", new JSONArray());
        JSONArray users = new JSONArray();
        JSONObject u = new JSONObject();
        u.put("uid", "uid_noone");
        u.put("name", "NoOne");
        u.put("avatar", "");
        users.put(u);
        payload.put("users", users);

        WKMultiForwardContent c = new WKMultiForwardContent();
        c.decodeMsg(payload);

        // 完全没有外部字段的旧版载荷 → 不污染 remoteExtraMap
        assertNull(c.userList.get(0).remoteExtraMap);
    }

    @Test
    public void encodeMsg_writesAllExternalFieldsBack() throws JSONException {
        WKMultiForwardContent c = new WKMultiForwardContent();
        c.channelType = (byte) 2;
        c.msgList = new ArrayList<>();
        c.userList = new ArrayList<>();

        WKChannel bob = new WKChannel();
        bob.channelID = "uid_bob";
        bob.channelName = "Bob";
        bob.avatar = "";
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        extras.put(WKChannelMemberExtras.sourceSpaceID, "space_beta");
        extras.put(WKChannelMemberExtras.sourceSpaceName, "Beta");
        extras.put(WKChannelMemberExtras.homeSpaceID, "space_alpha");
        extras.put(WKChannelMemberExtras.homeSpaceName, "Alpha");
        bob.remoteExtraMap = extras;
        c.userList.add(bob);

        JSONObject json = c.encodeMsg();
        JSONArray usersOut = json.getJSONArray("users");
        assertEquals(1, usersOut.length());
        JSONObject u = usersOut.getJSONObject(0);
        assertEquals(1, u.getInt("is_external"));
        assertEquals("space_beta", u.getString("source_space_id"));
        assertEquals("Beta", u.getString("source_space_name"));
        assertEquals("space_alpha", u.getString("home_space_id"));
        assertEquals("Alpha", u.getString("home_space_name"));
    }

    @Test
    public void encode_thenDecode_roundTripsExternalFields() throws JSONException {
        WKMultiForwardContent c = new WKMultiForwardContent();
        c.channelType = (byte) 2;
        c.msgList = new ArrayList<>();
        c.userList = new ArrayList<>();

        WKChannel bob = new WKChannel();
        bob.channelID = "uid_bob";
        bob.channelName = "Bob";
        bob.avatar = "";
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        extras.put(WKChannelMemberExtras.sourceSpaceID, "space_beta");
        extras.put(WKChannelMemberExtras.sourceSpaceName, "Beta");
        extras.put(WKChannelMemberExtras.homeSpaceID, "space_alpha");
        extras.put(WKChannelMemberExtras.homeSpaceName, "Alpha");
        bob.remoteExtraMap = extras;
        c.userList.add(bob);

        JSONObject encoded = c.encodeMsg();

        WKMultiForwardContent decoded = new WKMultiForwardContent();
        decoded.decodeMsg(encoded);

        assertEquals(1, decoded.userList.size());
        WKChannel roundTrip = decoded.userList.get(0);
        assertNotNull(roundTrip.remoteExtraMap);
        assertEquals(1, roundTrip.remoteExtraMap.get(WKChannelMemberExtras.isExternal));
        assertEquals("space_beta", roundTrip.remoteExtraMap.get(WKChannelMemberExtras.sourceSpaceID));
        assertEquals("Beta", roundTrip.remoteExtraMap.get(WKChannelMemberExtras.sourceSpaceName));
        assertEquals("space_alpha", roundTrip.remoteExtraMap.get(WKChannelMemberExtras.homeSpaceID));
        assertEquals("Alpha", roundTrip.remoteExtraMap.get(WKChannelMemberExtras.homeSpaceName));
    }

    @Test
    public void encodeMsg_skipsExternalFields_whenExtraMapNull() throws JSONException {
        WKMultiForwardContent c = new WKMultiForwardContent();
        c.channelType = (byte) 2;
        c.msgList = new ArrayList<>();
        c.userList = new ArrayList<>();

        WKChannel alice = new WKChannel();
        alice.channelID = "uid_alice";
        alice.channelName = "Alice";
        alice.avatar = "";
        // remoteExtraMap = null (老客户端发送的消息)
        c.userList.add(alice);

        JSONObject json = c.encodeMsg();
        JSONObject u = json.getJSONArray("users").getJSONObject(0);
        assertTrue(u.isNull("is_external") || !u.has("is_external"));
        assertFalse(u.has("source_space_id"));
        assertFalse(u.has("source_space_name"));
        assertFalse(u.has("home_space_id"));
        assertFalse(u.has("home_space_name"));
    }

    /**
     * claude review P1 回归保护：AOSP JSONObject.optString 对 JSON null 返回字符串 "null"
     * （而非空串），若不先 isNull() 守卫，会把字面量 "null" 写进 remoteExtraMap，
     * UI 侧就会展示一个叫 "null" 的 Space。
     *
     * 注：host JVM 用的 json.org 实现 optString 返回 ""，所以在单测里走 AOSP 路径需要
     * 我们显式用 JSONObject.NULL 触发 "null" 语义。decode 逻辑先 isNull() 守卫，
     * 这些字段就都不应该进 remoteExtraMap。
     */
    @Test
    public void decodeMsg_treatsJsonNull_asAbsent_notLiteralNullString() throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("channel_type", 2);
        payload.put("msgs", new JSONArray());
        JSONArray users = new JSONArray();
        JSONObject u = new JSONObject();
        u.put("uid", "uid_bob");
        u.put("name", "Bob");
        u.put("avatar", "");
        u.put("is_external", 1);
        u.put("source_space_id", JSONObject.NULL);
        u.put("source_space_name", JSONObject.NULL);
        u.put("home_space_id", JSONObject.NULL);
        u.put("home_space_name", JSONObject.NULL);
        users.put(u);
        payload.put("users", users);

        WKMultiForwardContent c = new WKMultiForwardContent();
        c.decodeMsg(payload);

        WKChannel bob = c.userList.get(0);
        assertNotNull(bob.remoteExtraMap);
        assertEquals(1, bob.remoteExtraMap.get(WKChannelMemberExtras.isExternal));
        // 关键：不能出现字面量 "null" 作为 Space ID / name
        assertFalse(bob.remoteExtraMap.containsKey(WKChannelMemberExtras.sourceSpaceID));
        assertFalse(bob.remoteExtraMap.containsKey(WKChannelMemberExtras.sourceSpaceName));
        assertFalse(bob.remoteExtraMap.containsKey(WKChannelMemberExtras.homeSpaceID));
        assertFalse(bob.remoteExtraMap.containsKey(WKChannelMemberExtras.homeSpaceName));
    }

    /**
     * claude review round-2 P2 回归保护：encode 路径必须对空串同样跳过，与 decode 对称。
     * 旧客户端可能通过 WKCommonUtils.str2HashMap 把空串落进 remoteExtraMap，
     * 不加守卫就会让 "source_space_name":"" 进入线上 payload 污染下游。
     */
    @Test
    public void encodeMsg_skipsEmptyStringFields_onRoundTrip() throws JSONException {
        WKMultiForwardContent c = new WKMultiForwardContent();
        c.channelType = (byte) 2;
        c.msgList = new ArrayList<>();
        c.userList = new ArrayList<>();

        WKChannel bob = new WKChannel();
        bob.channelID = "uid_bob";
        bob.channelName = "Bob";
        bob.avatar = "";
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        // 所有 String 字段都是空串——encode 应该全部跳过，只留 is_external
        extras.put(WKChannelMemberExtras.sourceSpaceID, "");
        extras.put(WKChannelMemberExtras.sourceSpaceName, "");
        extras.put(WKChannelMemberExtras.homeSpaceID, "");
        extras.put(WKChannelMemberExtras.homeSpaceName, "");
        bob.remoteExtraMap = extras;
        c.userList.add(bob);

        JSONObject json = c.encodeMsg();
        JSONObject u = json.getJSONArray("users").getJSONObject(0);
        assertEquals(1, u.getInt("is_external"));
        assertFalse(u.has("source_space_id"));
        assertFalse(u.has("source_space_name"));
        assertFalse(u.has("home_space_id"));
        assertFalse(u.has("home_space_name"));
    }
}
