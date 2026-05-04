package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKSyncConvMsg;

import org.junit.Test;

/**
 * YUJ-326 · Host-side 单测：验证 per-Space cache 涉及的 DTO/entity 字段契约。
 *
 * <p>不直接测 {@link WKSqlContentValues#getContentValuesWithCoverMsg}：host-side
 * {@code unitTests.returnDefaultValues=true} 让 {@code ContentValues.put/get} 全部 stub
 * 成 no-op/null，无法断言字段落地。真机 instrumentation test 可补上 ContentValues 路径；
 * 此处只守住"字段存在、类型正确、默认值合理"这条契约线，防止后续 refactor 误删字段。
 */
public class PerSpaceCoverMsgCVTest {

    @Test
    public void conversationMsgDefaultsSpaceIDToEmptyString() {
        WKConversationMsg msg = new WKConversationMsg();
        assertNotNull("spaceID 初值不能为 null（NOT NULL 列写入会拒绝）", msg.spaceID);
        assertEquals("", msg.spaceID);
    }

    @Test
    public void conversationMsgPreservesSpaceIDAssignment() {
        WKConversationMsg msg = new WKConversationMsg();
        msg.spaceID = "spaceA_32hex_aaaaaaaaaaaaaaaaaaaa";
        assertEquals("spaceA_32hex_aaaaaaaaaaaaaaaaaaaa", msg.spaceID);
    }

    @Test
    public void syncConvMsgDtoExposesPublicSpaceIdFieldForDeserialization()
            throws NoSuchFieldException {
        // Gson/fastjson 反序列化要求字段 public 且类型匹配 JSON key。
        // 这条契约守住"server dmworkim @759dd507 ship 的 SpaceID（api_conversation.go:1223）
        // Android 能解码到"。
        assertTrue(java.lang.reflect.Modifier.isPublic(
                WKSyncConvMsg.class.getField("space_id").getModifiers()));
        assertEquals(String.class, WKSyncConvMsg.class.getField("space_id").getType());

        WKSyncConvMsg dto = new WKSyncConvMsg();
        assertNull("DTO 未 hydrate 时 space_id 默认 null（与其它 string 字段一致）",
                dto.space_id);
        dto.space_id = "x";
        assertEquals("x", dto.space_id);
    }

    @Test
    public void dbColumnsDefinesSpaceIdConstant() {
        // WKCoverMessageColumns.space_id 被 WKSqlContentValues、ConversationDbManager 引用，
        // 不能被重命名或删除。contract lock-in。
        assertEquals("space_id", com.xinbida.wukongim.db.WKDBColumns.WKCoverMessageColumns.space_id);
    }
}
