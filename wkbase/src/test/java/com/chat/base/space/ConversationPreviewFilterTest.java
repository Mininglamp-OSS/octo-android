package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgExtra;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * YUJ-219-B · {@link ConversationPreviewFilter} host-side 单元测试。
 *
 * <p>覆盖 Layer B render-time filter 的 PERSONAL 频道 payload.space_id 语义
 * （对齐 iOS {@code spaceFilteredLastMessage} + Web {@code getSpaceFilteredLastMessage}）：
 * <ul>
 *     <li>SystemBot + msg.space_id == currentSpace → 不过滤</li>
 *     <li>SystemBot + msg.space_id != currentSpace → 过滤（跨 Space 污染）</li>
 *     <li>SystemBot + 无 space_id → 过滤（对齐 Web 隐藏口径）</li>
 *     <li>SystemBot + wkMsg = null（占位 entry） → 不过滤</li>
 *     <li>非 SystemBot + msg.space_id != current → 过滤</li>
 *     <li>非 SystemBot + 无 space_id → 不过滤（向前兼容老消息）</li>
 *     <li>非 Space 模式 → 不过滤</li>
 * </ul>
 *
 * <p>GROUP / COMMUNITY_TOPIC 的分支依赖 {@link SpaceFilter} 的 SDK 缓存路径，
 * host-side 走 fail-open，覆盖在 {@code SpaceFilterTest} 里做。
 */
public class ConversationPreviewFilterTest {

    private static final String SPACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Before
    public void setUp() {
        SystemBotsFallback.setTestOverride(new LinkedHashSet<>(
                Arrays.asList("botfather", "u_10000", "fileHelper")));
    }

    @After
    public void tearDown() {
        SystemBotsFallback.setTestOverride(null);
    }

    // ------------------------------------------------------------------
    // 无 Space 模式（放行一切）
    // ------------------------------------------------------------------

    @Test
    public void notSpaceMode_noFiltering() {
        WKUIConversationMsg uc = makeUc("botfather", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_B));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, ""));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, null));
    }

    // ------------------------------------------------------------------
    // PERSONAL · SystemBot
    // ------------------------------------------------------------------

    @Test
    public void personal_systemBot_msgMatchesCurrentSpace_notCross() {
        WKUIConversationMsg uc = makeUc("botfather", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_A));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_systemBot_msgOtherSpace_isCross() {
        WKUIConversationMsg uc = makeUc("botfather", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_B));
        assertTrue(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_systemBot_msgNoSpaceId_isCross() {
        // 对齐 Web SpaceService.getSpaceFilteredLastMessage：SYSTEM_BOTS + 无 space_id → hide
        WKUIConversationMsg uc = makeUc("botfather", WKChannelType.PERSONAL, wkMsgWithSpaceId(null));
        assertTrue(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_systemBot_wkMsgNull_notCross() {
        // SystemBotsFallback.buildPlaceholder 合成的占位，wkMsg = null；
        // 过滤函数内部 try/catch 兜住 DB 访问失败的 host-side 路径。
        WKUIConversationMsg uc = makeUc("botfather", WKChannelType.PERSONAL, null);
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_u10000_sameRules() {
        assertTrue(ConversationPreviewFilter.isMessageCrossSpace(
                makeUc("u_10000", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_B)), SPACE_A));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(
                makeUc("u_10000", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_A)), SPACE_A));
    }

    @Test
    public void personal_fileHelper_sameRules() {
        assertTrue(ConversationPreviewFilter.isMessageCrossSpace(
                makeUc("fileHelper", WKChannelType.PERSONAL, wkMsgWithSpaceId(null)), SPACE_A));
    }

    // ------------------------------------------------------------------
    // PERSONAL · 非 SystemBot（普通 DM）
    // ------------------------------------------------------------------

    @Test
    public void personal_regular_msgMatchesCurrentSpace_notCross() {
        WKUIConversationMsg uc = makeUc("friend_uid", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_A));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_regular_msgOtherSpace_isCross() {
        WKUIConversationMsg uc = makeUc("friend_uid", WKChannelType.PERSONAL, wkMsgWithSpaceId(SPACE_B));
        assertTrue(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    @Test
    public void personal_regular_msgNoSpaceId_notCross_forwardCompat() {
        // 普通 DM + 无 space_id → 向前兼容（老消息没写 space_id）
        WKUIConversationMsg uc = makeUc("friend_uid", WKChannelType.PERSONAL, wkMsgWithSpaceId(null));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(uc, SPACE_A));
    }

    // ------------------------------------------------------------------
    // null 入参防御
    // ------------------------------------------------------------------

    @Test
    public void getters_nullInput_returnsSafeDefaults() {
        assertNull(ConversationPreviewFilter.getSpaceFilteredWkMsg(null));
        assertEquals(0L, ConversationPreviewFilter.getSpaceFilteredTimestamp(null));
        assertEquals(0, ConversationPreviewFilter.getSpaceFilteredUnread(null));
        assertFalse(ConversationPreviewFilter.isMessageCrossSpace(null, SPACE_A));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WKUIConversationMsg makeUc(String channelID, byte channelType, WKMsg msg) {
        WKUIConversationMsg uc = new WKUIConversationMsg();
        uc.channelID = channelID;
        uc.channelType = channelType;
        uc.clientMsgNo = "";
        uc.setWkMsg(msg);
        return uc;
    }

    /** 构造一个 WKMsg，content 中有/无 space_id。null spaceId 表示不写 space_id。 */
    private static WKMsg wkMsgWithSpaceId(String spaceId) {
        WKMsg msg = new WKMsg();
        msg.clientMsgNO = "m1";
        msg.remoteExtra = new WKMsgExtra();
        if (spaceId != null) {
            msg.content = "{\"type\":1,\"content\":\"hi\",\"space_id\":\"" + spaceId + "\"}";
        } else {
            msg.content = "{\"type\":1,\"content\":\"hi\"}";
        }
        return msg;
    }
}
