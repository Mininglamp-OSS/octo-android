package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * YUJ-217 · {@link SpaceConversationPruner} host-side 单元测试。
 *
 * <p>覆盖 Fix D 状态变化回扫的关键行为合约：
 * <ul>
 *     <li>非当前 Space 的 GROUP 会被 prune</li>
 *     <li>外部群（source_space_id == currentSpaceId）不会被 prune</li>
 *     <li>PERSONAL（含 botfather 等系统 Bot）绝不会被 prune</li>
 *     <li>currentSpaceId 为空（非 Space 模式）时不做任何 prune</li>
 *     <li>白名单清理 key 解析与格式容错</li>
 * </ul>
 *
 * <p>复用 {@link SpaceFilterTest.StubProvider} 的 provider 约定但本地重建，避免 package-private
 * 访问问题。
 */
public class SpaceConversationPrunerTest {

    private static final String SPACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final byte GROUP = WKChannelType.GROUP;
    private static final byte PERSONAL = WKChannelType.PERSONAL;

    /** 本测试用的 stub provider：可为不同 channelID 返回不同的 space 信息。 */
    private static final class MapProvider implements SpaceFilter.ChannelInfoProvider {
        final java.util.Map<String, String> groupSpaceIds = new java.util.HashMap<>();
        final java.util.Map<String, String> mySourceSpaceIds = new java.util.HashMap<>();
        final java.util.Set<String> myRowCached = new java.util.HashSet<>();

        MapProvider putGroup(String cid, String spaceId) {
            groupSpaceIds.put(cid, spaceId);
            return this;
        }

        MapProvider putMyMembership(String cid, String sourceSpaceId, boolean cached) {
            mySourceSpaceIds.put(cid, sourceSpaceId);
            if (cached) myRowCached.add(cid);
            return this;
        }

        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            return groupSpaceIds.get(channelID);
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            return mySourceSpaceIds.get(channelID);
        }

