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

package com.chat.uikit.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.chat.base.external.ExternalViewerResolver;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.group.service.entity.GroupMember;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.junit.Test;

import java.util.HashMap;

/**
 *  — DM 骚扰治理 Phase 1：UserInfo 面板跨 Space 发送消息按钮隐藏。
 *
 * <p>对齐  #1021。覆盖任务要求的 3 个核心场景 + 防回归的边界用例。
 * 注意：{@link UserDetailExternalHelper} 是纯函数，依赖注入 viewer / member / fresh
 * 三元组，JVM 下可直裸跑 —— 不走 Robolectric，不 mock Android framework。
 */
public class UserDetailExternalHelperTest {

    private static final String VIEWER_UID = "viewer_uid";
    private static final String VIEWER_SPACE = "space_a";
    private static final String OTHER_SPACE = "space_b";
    private static final String GROUP_ID = "gid";

    // --- 任务指定的 3 个基础场景 ---

    /** 场景 1：同 Space 成员 → 发送消息按钮保持原有可见性（非外部 / 不隐藏）。 */
    @Test
    public void sameSpace_resolutionReportsNotExternal_buttonShown() {
        UserInfo info = userInfo("other_uid");
        info.group_member = groupMember(VIEWER_SPACE, "Team A", 0, null);

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, /* member */ null, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNotNull("同 Space 也返回非空 resolution，否则按钮隐藏逻辑无法判定", res);
        assertFalse(res.isExternal());
        assertFalse("同 Space 不应隐藏发送消息按钮",
                UserDetailExternalHelper.shouldHideSendMessageButton(res));
    }

    /** 场景 2：跨 Space 成员 → 隐藏发送消息按钮，并拿到 home_space_name 作为「来源」行文案。 */
    @Test
    public void crossSpace_resolutionReportsExternal_buttonHidden() {
        UserInfo info = userInfo("other_uid");
        info.group_member = groupMember(OTHER_SPACE, "Team B", 0, null);

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, /* member */ null, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNotNull(res);
        assertTrue(res.isExternal());
        assertTrue("跨 Space 必须隐藏发送消息按钮 ( 对齐 web PR#1021)",
                UserDetailExternalHelper.shouldHideSendMessageButton(res));
        // 「来源」行依然能拿到 Space 名，不会因为隐藏按钮而丢掉上下文
        org.junit.Assert.assertEquals("Team B", res.getSourceSpaceName());
    }

    /** 场景 3：自己看自己 → 返回 null，按钮走原有 follow 逻辑，不会因为判定逻辑错误自伤。 */
    @Test
    public void selfViewingSelf_resolutionIsNull_buttonUnaffected() {
        UserInfo info = userInfo(VIEWER_UID);
        info.group_member = groupMember(OTHER_SPACE, "Team B", 1, "Team B");

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, /* member */ null, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNull("自看必须早退，防止错误隐藏自己的 DM 入口", res);
        assertFalse(UserDetailExternalHelper.shouldHideSendMessageButton(res));
    }

    // --- 边界 / 防回归 ---

    /** 非群内入口（groupID 空）→ null：主动防御 1:1 联系人详情页不触发外部判定。 */
    @Test
    public void nonGroupEntry_returnsNull() {
        UserInfo info = userInfo("other_uid");
        info.group_member = groupMember(OTHER_SPACE, "Team B", 0, null);

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, /* member */ null, VIEWER_UID, VIEWER_SPACE, /* groupID */ "");

