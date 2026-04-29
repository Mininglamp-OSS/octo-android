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
 * YUJ-136 — DM 骚扰治理 Phase 1：UserInfo 面板跨 Space 发送消息按钮隐藏。
 *
 * <p>对齐 dmwork-web PR #1021。覆盖任务要求的 3 个核心场景 + 防回归的边界用例。
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
        assertTrue("跨 Space 必须隐藏发送消息按钮 (YUJ-136 对齐 web PR#1021)",
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
     * 仍然能判定为外部，隐藏按钮。对应「老后端没上 YUJ-87」过渡期。
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
     * Codex P2 回归（继承 YUJ-87）：fresh 带 home_space_id=viewer（「其实已同 Space」）
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