        @Override
        public boolean isMyMembershipCached(String channelID, byte channelType) {
            return myRowCached.contains(channelID);
        }
    }

    @Test
    public void shouldPrune_groupNotInCurrentSpaceWithMyRowCached_returnsTrue() {
        MapProvider p = new MapProvider()
                .putGroup("g_other", SPACE_B)
                .putMyMembership("g_other", null, true);
        assertTrue(SpaceConversationPruner.shouldPrune("g_other", GROUP, SPACE_A, p));
    }

    @Test
    public void shouldPrune_externalGroupSourceSpaceMatches_returnsFalse() {
        MapProvider p = new MapProvider()
                .putGroup("g_external", SPACE_B)
                .putMyMembership("g_external", SPACE_A, true);
        assertFalse(SpaceConversationPruner.shouldPrune("g_external", GROUP, SPACE_A, p));
    }

    @Test
    public void shouldPrune_groupInCurrentSpace_returnsFalse() {
        MapProvider p = new MapProvider().putGroup("g_mine", SPACE_A);
        assertFalse(SpaceConversationPruner.shouldPrune("g_mine", GROUP, SPACE_A, p));
    }

    @Test
    public void shouldPrune_personalAlwaysKept_evenBotfatherAcrossSpaces() {
        // PERSONAL 频道绝不做 channel-level prune，botfather 跨 Space 共享走消息级过滤
        MapProvider p = new MapProvider();
        assertFalse(SpaceConversationPruner.shouldPrune("botfather", PERSONAL, SPACE_A, p));
        assertFalse(SpaceConversationPruner.shouldPrune("friend_uid", PERSONAL, SPACE_A, p));
    }

    @Test
    public void shouldPrune_emptyCurrentSpace_neverPrunes() {
        MapProvider p = new MapProvider().putGroup("g_other", SPACE_B);
        assertFalse(SpaceConversationPruner.shouldPrune("g_other", GROUP, null, p));
        assertFalse(SpaceConversationPruner.shouldPrune("g_other", GROUP, "", p));
    }

    @Test
    public void shouldPrune_emptyChannelId_returnsFalse() {
        MapProvider p = new MapProvider();
        assertFalse(SpaceConversationPruner.shouldPrune(null, GROUP, SPACE_A, p));
        assertFalse(SpaceConversationPruner.shouldPrune("", GROUP, SPACE_A, p));
    }

    @Test
    public void shouldPrune_myRowNotCachedFailOpen_returnsFalse() {
        // 我的 row 尚未 sync 到本地 → fail-open（竞态防御），不 prune
        MapProvider p = new MapProvider()
                .putGroup("g_race", SPACE_B);
        // 不调 putMyMembership → myCached=false
        assertFalse(SpaceConversationPruner.shouldPrune("g_race", GROUP, SPACE_A, p));
    }

    @Test
    public void collectIndicesToPrune_returnsReverseOrder_forInPlaceRemoval() {
        MapProvider p = new MapProvider()
                .putGroup("g_a_keep", SPACE_A)
                .putGroup("g_b_drop", SPACE_B)
                .putMyMembership("g_b_drop", null, true)
                .putGroup("g_c_keep", SPACE_A)
                .putGroup("g_d_drop", SPACE_B)
                .putMyMembership("g_d_drop", null, true);

        List<WKUIConversationMsg> list = new ArrayList<>();
        list.add(conv("g_a_keep", GROUP));
        list.add(conv("g_b_drop", GROUP));
        list.add(conv("g_c_keep", GROUP));
        list.add(conv("g_d_drop", GROUP));

        List<Integer> indices = SpaceConversationPruner.collectIndicesToPrune(list, SPACE_A, p);
        assertEquals(Arrays.asList(3, 1), indices);
        // 原地逆序 remove：list 不被 mutate，collect 只读
        assertEquals(4, list.size());
    }

    @Test
    public void collectIndicesToPrune_emptyList_returnsEmpty() {
        MapProvider p = new MapProvider();
        assertTrue(SpaceConversationPruner.collectIndicesToPrune(null, SPACE_A, p).isEmpty());
        assertTrue(SpaceConversationPruner.collectIndicesToPrune(new ArrayList<>(), SPACE_A, p).isEmpty());
    }

    @Test
    public void collectIndicesToPrune_emptyCurrentSpace_returnsEmpty() {
        MapProvider p = new MapProvider().putGroup("g", SPACE_B);
        List<WKUIConversationMsg> list = new ArrayList<>();
        list.add(conv("g", GROUP));
        assertTrue(SpaceConversationPruner.collectIndicesToPrune(list, "", p).isEmpty());
    }

    @Test
    public void pruneWhitelist_removesOffSpaceGroup_keepsPersonal() {
        MapProvider p = new MapProvider()
                .putGroup("g_other", SPACE_B)
                .putMyMembership("g_other", null, true)
                .putGroup("g_mine", SPACE_A);
        Set<String> keys = new LinkedHashSet<>(Arrays.asList(
                "g_other_" + GROUP,
                "g_mine_" + GROUP,
                "botfather_" + PERSONAL,
                "friend_" + PERSONAL));
        int removed = SpaceConversationPruner.pruneWhitelist(keys, SPACE_A, p);
        assertEquals(1, removed);
        assertFalse(keys.contains("g_other_" + GROUP));
        assertTrue(keys.contains("g_mine_" + GROUP));
        assertTrue(keys.contains("botfather_" + PERSONAL));
        assertTrue(keys.contains("friend_" + PERSONAL));
    }

    @Test
    public void pruneWhitelist_emptyInputs_noop() {
        MapProvider p = new MapProvider();
        assertEquals(0, SpaceConversationPruner.pruneWhitelist(null, SPACE_A, p));
        assertEquals(0, SpaceConversationPruner.pruneWhitelist(new HashSet<>(), SPACE_A, p));
        Set<String> keys = new HashSet<>(Arrays.asList("g_" + GROUP));
        assertEquals(0, SpaceConversationPruner.pruneWhitelist(keys, "", p));
    }

    @Test
    public void parseKey_validFormat_returnsChannelIdAndType() {
        SpaceConversationPruner.ParsedKey k = SpaceConversationPruner.parseKey("channel123_" + GROUP);
        assertNotNull(k);
        assertEquals("channel123", k.channelID);
        assertEquals(GROUP, k.channelType);
    }

    @Test
    public void parseKey_channelIdWithUnderscore_takesLastUnderscoreAsSeparator() {
        // Space 前缀格式 s{hex}_... 的 channelID 本身带下划线，必须以最后一个 _ 作分隔
        SpaceConversationPruner.ParsedKey k = SpaceConversationPruner.parseKey(
                "s" + SPACE_A + "_foo_" + GROUP);
        assertNotNull(k);
        assertEquals("s" + SPACE_A + "_foo", k.channelID);
        assertEquals(GROUP, k.channelType);
    }

    @Test
    public void parseKey_malformed_returnsNull() {
        assertNull(SpaceConversationPruner.parseKey(null));
        assertNull(SpaceConversationPruner.parseKey(""));
        assertNull(SpaceConversationPruner.parseKey("no_separator_only"));
        assertNull(SpaceConversationPruner.parseKey("_5")); // 空 channelID
        assertNull(SpaceConversationPruner.parseKey("channel_")); // 空 channelType
        assertNull(SpaceConversationPruner.parseKey("channel_notanumber"));
    }

    private static WKUIConversationMsg conv(String channelID, byte channelType) {
        WKUIConversationMsg m = new WKUIConversationMsg();
        m.channelID = channelID;
        m.channelType = channelType;
        return m;
    }
}
