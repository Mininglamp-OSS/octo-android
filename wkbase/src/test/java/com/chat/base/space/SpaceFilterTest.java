package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;

import org.junit.Test;

/**
 * EP3 · YUJ-88 — host-side 单元测试覆盖 {@link SpaceFilter} 字段透传与分支判定。
 *
 * <p>测试矩阵覆盖验收清单 + iOS EP3 跨端一致性 P2 修复：
 * {@code space_prefix / cached-match / cached-external-member / cached-mismatch /
 *  info-match / fail-open / prefix-mismatch-fallthrough / members-not-loaded-race}
 *
 * <p>所有测试通过 {@link StubProvider} 注入，纯 JVM 不依赖 Android 运行时。
 */
public class SpaceFilterTest {

    private static final String SPACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 32 hex
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final byte GROUP = WKChannelType.GROUP;
    private static final byte PERSONAL = WKChannelType.PERSONAL;

    /** 可控的 ChannelInfoProvider 存根，记录调用次数以验证短路行为。 */
    private static final class StubProvider implements SpaceFilter.ChannelInfoProvider {
        final String groupSpaceId;
        final String mySourceSpaceId;
        boolean mineCached;
        int getChannelSpaceIdCalls;
        int getMyMembershipSourceSpaceIdCalls;
        int isMyMembershipCachedCalls;

        StubProvider(String groupSpaceId, String mySourceSpaceId, boolean mineCached) {
            this.groupSpaceId = groupSpaceId;
            this.mySourceSpaceId = mySourceSpaceId;
            this.mineCached = mineCached;
        }

        /** 兼容旧测试：默认 mineCached=true（保持既有测试语义）。 */
        StubProvider(String groupSpaceId, String mySourceSpaceId) {
            this(groupSpaceId, mySourceSpaceId, true);
        }

        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            getChannelSpaceIdCalls++;
            return groupSpaceId;
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            getMyMembershipSourceSpaceIdCalls++;
            return mySourceSpaceId;
        }