        assertNull(res);
        assertFalse(UserDetailExternalHelper.shouldHideSendMessageButton(res));
    }

    /**
     * 无任何数据源（fresh 无 group_member，cache 也空）→ null：
     * UI 走老 source_desc 逻辑，不应误隐藏发送消息按钮。
     */
    @Test
    public void missingAllSources_returnsNull() {
        UserInfo info = userInfo("other_uid"); // group_member = null

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, /* member */ null, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNull(res);
    }

    /**
     * 降级路径：fresh 无 home_space_id，但 WKIM 缓存有 is_external=1 + source_space_name →
     * 仍然能判定为外部，隐藏按钮。对应「老后端没上 」过渡期。
     */
    @Test
    public void legacyCacheOnly_stillMarksExternal_buttonHidden() {
        UserInfo info = userInfo("other_uid");
        info.group_member = null;

        WKChannelMember member = new WKChannelMember();
        member.extraMap = new HashMap<>();
        member.extraMap.put(WKChannelMemberExtras.isExternal, 1);
        member.extraMap.put(WKChannelMemberExtras.sourceSpaceName, "LegacySpace");

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, member, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNotNull(res);
        assertTrue(res.isExternal());
        assertTrue(UserDetailExternalHelper.shouldHideSendMessageButton(res));
    }

    /**
     * Codex P2 回归（继承 ）：fresh 带 home_space_id=viewer（「其实已同 Space」）
     * 必须覆盖陈旧 cache 的 is_external=1，避免老条目把同 Space 成员误标外部、
     * 从而误隐藏按钮。
     */
    @Test
    public void staleCache_freshHomeSpaceWins_buttonShown() {
        UserInfo info = userInfo("other_uid");
        info.group_member = groupMember(VIEWER_SPACE, "Team A", 1, "OldSpace");

        WKChannelMember staleMember = new WKChannelMember();
        staleMember.extraMap = new HashMap<>();
        staleMember.extraMap.put(WKChannelMemberExtras.isExternal, 1);
        staleMember.extraMap.put(WKChannelMemberExtras.sourceSpaceName, "OldSpace");

        ExternalViewerResolver.Resolution res = UserDetailExternalHelper.resolve(
                info, staleMember, VIEWER_UID, VIEWER_SPACE, GROUP_ID);

        assertNotNull(res);
        assertFalse("fresh home_space_id=viewer 必须压掉 cache 的 is_external=1",
                res.isExternal());
        assertFalse(UserDetailExternalHelper.shouldHideSendMessageButton(res));
    }

    // --- ：applyBtn 跨 Space 边界（对齐 web PR#1013/1091 · iOS ） ---

    /** 跨 Space 外部成员：无论 follow / vercode，都必须隐藏「申请加好友」按钮。 */
    @Test
    public void applyBtn_externalUser_alwaysHidden() {
        // 陌生 + 持 vercode 本是老逻辑下的「可申请」入口，外部成员也不放行
        assertFalse("外部 + follow=0 + vercode 非空 仍必须隐藏 ( Space 边界)",
                UserDetailExternalHelper.shouldShowApplyButton(
                        /* isExternalUser */ true, /* follow */ 0, /* hasVercode */ true));
        assertFalse("外部 + follow=0 + 无 vercode → 仍隐藏",
                UserDetailExternalHelper.shouldShowApplyButton(true, 0, false));
        assertFalse("外部 + follow=1 → 仍隐藏",
                UserDetailExternalHelper.shouldShowApplyButton(true, 1, false));
    }

    /** 同 Space 陌生用户（非外部、follow=0、持 vercode）：保留老路径，可申请加好友。 */
    @Test
    public void applyBtn_sameSpaceStrangerWithVercode_shown() {
        assertTrue("同 Space 陌生用户仍可申请加好友 (非回归)",
                UserDetailExternalHelper.shouldShowApplyButton(
                        /* isExternalUser */ false, /* follow */ 0, /* hasVercode */ true));
    }

    /** 同 Space 陌生用户但无 vercode（无申请入场券）：保持老逻辑隐藏。 */
    @Test
    public void applyBtn_sameSpaceStrangerNoVercode_hidden() {
        assertFalse("无 vercode 时老逻辑即隐藏 applyBtn，helper 必须保一致",
                UserDetailExternalHelper.shouldShowApplyButton(false, 0, false));
    }

    /** 已是好友：无论是否外部、是否持 vercode，都隐藏「申请加好友」按钮。 */
    @Test
    public void applyBtn_alreadyFollowed_hidden() {
        assertFalse(UserDetailExternalHelper.shouldShowApplyButton(false, 1, true));
        assertFalse(UserDetailExternalHelper.shouldShowApplyButton(false, 1, false));
    }

    // --- ：外部 viewer 下 UserInfo 底部 bottomPanel 全隐 + 「仅可在群内交流」hint + 姓名旁 @SpaceName。
    // 对齐 web PR#1021 `UserInfo/index.tsx:29-48` 与 `Subscribers/list.tsx:320`。 ---

    /** shouldHideBottomPanel：外部成员 viewer → 隐藏整个底部面板；同 Space → 保持可见。 */
    @Test
    public void shouldHideBottomPanel() {
        assertTrue("外部成员 viewer 必须隐藏 applyBtn / sendMsgBtn / deleteLayout / pushBlackLayout",
                UserDetailExternalHelper.shouldHideBottomPanel(true));
        assertFalse("同 Space / 非外部 → 保留老 bottomPanel 逻辑",
                UserDetailExternalHelper.shouldHideBottomPanel(false));
    }

    /** shouldShowExternalHint：外部成员 viewer → 显示「仅可在群内交流」；同 Space → 隐藏。 */
    @Test
    public void shouldShowExternalHint() {
        assertTrue("外部成员 viewer 必须显示 externalHintTv（对齐 web wk-userinfo-footer-external-hint）",
                UserDetailExternalHelper.shouldShowExternalHint(true));
        assertFalse("同 Space / 非外部 viewer 下 externalHintTv 必须隐藏，避免与 bottomPanel 同屏",
                UserDetailExternalHelper.shouldShowExternalHint(false));
    }

    /**
     * shouldShowSourceSpaceRow：resolveSourceSpaceLabel 的三源优先级覆盖 —
     * resolver > UserInfo.home_space_name > UserInfo.source_space_name。
     */
    @Test
    public void shouldShowSourceSpaceRow() {
        UserInfo info = userInfo("other_uid");
        info.home_space_name = "HomeSpace";
        info.source_space_name = "SourceSpace";
        info.is_external = 1;

        ExternalViewerResolver.Resolution resolverRes =
                new ExternalViewerResolver.Resolution(true, "ResolverSpace");

        // 非外部 → null，不渲染
        assertNull("非外部成员不应渲染 @SpaceName / 来源行",
                UserDetailExternalHelper.resolveSourceSpaceLabel(false, resolverRes, info));

        // resolver 拿到 space name → 优先用 resolver
        org.junit.Assert.assertEquals("ResolverSpace",
                UserDetailExternalHelper.resolveSourceSpaceLabel(true, resolverRes, info));

        // resolver=null（group_member 缺字段） → 兜底 UserInfo.home_space_name
        org.junit.Assert.assertEquals("HomeSpace",
                UserDetailExternalHelper.resolveSourceSpaceLabel(true, null, info));

        // resolver 是外部但没 Space 名 → 也要走 UserInfo 兜底
        org.junit.Assert.assertEquals("HomeSpace",
                UserDetailExternalHelper.resolveSourceSpaceLabel(
                        true, new ExternalViewerResolver.Resolution(true, ""), info));

        // 只剩 legacy source_space_name
        UserInfo legacy = userInfo("other_uid");
        legacy.is_external = 1;
        legacy.source_space_name = "LegacySpace";
        org.junit.Assert.assertEquals("LegacySpace",
                UserDetailExternalHelper.resolveSourceSpaceLabel(true, null, legacy));

        // 三路径都空 → null
        UserInfo empty = userInfo("other_uid");
        assertNull(UserDetailExternalHelper.resolveSourceSpaceLabel(true, null, empty));
    }

    // ---  Space 模式免好友分支（shouldUseSpaceModeSendMessage）---
    // 优先级锁定：external hint > self > Space-mode 非bot → sendMsg >
    //            Space-mode bot > 非Space-mode follow 逻辑

    /** Space 模式 + 非好友 + 人类 → 直接 sendMsg（核心新增场景，对齐 web ）。 */
    @Test
    public void spaceModeSendMsg_humanNonFriend_returnsTrue() {
        assertTrue("同 Space 非好友人类应走 sendMsg 分支，跳过 applyBtn",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ false,
                        /* viewerSpaceId  */ VIEWER_SPACE,
                        /* isBot          */ false,
                        /* follow         */ 0));
    }

    /** 外部成员 → 由 bottomPanel 隐藏接管，Space 模式分支必须让路。 */
    @Test
    public void spaceModeSendMsg_externalUser_returnsFalse() {
        assertFalse("跨 Space 外部成员不能走 sendMsg 分支（交给 bottomPanel 全隐 + 外部 hint）",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ true,
                        /* viewerSpaceId  */ VIEWER_SPACE,
                        /* isBot          */ false,
                        /* follow         */ 0));
    }

    /** Space 模式 + bot 非好友 → 走 bot_add_friend 审批流，Space 分支让路。 */
    @Test
    public void spaceModeSendMsg_bot_returnsFalse() {
        assertFalse("Space 模式下 bot 仍需保留 bot_add_friend 审批流",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ false,
                        /* viewerSpaceId  */ VIEWER_SPACE,
                        /* isBot          */ true,
                        /* follow         */ 0));
    }

    /** 非 Space 模式（陌生人 + vercode 老场景） → 保持 applyBtn 原有语义。 */
    @Test
    public void spaceModeSendMsg_nonSpaceMode_returnsFalse() {
        assertFalse("viewer 未进入 Space 模式时应保留原有「申请加好友」分支",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ false,
                        /* viewerSpaceId  */ "",
                        /* isBot          */ false,
                        /* follow         */ 0));
        assertFalse("null viewerSpaceId 也视为非 Space 模式",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        false, null, false, 0));
    }

    /** follow=1 → 原有「已是好友 → sendMsg」分支处理，此 helper 不抢接管。 */
    @Test
    public void spaceModeSendMsg_alreadyFriend_returnsFalse() {
        assertFalse("follow=1 由原 sendMsg 分支处理，此 helper 应短路返回 false",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ false,
                        /* viewerSpaceId  */ VIEWER_SPACE,
                        /* isBot          */ false,
                        /* follow         */ 1));
    }

    /**
     * 嘉伟 bot 硬约束（产品决策）：bot + friend + Space 模式 → 原 sendMsg 分支处理。
     * 优先级 #4：helper 短路返回 false（follow=1 在 isBot 检查之前），让活动层
     * 既有 {@code follow == 1 && !hideSendMsgForExternal} 分支统一走 sendMsgBtn，
     * 不干扰 bot add-friend 审批流（只对 follow=0 生效）。
     */
    @Test
    public void spaceModeSendMsg_botFriendInSpace_returnsFalse() {
        assertFalse("bot+friend+Space：helper 让路给原 sendMsg 分支，优先级 #4",
                UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                        /* isExternalUser */ false,
                        /* viewerSpaceId  */ VIEWER_SPACE,
                        /* isBot          */ true,
                        /* follow         */ 1));
    }

    // --- helpers ---

    private static UserInfo userInfo(String uid) {
        UserInfo info = new UserInfo();
        info.uid = uid;
        return info;
    }

    private static GroupMember groupMember(
            String homeSpaceId, String homeSpaceName,
            int isExternalLegacy, String sourceSpaceNameLegacy) {
        GroupMember gm = new GroupMember();
        gm.home_space_id = homeSpaceId;
        gm.home_space_name = homeSpaceName;
        gm.is_external = isExternalLegacy;
        gm.source_space_name = sourceSpaceNameLegacy;
        return gm;
    }
}
