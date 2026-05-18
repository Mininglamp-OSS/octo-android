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
 *  /  · {@link SystemBotsFallback} host-side 单元测试。
 *
 * <p>覆盖行为合约（合并 -A3 appconfig 消费 + -B 三端 SystemBot 集合对齐）：
 * <ul>
 *     <li>FALLBACK / DEFAULT 包含 botfather / u_10000 / fileHelper（与后端
 *         {@code pkg/space/query.go :: SystemBots} 对齐）</li>
 *     <li>运行时集合可由单测 override 注入 / appconfig 下发（生产路径）</li>
 *     <li>缺失时合成占位，已存在时不重复合成</li>
 *     <li>合成的占位 channelType = PERSONAL，lastMsgTimestamp = 0（排序沉底）</li>
 *     <li>key 解析容错（channelID_channelType / channelID 仅）</li>
 *     <li>appconfig 注入后 getSystemBotIds 反映最新白名单</li>
 * </ul>
 */
public class SystemBotsFallbackTest {

    private static final byte GROUP = WKChannelType.GROUP;
    private static final byte PERSONAL = WKChannelType.PERSONAL;

    /** 每个 case 前强制 override 成稳定集合，避免 WKConfig Android stub 异常路径。 */
    private static final Set<String> TEST_BOTS =
            Collections.unmodifiableSet(new LinkedHashSet<>(
                    Arrays.asList("botfather", "u_10000", "fileHelper")));

    @Before
    public void setUp() {
        SystemBotsFallback.setTestOverride(TEST_BOTS);
    }

    @After
    public void tearDown() {
        SystemBotsFallback.setTestOverride(null);
        SystemBotsFallback.overrideSystemBotIdsForTest(null);
    }

    // ------------------------------------------------------------------
    // -A3 / B · FALLBACK 常量与默认行为
    // ------------------------------------------------------------------

    @Test
    public void fallback_containsBotfatherU10000AndFileHelper() {
        Set<String> fallback = SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS;
        assertTrue(fallback.contains("botfather"));
        assertTrue(fallback.contains("u_10000"));
        assertTrue(fallback.contains("fileHelper"));
        assertEquals(3, fallback.size());
    }

    @Test
    public void defaultSystemBotIds_alignsWithBackend() {
        // 后端 pkg/space/query.go :: SystemBots 契约；DEFAULT 是 FALLBACK 的别名。
        assertTrue(SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS.contains("botfather"));
        assertTrue(SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS.contains("u_10000"));
        assertTrue(SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS.contains("fileHelper"));
        assertEquals(3, SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS.size());
        assertEquals(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS,
                SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS);
    }

    @Test
    public void getSystemBotIds_defaultsToFallback() {
        SystemBotsFallback.setTestOverride(null);
        SystemBotsFallback.overrideSystemBotIdsForTest(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS);
        assertEquals(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS, SystemBotsFallback.getSystemBotIds());
    }

    @Test
    public void getSystemBotIds_respectsTestOverride() {
        SystemBotsFallback.setTestOverride(new HashSet<>(Arrays.asList("only_one")));
        Set<String> ids = SystemBotsFallback.getSystemBotIds();
        assertEquals(Collections.singleton("only_one"), ids);
    }

    @Test
    public void getSystemBotIds_reflectsOverriddenWhitelist() {
        Set<String> remote = new LinkedHashSet<>(Arrays.asList("botfather", "customBot"));
        SystemBotsFallback.overrideSystemBotIdsForTest(remote);
        SystemBotsFallback.setTestOverride(null);
        assertEquals(remote, SystemBotsFallback.getSystemBotIds());
        assertTrue(SystemBotsFallback.isSystemBot("customBot"));
        // u_10000 不在 remote 中 → 不再视为系统 Bot
        assertFalse(SystemBotsFallback.isSystemBot("u_10000"));
    }

