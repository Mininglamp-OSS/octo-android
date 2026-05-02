package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * YUJ-217 / YUJ-219 · {@link SystemBotsFallback} host-side 单元测试。
 *
 * <p>覆盖行为合约：
 * <ul>
 *     <li>FALLBACK 包含 botfather / u_10000 / fileHelper（appconfig 未返回时）</li>
 *     <li>缺失时合成占位，已存在时不重复合成</li>
 *     <li>合成的占位 channelType = PERSONAL，lastMsgTimestamp = 0（排序沉底）</li>
 *     <li>key 解析容错（channelID_channelType / channelID 仅）</li>
 *     <li>appconfig 注入后 getSystemBotIds 反映最新白名单</li>
 * </ul>
 */
public class SystemBotsFallbackTest {

    private static final byte GROUP = WKChannelType.GROUP;
    private static final byte PERSONAL = WKChannelType.PERSONAL;

    @Before
    public void setUp() {
        // 单测默认跑 fallback 分支（appconfig 不可用）
        SystemBotsFallback.overrideSystemBotIdsForTest(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS);
    }

    @After
    public void tearDown() {
        SystemBotsFallback.overrideSystemBotIdsForTest(null);
    }

    @Test
    public void fallback_containsBotfatherU10000AndFileHelper() {
        Set<String> fallback = SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS;
        assertTrue(fallback.contains("botfather"));
        assertTrue(fallback.contains("u_10000"));
        assertTrue(fallback.contains("fileHelper"));
        assertEquals(3, fallback.size());
    }

    @Test
    public void getSystemBotIds_defaultsToFallback() {
        assertEquals(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS, SystemBotsFallback.getSystemBotIds());
    }

    @Test
    public void getSystemBotIds_reflectsOverriddenWhitelist() {
        Set<String> remote = new LinkedHashSet<>(Arrays.asList("botfather", "customBot"));
        SystemBotsFallback.overrideSystemBotIdsForTest(remote);
        assertEquals(remote, SystemBotsFallback.getSystemBotIds());
        assertTrue(SystemBotsFallback.isSystemBot("customBot"));
        // u_10000 不在 remote 中 → 不再视为系统 Bot
        assertFalse(SystemBotsFallback.isSystemBot("u_10000"));
    }

    @Test
    public void findMissingBotIds_emptyExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(Collections.emptyList());
        assertEquals(SystemBotsFallback.getSystemBotIds(), missing);
    }

    @Test
    public void findMissingBotIds_nullExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(null);
        assertEquals(SystemBotsFallback.getSystemBotIds(), missing);
    }

    @Test
    public void findMissingBotIds_allBotsPresentAsChannelKeys_returnsEmpty() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "botfather_" + PERSONAL,
                "u_10000_" + PERSONAL,
                "fileHelper_" + PERSONAL,
                "friend_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.isEmpty());
    }

    @Test
    public void findMissingBotIds_partialPresent_returnsRemaining() {
        Set<String> keys = new HashSet<>(Arrays.asList("botfather_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertFalse(missing.contains("botfather"));
        assertTrue(missing.contains("u_10000"));
        assertTrue(missing.contains("fileHelper"));
    }

    @Test
    public void findMissingBotIds_botPresentAsBareId_returnsEmptyForThatBot() {
        // 容错：调用方传已解析的 channelID 集合（单 token，无 `_` 分隔符的情况）
        Set<String> keys = new HashSet<>(Arrays.asList("botfather"));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertFalse(missing.contains("botfather"));
    }

    @Test
    public void findMissingBotIds_unrelatedChannelsOnly_returnsAllBots() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "group_a_" + GROUP,
                "group_b_" + GROUP,
                "friend_uid_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.contains("botfather"));
        assertTrue(missing.contains("u_10000"));
        assertTrue(missing.contains("fileHelper"));
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
        assertEquals(SystemBotsFallback.getSystemBotIds().size(), synthesized.size());
        Set<String> synthesizedIds = new HashSet<>();
        for (WKUIConversationMsg m : synthesized) {
            synthesizedIds.add(m.channelID);
            assertEquals(PERSONAL, m.channelType);
        }
        assertEquals(SystemBotsFallback.getSystemBotIds(), synthesizedIds);
    }

    @Test
    public void synthesizeMissing_allBotsAlreadyPresent_returnsEmpty() {
        List<WKUIConversationMsg> synthesized = SystemBotsFallback.synthesizeMissing(Arrays.asList(
                "botfather_" + PERSONAL,
                "u_10000_" + PERSONAL,
                "fileHelper_" + PERSONAL));
        assertTrue(synthesized.isEmpty());
    }

    @Test
    public void isSystemBot_matchesWhitelistOnly() {
        assertTrue(SystemBotsFallback.isSystemBot("botfather"));
        assertTrue(SystemBotsFallback.isSystemBot("u_10000"));
        assertTrue(SystemBotsFallback.isSystemBot("fileHelper"));
        assertFalse(SystemBotsFallback.isSystemBot("friend_uid"));
        assertFalse(SystemBotsFallback.isSystemBot(null));
        assertFalse(SystemBotsFallback.isSystemBot(""));
    }
}
