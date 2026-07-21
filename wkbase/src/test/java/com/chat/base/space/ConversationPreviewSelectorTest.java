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

package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
 * {@link ConversationPreviewSelector} 的纯函数单测（不触发 SDK DB）。
 *
 * <p>核心目标：验证 iOS-parity 的 5 条 spaceFilteredLastMessage 分支：
 * <ol>
 *     <li>非多 space 模式 → rawMsg 原样</li>
 *     <li>rawMsg.space_id 匹配当前 space → rawMsg</li>
 *     <li>rawMsg 无 space_id + 非 BotFather → rawMsg（AI 回复默认属于当前 space）</li>
 *     <li>rawMsg 无 space_id + BotFather → DB 分页找（走 SDK）</li>
 *     <li>rawMsg 属于其它 space → DB 分页找（走 SDK）</li>
 * </ol>
 *
 * <p>{@code findSpaceScopedMessage} / {@code queryLatestMsg} 依赖 {@code MsgDbManager}
 * 单例（SDK 持久化），host-side 无法初始化 → 分页 / DB 兜底路径的单测在
 * 集成测试里覆盖，此处只测纯 in-memory 判定分支。
 */
public class ConversationPreviewSelectorTest {

    private static final String SPACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Before
    public void setUp() {
        SystemBotsFallback.setTestOverride(new LinkedHashSet<>(
                Arrays.asList("botfather", "u_10000", "fileHelper", "notification")));
    }

    @After
    public void tearDown() {
        SystemBotsFallback.setTestOverride(null);
    }

    // ------------------------------------------------------------------
    // selectDisplayMessage —— iOS parity
    // ------------------------------------------------------------------

    @Test
    public void nonSpaceMode_returnsRawMsg() {
        WKMsg raw = makeMsg(SPACE_A, 100L);
        WKUIConversationMsg uc = makeUc("botfather", raw);
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(uc, null));
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(uc, ""));
    }

    @Test
    public void rawMsgMatchesCurrentSpace_returnsRaw() {
        WKMsg raw = makeMsg(SPACE_A, 100L);
        WKUIConversationMsg uc = makeUc("botfather", raw);
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(uc, SPACE_A));
    }

    @Test
    public void rawMsgNoSpaceId_nonBotFather_returnsRaw_globalBotException() {
        // 对齐 iOS "非 BotFather：视为属于当前空间"
        WKMsg raw = makeMsg(null, 100L);
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(
                makeUc("u_10000", raw), SPACE_A));
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(
                makeUc("notification", raw), SPACE_A));
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(
                makeUc("fileHelper", raw), SPACE_A));
    }

    @Test
    public void rawMsgNoSpaceId_regularDM_returnsRaw() {
        // 普通私聊（非 SystemBot）无 space_id 也放行（向前兼容老消息）
        WKMsg raw = makeMsg(null, 100L);
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(
                makeUc("friend_uid", raw), SPACE_A));
    }

    @Test
    public void rawMsgOtherSpace_regularDM_returnsNull_strictLikeOldBehavior() {
        // 真人 DM (非 SystemBot) + raw 明确带其它 space_id → 返回 null，preview 空。
        // 保持旧严格 Space 隔离行为，不做 DB 分页 / 无 space_id 兜底
        // （用户要求"改动只对 SystemBot 生效，不影响真人私聊现有行为"）。
        WKMsg raw = makeMsg(SPACE_B, 100L);
        assertNull(ConversationPreviewSelector.selectDisplayMessage(
                makeUc("friend_uid", raw), SPACE_A));
    }

    @Test
    public void rawMsgOtherSpace_regularDM_nonSpaceMode_returnsRaw() {
        // 非多 space 模式下真人 DM 直接返回 raw（不做 space 判定）
        WKMsg raw = makeMsg(SPACE_B, 100L);
        assertSame(raw, ConversationPreviewSelector.selectDisplayMessage(
                makeUc("friend_uid", raw), null));
    }

    // ------------------------------------------------------------------
    // selectDisplayTimestamp —— iOS -[WKConversationWrapModel lastMsgTimestamp]
    // ------------------------------------------------------------------

    @Test
    public void timestamp_nonSystemBot_returnsRawLastMsgTimestamp() {
        // 普通频道 selector 直接返回 uc.lastMsgTimestamp，不管 wkMsg 是啥
        WKUIConversationMsg uc = makeUc("friend_uid", null);
        uc.lastMsgTimestamp = 2000L;
        assertEquals(2000L, ConversationPreviewSelector.selectDisplayTimestamp(uc, SPACE_A));
    }

    @Test
    public void timestamp_nonSystemBot_nonSpaceMode_returnsRaw() {
        WKUIConversationMsg uc = makeUc("friend_uid", null);
        uc.lastMsgTimestamp = 3000L;
        assertEquals(3000L, ConversationPreviewSelector.selectDisplayTimestamp(uc, (String) null));
    }

    @Test
    public void timestamp_systemBot_nonSpaceMode_returnsRaw() {
        // 非多 space 模式，SystemBot 也直接读原生
        WKUIConversationMsg uc = makeUc("u_10000", null);
        uc.lastMsgTimestamp = 4000L;
        assertEquals(4000L, ConversationPreviewSelector.selectDisplayTimestamp(uc, ""));
    }

    @Test
    public void timestamp_systemBot_hasWkMsg_usesDisplayMsgTimestamp() {
        // 多 space + SystemBot: 用 selectDisplayMessage.timestamp
        WKMsg raw = makeMsg(null, 5000L);  // 无 space_id → 非 BotFather 用 raw
        WKUIConversationMsg uc = makeUc("notification", raw);
        uc.lastMsgTimestamp = 0L;  // SDK 元数据缺失 → 走 selector 兜底
        assertEquals(5000L, ConversationPreviewSelector.selectDisplayTimestamp(uc, SPACE_A));
    }

    @Test
    public void timestamp_null_returnsZero() {
        assertEquals(0L, ConversationPreviewSelector.selectDisplayTimestamp(null, SPACE_A));
    }

    // ------------------------------------------------------------------
    // null / defensive
    // ------------------------------------------------------------------

    @Test
    public void selectDisplayMessage_nullInput_returnsNull() {
        assertNull(ConversationPreviewSelector.selectDisplayMessage(null, SPACE_A));
        assertNull(ConversationPreviewSelector.selectDisplayMessage(null, null));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WKUIConversationMsg makeUc(String channelID, WKMsg wkMsg) {
        WKUIConversationMsg uc = new WKUIConversationMsg();
        uc.channelID = channelID;
        uc.channelType = WKChannelType.PERSONAL;
        uc.clientMsgNo = wkMsg != null ? wkMsg.clientMsgNO : "";
        uc.setWkMsg(wkMsg);
        return uc;
    }

    private static WKMsg makeMsg(String spaceId, long timestamp) {
        WKMsg m = new WKMsg();
        m.clientMsgNO = "cli-" + timestamp;
        m.timestamp = timestamp;
        m.remoteExtra = new WKMsgExtra();
        if (spaceId != null) {
            m.content = "{\"type\":1,\"content\":\"hi\",\"space_id\":\"" + spaceId + "\"}";
        } else {
            m.content = "{\"type\":1,\"content\":\"hi\"}";
        }
        return m;
    }
}
