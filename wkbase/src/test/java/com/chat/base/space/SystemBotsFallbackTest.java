package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * YUJ-217 · {@link SystemBotsFallback} host-side 单元测试。
 *
 * <p>覆盖 Fix C 本地合成的关键行为合约：
 * <ul>
 *     <li>SYSTEM_BOT_IDS 包含 botfather</li>
 *     <li>缺失时合成占位，已存在时不重复合成</li>
 *     <li>合成的占位 channelType = PERSONAL，lastMsgTimestamp = 0（排序沉底）</li>
 *     <li>key 解析容错（channelID_channelType / channelID 仅）</li>
 * </ul>
 */
public class SystemBotsFallbackTest {

    private static final byte GROUP = WKChannelType.GROUP;
    private static final byte PERSONAL = WKChannelType.PERSONAL;

    @Test
    public void systemBotIds_containsBotfather() {
        assertTrue(SystemBotsFallback.SYSTEM_BOT_IDS.contains("botfather"));
    }

    @Test
    public void findMissingBotIds_emptyExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(Collections.emptyList());
        assertEquals(SystemBotsFallback.SYSTEM_BOT_IDS, missing);
    }

    @Test
    public void findMissingBotIds_nullExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(null);
        assertEquals(SystemBotsFallback.SYSTEM_BOT_IDS, missing);
    }

    @Test
    public void findMissingBotIds_botPresentAsChannelKey_returnsEmpty() {
        Set<String> keys = new HashSet<>(Arrays.asList("botfather_" + PERSONAL, "friend_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.isEmpty());
    }

    @Test
    public void findMissingBotIds_botPresentAsBareId_returnsEmpty() {
        // 容错：调用方传的是已存在的 channelID 集合（不带 _type 后缀）
        Set<String> keys = new HashSet<>(Arrays.asList("botfather"));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.isEmpty());
    }

    @Test
    public void findMissingBotIds_unrelatedChannelsOnly_returnsBot() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "group_a_" + GROUP,
                "group_b_" + GROUP,
                "friend_uid_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.contains("botfather"));
    }

    @Test
    public void buildPlaceholder_setsExpectedFields() {
        WKUIConversationMsg msg = SystemBotsFallback.buildPlaceholder("botfather");
        assertNotNull(msg);
        assertEquals("botfather", msg.channelID);
        assertEquals(PERSONAL, msg.channelType);
        assertEquals(0L, msg.lastMsgTimestamp);
        assertEquals(0, msg.unreadCount);
        assertEquals(0, msg.isDeleted);
    }

    @Test
    public void synthesizeMissing_emptyExisting_returnsAllBotsAsPlaceholders() {
        List<WKUIConversationMsg> synthesized =
                SystemBotsFallback.synthesizeMissing(Collections.emptyList());
        assertEquals(SystemBotsFallback.SYSTEM_BOT_IDS.size(), synthesized.size());
        Set<String> synthesizedIds = new HashSet<>();
        for (WKUIConversationMsg m : synthesized) {
            synthesizedIds.add(m.channelID);
            assertEquals(PERSONAL, m.channelType);
        }
        assertEquals(SystemBotsFallback.SYSTEM_BOT_IDS, synthesizedIds);
    }

    @Test
    public void synthesizeMissing_botAlreadyPresent_returnsEmpty() {
        List<WKUIConversationMsg> synthesized = SystemBotsFallback.synthesizeMissing(
                Collections.singletonList("botfather_" + PERSONAL));
        assertTrue(synthesized.isEmpty());
    }

    @Test
    public void isSystemBot_matchesWhitelistOnly() {
        assertTrue(SystemBotsFallback.isSystemBot("botfather"));
        assertFalse(SystemBotsFallback.isSystemBot("friend_uid"));
        assertFalse(SystemBotsFallback.isSystemBot(null));
        assertFalse(SystemBotsFallback.isSystemBot(""));
    }
}