        @Override
        public boolean isMyMembershipCached(String channelID, byte channelType) {
            isMyMembershipCachedCalls++;
            return mineCached;
        }
    }

    // ------------------------------------------------------------------
    // shouldSkipChannelForSpace — 基础 8 分支
    // ------------------------------------------------------------------

    @Test
    public void spaceEmptyPass_nonSpaceMode_neverSkips() {
        StubProvider p = new StubProvider(SPACE_B, null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, "", p));
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, null, p));
        // 不应进入 provider（短路）
        assertEquals(0, p.getChannelSpaceIdCalls);
    }

    @Test
    public void spacePrefix_match_doesNotSkip() {
        StubProvider p = new StubProvider(null, null);
        String cid = "s" + SPACE_A + "_group001";
        assertFalse(SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_A, p));
        // prefix match 快速路径不应查 channelInfo
        assertEquals(0, p.getChannelSpaceIdCalls);
    }

    @Test
    public void personPass_privateChat_doesNotSkipAtChannelLevel() {
        StubProvider p = new StubProvider(SPACE_B, null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("userX", PERSONAL, SPACE_A, p));
        assertEquals(0, p.getChannelSpaceIdCalls);
    }

    @Test
    public void cachedMatch_groupSpaceEqualsCurrent_doesNotSkip() {
        StubProvider p = new StubProvider(SPACE_A, null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        // cached-match 命中后不应查 membership / mineCached
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
        assertEquals(0, p.isMyMembershipCachedCalls);
    }

    @Test
    public void cachedExternalMember_mySourceSpaceEqualsCurrent_doesNotSkip() {
        // 我在 Space A 以外部成员身份加入 Space B 的群 G（members 已加载）
        StubProvider p = new StubProvider(SPACE_B, SPACE_A, /*mineCached=*/true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(1, p.getMyMembershipSourceSpaceIdCalls);
        assertEquals(1, p.isMyMembershipCachedCalls);
    }

    @Test
    public void cachedMismatch_groupInOtherSpaceAndNotExternalMember_skips() {
        // 群归属 Space B；members 已加载；我非外部成员
        StubProvider p = new StubProvider(SPACE_B, null, /*mineCached=*/true);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));

        StubProvider p2 = new StubProvider(SPACE_B, SPACE_B, /*mineCached=*/true); // source 也不等 current
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p2));
    }

    @Test
    public void failOpen_noChannelInfoAndNoPrefix_doesNotSkip() {
        // groupSpaceId null 且无前缀 → fail-open
        StubProvider pNull = new StubProvider(null, null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, pNull));

        StubProvider pEmpty = new StubProvider("", null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, pEmpty));
    }

    // ------------------------------------------------------------------
    // P2 #1 修复 — 前缀不匹配 fall-through（跨端一致性：对齐 iOS EP3）
    // ------------------------------------------------------------------

    @Test
    public void p2_spacePrefixMismatch_externalMember_doesNotSkip() {
        // 核心场景：群 G channel_id 带 Space B 前缀（群归属 B），
        // 我在 Space A 以外部成员身份加入（source_space_id=A），
        // currentSpaceId=A 时前缀不匹配 → 应 fall-through 到 external-member 兜底 → 不跳过
        String cid = "s" + SPACE_B + "_group001";
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/null, // channelInfo.orgData.space_id 未就绪也 OK，会回退用 prefix
                /*mySourceSpaceId=*/SPACE_A,
                /*mineCached=*/true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_A, p));
        // 必须进入到 membership 查询，证明没被前缀分支短路错杀
        assertEquals(1, p.getMyMembershipSourceSpaceIdCalls);
    }

    @Test
    public void p2_spacePrefixMismatch_notExternalMember_stillSkips() {
        // 对照：前缀不匹配 + 我不是外部成员 + members 已加载 → 仍应 Skip（维持正确隔离）
        String cid = "s" + SPACE_B + "_group001";
        StubProvider p = new StubProvider(null, null, /*mineCached=*/true);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_A, p));
    }

    @Test
    public void p2_spacePrefixMismatch_personChannel_doesNotSkip() {
        // 前缀不匹配但是私聊（理论上不该出现，但保护 person-pass 逻辑不被意外绕过）
        String cid = "s" + SPACE_B + "_userX";
        StubProvider p = new StubProvider(null, null);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace(cid, PERSONAL, SPACE_A, p));
    }

    // ------------------------------------------------------------------
    // P2 #2 修复 — my own subscriber row 未缓存 fail-open（对齐 iOS EP3 + codex 二次审查）
    //
    // codex 二次审查指出：不能用「members 列表非空」作为加载信号——发送者的 row 可能先
    // sync 下来，我的外部成员 row 还在路上，此时「非空」会误判 cached-mismatch 错杀外部群。
    // 正确信号只能是 my own row 是否在本地 DB：my row 在 → 可信；my row 不在 → race → fail-open。
    // ------------------------------------------------------------------

    @Test
    public void p2_groupInOtherSpace_myRowNotCached_failOpen() {
        // 核心场景：群归属 Space B 但我自己的 member row 还没 sync 下来 →
        // 不能直接判定 Skip（会错杀切 Space 瞬间的外部群），应 fail-open 等二次校准
        StubProvider p = new StubProvider(SPACE_B, null, /*mineCached=*/false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        // my row 未缓存时不应查 source_space_id（走 fail-open 就够了）
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
    }

    @Test
    public void p2_prefixMismatch_myRowNotCached_failOpen() {
        // 前缀不匹配 + 我的 row 未缓存 → 仍应 fail-open（避免启动竞态错杀）
        String cid = "s" + SPACE_B + "_group001";
        StubProvider p = new StubProvider(null, null, /*mineCached=*/false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_A, p));
    }

    @Test
    public void p2_partialMembersSynced_myRowStillMissing_failOpen() {
        // codex 捕获的 edge case：本地 member 表里已有发送者的 row（非空），
        // 但我自己的外部成员 row 还在 sync 路上。此时 mineCached=false 必须触发 fail-open，
        // 不能因为「members 列表非空」就判定为 cached-mismatch。
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/SPACE_B,
                /*mySourceSpaceId=*/null, // 我的 row 没 sync，当然拿不到 source_space_id
                /*mineCached=*/false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
    }

    // ------------------------------------------------------------------
    // hasSpacePrefix / extractSpacePrefix
    // ------------------------------------------------------------------

    @Test
    public void hasSpacePrefix_matchesWebRegex() {
        assertTrue(SpaceFilter.hasSpacePrefix("s" + SPACE_A + "_group001"));
        assertFalse(SpaceFilter.hasSpacePrefix("group001"));
        assertFalse(SpaceFilter.hasSpacePrefix("s_group001"));              // 空 spaceId
        assertFalse(SpaceFilter.hasSpacePrefix("sZZZ" + "_group001"));      // 非 hex
        assertFalse(SpaceFilter.hasSpacePrefix("sAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA_g")); // 大写 hex 不匹配（对齐 web 小写）
        assertFalse(SpaceFilter.hasSpacePrefix(null));
    }

    @Test
    public void extractSpacePrefix_returnsSpaceIdOrNull() {
        assertEquals(SPACE_A, SpaceFilter.extractSpacePrefix("s" + SPACE_A + "_group001"));
        assertNull(SpaceFilter.extractSpacePrefix("group001"));
        assertNull(SpaceFilter.extractSpacePrefix(null));
    }

    // ------------------------------------------------------------------
    // shouldSkipMessageForSpace — 私聊消息级
    // ------------------------------------------------------------------

    @Test
    public void shouldSkipMessageForSpace_nullMsg_doesNotSkip() {
        assertFalse(SpaceFilter.shouldSkipMessageForSpace(null, SPACE_A));
        assertFalse(SpaceFilter.shouldSkipMessageForSpace(null, ""));
        assertFalse(SpaceFilter.shouldSkipMessageForSpace(null, null));
    }
}
