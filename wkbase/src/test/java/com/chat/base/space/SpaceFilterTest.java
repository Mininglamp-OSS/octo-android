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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelType;

import org.junit.Test;

/**
 * EP3 ·  — host-side 单元测试覆盖 {@link SpaceFilter} 字段透传与分支判定。
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
        String convSyncMySourceSpaceId; // GH #251：conv sync 预填的 my_source_space_id
        boolean authoritative; // space_memberships 缓存是否权威
        int getChannelSpaceIdCalls;
        int getMyMembershipSourceSpaceIdCalls;
        int isMyMembershipCachedCalls;
        int getConvSyncMySourceSpaceIdCalls;

        StubProvider(String groupSpaceId, String mySourceSpaceId, boolean mineCached) {
            this.groupSpaceId = groupSpaceId;
            this.mySourceSpaceId = mySourceSpaceId;
            this.mineCached = mineCached;
        }

        /** 兼容旧测试：默认 mineCached=true（保持既有测试语义）。 */
        StubProvider(String groupSpaceId, String mySourceSpaceId) {
            this(groupSpaceId, mySourceSpaceId, true);
        }

        StubProvider withConvSyncMySource(String value) {
            this.convSyncMySourceSpaceId = value;
            return this;
        }

        StubProvider withAuthoritative(boolean value) {
            this.authoritative = value;
            return this;
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

        @Override
        public String getConvSyncMySourceSpaceId(String channelID, byte channelType) {
            getConvSyncMySourceSpaceIdCalls++;
            return convSyncMySourceSpaceId;
        }

        @Override
        public boolean isSpaceCacheAuthoritative() {
            return authoritative;
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
    //  — 新消息到达路径：外部群 + 非当前 Space 必须被跳过
    //
    // 场景：用户停留在 Space A，来自 Space B 的外部群 G 收到新消息 → 在 A 的
    // 会话列表 onNewMessage / recent-update 路径必须经由 SpaceFilter 判定为 skip，
    // 否则 G 会错挂到 A 的列表（污染当前视图）。
    //
    // 该测试锁定 SpaceFilter 对「新消息 + 外部群 + 非当前 Space」场景的决策合约；
    // ChatFragment 侧已移除 spaceConversationKeys.isEmpty() 短路，保证
    // 白名单空态（首次加载 / 切 Space 瞬态）下依然会调用本函数。
    // ------------------------------------------------------------------

    @Test
    public void yuj208_newMessage_externalGroupFromOtherSpace_skips_viaRemoteExtra() {
        // 场景 A：channel.remoteExtraMap.space_id=B 可读 → cached-mismatch → skip
        // 我在 Space A，非该群外部成员（未以 A 身份加入），my row 已缓存
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/SPACE_B,
                /*mySourceSpaceId=*/null,
                /*mineCached=*/true);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group_any", GROUP, SPACE_A, p));
    }

    @Test
    public void yuj208_newMessage_externalGroupFromOtherSpace_skips_viaPrefixFallback() {
        // 场景 B：channelInfo 还没同步（groupSpaceId=null），但 channelID 带 sB_ 前缀，
        // 回退到前缀识别归属 Space B → cached-mismatch → skip
        String cid = "s" + SPACE_B + "_external_group";
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/null,
                /*mySourceSpaceId=*/null,
                /*mineCached=*/true);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_A, p));
    }

    @Test
    public void yuj208_newMessage_externalGroupWhereIAmExternalMember_doesNotSkip() {
        // 对照：我在 Space A 以外部成员身份加入 Space B 的群 G（source_space_id=A）
        // → 此时 G 的新消息应当出现在 A 的列表（这是外部群放行约定）
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/SPACE_B,
                /*mySourceSpaceId=*/SPACE_A,
                /*mineCached=*/true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group_any", GROUP, SPACE_A, p));
    }

    @Test
    public void yuj208_newMessage_memberExtraNotLoaded_failOpen() {
        // 约束「🟡 若 SpaceFilter 需要 source_space_id 但 WKSDK member extra 未加载，
        // 按 fail-open 原则降级」—— my row 未缓存时不得直接判 skip，避免启动/切 Space
        // 竞态窗口错杀外部群（由 eventual consistency 在下一次校准修正）。
        StubProvider p = new StubProvider(
                /*groupSpaceId=*/SPACE_B,
                /*mySourceSpaceId=*/null,
                /*mineCached=*/false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group_any", GROUP, SPACE_A, p));
    }

    // ------------------------------------------------------------------
    // GH dmwork-android#251 / octo-server PR #154 — conv sync my_source_space_id 短路
    //
    // 服务端在 conversation sync 响应里 resolved 的 my_source_space_id 由 SDK 写入
    // channel.remoteExtraMap，SpaceFilter 优先用它做判定，从而消除两个 fail-open 窗口：
    //   (a) my-row-not-cached-fail-open（subscriber row 还在路上）
    //   (b) 末尾 fail-open（group space_id 也缺失）
    // 老后端没回这个字段时（StubProvider.convSyncMySourceSpaceId==null）退化到原行为。
    // ------------------------------------------------------------------

    @Test
    public void gh251_convSyncExternalMember_overridesMyRowFailOpen_doesNotSkip() {
        // 群归属 Space B；my-row 没 sync（mineCached=false）；
        // conv sync 已带 my_source_space_id=A → 直接判定为外部成员 → 不跳过，
        // 不需要等 member sync。
        StubProvider p = new StubProvider(SPACE_B, /*mySource=*/null, /*mineCached=*/false)
                .withConvSyncMySource(SPACE_A);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        // 没必要再查 member sync 状态
        assertEquals(0, p.isMyMembershipCachedCalls);
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
        assertEquals(1, p.getConvSyncMySourceSpaceIdCalls);
    }

    @Test
    public void gh251_convSyncMismatch_overridesMyRowFailOpen_skips() {
        // 群归属 Space B；my-row 没 sync；conv sync 带 my_source_space_id=C（既不是当前 Space
        // 也不是群 Space）→ 直接判定 cached-mismatch → 跳过，不再 fail-open 泄漏跨 Space 群。
        String otherSource = "cccccccccccccccccccccccccccccccc";
        StubProvider p = new StubProvider(SPACE_B, /*mySource=*/null, /*mineCached=*/false)
                .withConvSyncMySource(otherSource);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(0, p.isMyMembershipCachedCalls);
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
    }

    @Test
    public void gh251_convSyncMissing_fallsBackToMyRowFailOpen() {
        // conv sync 没回 my_source_space_id（老后端 / 该群没有外部成员关系）→ 原 fail-open
        // 行为保持：my-row 没 sync 时不跳过，等下一次校准。
        StubProvider p = new StubProvider(SPACE_B, /*mySource=*/null, /*mineCached=*/false);
        // convSyncMySourceSpaceId 默认 null
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(1, p.isMyMembershipCachedCalls);
    }

    @Test
    public void gh251_convSyncExternalMember_alsoOverridesEvenWhenMyRowCached() {
        // 当 conv sync 已经给了权威值时直接用它，不走 member subscriber 查询路径，
        // 避免出现 conv sync vs member sync 数据不一致时的不可预期行为。
        StubProvider p = new StubProvider(SPACE_B, /*mySource=*/null, /*mineCached=*/true)
                .withConvSyncMySource(SPACE_A);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
    }

    @Test
    public void gh251_finalFailOpen_convSyncMySourceOverridesNoGroupSpace_doesNotSkip() {
        // 群 space_id 完全缺失（groupSpaceId=null，channel_id 无前缀） → 原本走最末尾 fail-open；
        // 但 conv sync 已带 my_source_space_id=A → 即便不知道群归属，也能判断我是外部成员 → 不跳过。
        StubProvider p = new StubProvider(/*groupSpaceId=*/null, /*mySource=*/null, /*mineCached=*/false)
                .withConvSyncMySource(SPACE_A);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void gh251_finalFailOpen_convSyncMismatchOverridesNoGroupSpace_skips() {
        // 群 space_id 完全缺失，但 conv sync 给的 my_source_space_id 也不是当前 Space →
        // 改判 skip，堵住末尾 fail-open 的跨 Space 泄漏窗口。
        String otherSource = "cccccccccccccccccccccccccccccccc";
        StubProvider p = new StubProvider(/*groupSpaceId=*/null, /*mySource=*/null, /*mineCached=*/false)
                .withConvSyncMySource(otherSource);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void gh251_finalFailOpen_noConvSyncData_keepsLegacyFailOpen() {
        // 完全没有任何 Space 信息（老后端 + 该群既没有 group space_id 也没有 my_source_space_id）
        // → 维持原有 fail-open，等 channelInfo 异步回来二次校准。
        StubProvider p = new StubProvider(/*groupSpaceId=*/null, /*mySource=*/null, /*mineCached=*/false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void gh251_constant_matchesBackendContract() {
        // 锁定 key 字面量与后端 sync payload 约定一致，防止跨端不一致回归
        assertEquals("space_id", SpaceFilter.CHANNEL_EXTRA_SPACE_ID);
        assertEquals("my_source_space_id", SpaceFilter.CHANNEL_EXTRA_MY_SOURCE_SPACE_ID);
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

    // ------------------------------------------------------------------
    // space_memberships 权威缓存 — 消除 fail-open 泄漏
    //
    // 当 space_memberships 缓存为权威时（applySpaceMemberships 已执行），
    // SpaceFilter 应当基于 space_id 和 my_source_space_id 做确定性判定，
    // 不再走 fail-open 路径。
    // ------------------------------------------------------------------

    @Test
    public void authoritative_groupInOtherSpace_noMySource_skips() {
        // 权威缓存：群属 Space B，convSync 和 member DB 均无外部成员关系 → skip
        StubProvider p = new StubProvider(SPACE_B, null, /*mineCached=*/false)
                .withAuthoritative(true);
        assertTrue(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(1, p.isMyMembershipCachedCalls);
        assertEquals(0, p.getMyMembershipSourceSpaceIdCalls);
    }

    @Test
    public void authoritative_groupInSameSpace_doesNotSkip() {
        // 权威缓存：群属当前 Space → cached-match → 不跳过
        StubProvider p = new StubProvider(SPACE_A, null, /*mineCached=*/false)
                .withAuthoritative(true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void authoritative_externalMember_doesNotSkip() {
        // 权威缓存：群属 Space B，my_source_space_id=A → 外部成员 → 不跳过
        StubProvider p = new StubProvider(SPACE_B, null, /*mineCached=*/false)
                .withConvSyncMySource(SPACE_A)
                .withAuthoritative(true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void authoritative_externalMemberInMemberDbOnly_doesNotSkip() {
        // 权威缓存：群属 Space B，convSync 无 my_source_space_id，但 member DB 有 source=A → 不跳过
        StubProvider p = new StubProvider(SPACE_B, /*mySourceSpaceId=*/SPACE_A, /*mineCached=*/true)
                .withAuthoritative(true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void authoritative_noGroupSpaceId_notInMemberships_failOpen() {
        // 权威缓存但没有 groupSpaceId（新群还没 sync）→ fail-open，等 channelInfo/sync 到了自动修正
        StubProvider p = new StubProvider(null, null, /*mineCached=*/false)
                .withAuthoritative(true);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
    }

    @Test
    public void nonAuthoritative_fallsBackToFailOpen() {
        // 非权威缓存（老后端 / 首次 sync 前）→ 保留 fail-open 行为
        StubProvider p = new StubProvider(SPACE_B, null, /*mineCached=*/false)
                .withAuthoritative(false);
        assertFalse(SpaceFilter.shouldSkipChannelForSpace("group001", GROUP, SPACE_A, p));
        assertEquals(1, p.isMyMembershipCachedCalls);
    }
}