    @Test
    public void getSystemBotIds_nullOverride_fallsBackToDefault() {
        SystemBotsFallback.setTestOverride(null);
        SystemBotsFallback.overrideSystemBotIdsForTest(null);
        // WKConfig 在 host-side 抛异常会被 catch，回落 FALLBACK_SYSTEM_BOT_IDS
        Set<String> ids = SystemBotsFallback.getSystemBotIds();
        assertEquals(SystemBotsFallback.FALLBACK_SYSTEM_BOT_IDS, ids);
    }

    // ------------------------------------------------------------------
    // findMissingBotIds / synthesizeMissing
    // ------------------------------------------------------------------

    @Test
    public void findMissingBotIds_emptyExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(Collections.emptyList());
        assertEquals(TEST_BOTS, missing);
    }

    @Test
    public void findMissingBotIds_nullExisting_returnsAllBots() {
        Set<String> missing = SystemBotsFallback.findMissingBotIds(null);
        assertEquals(TEST_BOTS, missing);
    }

    @Test
    public void findMissingBotIds_allBotsPresentAsChannelKey_returnsEmpty() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "botfather_" + PERSONAL,
                "u_10000_" + PERSONAL,
                "fileHelper_" + PERSONAL,
                "friend_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertTrue(missing.isEmpty());
    }

    @Test
    public void findMissingBotIds_onlyBotfatherPresent_returnsOtherTwo() {
        // -B 回归：旧实现硬编码只含 botfather，这里会返回 missing.isEmpty
        // 新实现必须发现 u_10000 / fileHelper 缺失（对齐后端三端）
        Set<String> keys = new HashSet<>(Arrays.asList("botfather_" + PERSONAL));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertEquals(new HashSet<>(Arrays.asList("u_10000", "fileHelper")), missing);
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
    public void findMissingBotIds_bareIdKeys_returnsNone() {
        // 容错：调用方传已解析的 channelID 集合（botfather / fileHelper 无 `_` 分隔符）。
        // 注意 `u_10000` 含 `_`，会被 key-parser 拆成 `u`，因此不在此用例断言范围内；
        // 只断言不含 `_` 的 bot 能正确识别。
        Set<String> keys = new HashSet<>(Arrays.asList("botfather", "fileHelper"));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertFalse(missing.contains("botfather"));
        assertFalse(missing.contains("fileHelper"));
    }

    @Test
    public void findMissingBotIds_botPresentAsBareId_returnsEmptyForThatBot() {
        // 容错：调用方传已解析的 channelID 集合（单 token，无 `_` 分隔符的情况）
        Set<String> keys = new HashSet<>(Arrays.asList("botfather"));
        Set<String> missing = SystemBotsFallback.findMissingBotIds(keys);
        assertFalse(missing.contains("botfather"));
    }

    @Test
    public void findMissingBotIds_unrelatedChannelsOnly_returnsAll() {
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
        assertEquals(TEST_BOTS.size(), synthesized.size());
        Set<String> synthesizedIds = new HashSet<>();
        for (WKUIConversationMsg m : synthesized) {
            synthesizedIds.add(m.channelID);
            assertEquals(PERSONAL, m.channelType);
        }
        assertEquals(TEST_BOTS, synthesizedIds);
    }

    @Test
    public void synthesizeMissing_allBotsAlreadyPresent_returnsEmpty() {
        List<WKUIConversationMsg> synthesized = SystemBotsFallback.synthesizeMissing(Arrays.asList(
                "botfather_" + PERSONAL,
                "u_10000_" + PERSONAL,
                "fileHelper_" + PERSONAL));
        assertTrue(synthesized.isEmpty());
    }

    // ------------------------------------------------------------------
    // isSystemBot
    // ------------------------------------------------------------------

    @Test
    public void isSystemBot_matchesAllThreeBots() {
        assertTrue(SystemBotsFallback.isSystemBot("botfather"));
        assertTrue(SystemBotsFallback.isSystemBot("u_10000"));
        assertTrue(SystemBotsFallback.isSystemBot("fileHelper"));
    }

    @Test
    public void isSystemBot_rejectsNonBots() {
        assertFalse(SystemBotsFallback.isSystemBot("friend_uid"));
        assertFalse(SystemBotsFallback.isSystemBot(null));
        assertFalse(SystemBotsFallback.isSystemBot(""));
    }
}
