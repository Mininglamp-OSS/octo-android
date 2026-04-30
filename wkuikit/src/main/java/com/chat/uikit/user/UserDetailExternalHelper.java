package com.chat.uikit.user;

import com.chat.base.external.ExternalViewerResolver;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.group.service.entity.GroupMember;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure-function helper that answers「对当前 viewer 而言，这个 UserInfo 是不是外部成员？」
 *
 * <p>YUJ-136 (DM 骚扰治理 Phase 1 / 对齐 web PR#1021)：UserInfo 面板进入路径可能来自外部群，
 * 客户端必须在渲染「发送消息」按钮前判定跨 Space 关系，隐藏跨 Space 的 DM 入口。
 *
 * <p>本类与 {@link UserDetailActivity#applyExternalSourceRow} 共用同一套数据源优先级
 * （fresh group_member / WKIM 成员缓存 / legacy fresh），抽出来是为了在纯 JVM 下单测 —
 * 活动本身依赖 Android framework，直接测试会被逼使用 Robolectric。
 *
 * <p>规则（与 {@link ExternalViewerResolver} 一致）：
 * <ul>
 *   <li>{@code groupID} 为空 → {@code null}（非群内路径不判定）</li>
 *   <li>{@code userInfo.uid} == {@code viewerUid} → {@code null}（自看直接走老逻辑）</li>
 *   <li>无任何数据源 → {@code null}</li>
 *   <li>否则返回 {@link ExternalViewerResolver.Resolution}</li>
 * </ul>
 */
public final class UserDetailExternalHelper {

    private UserDetailExternalHelper() {
    }

    /**
     * Compute the viewer-relative external resolution for the user in the context
     * of a given group view.
     *
     * @param userInfo      fresh UserInfo returned from {@code /users/{uid}?group_no=…}
     * @param member        stale WKIM channel member cache entry (may be null)
     * @param viewerUid     current logged-in user id (for self check)
     * @param viewerSpaceId current viewer Space id (from MsgModel.getCurrentSpaceId())
     * @param groupID       group id when the UserInfo page was opened from a group;
     *                      empty/null means direct entry (no external check applies)
     * @return resolution, or {@code null} if not applicable
     */
    public static ExternalViewerResolver.Resolution resolve(
            UserInfo userInfo,
            WKChannelMember member,
            String viewerUid,
            String viewerSpaceId,
            String groupID) {
        if (userInfo == null) return null;
        if (isNullOrEmpty(groupID)) return null;
        if (userInfo.uid != null && userInfo.uid.equals(viewerUid)) return null;

        Map<String, Object> extras = buildExtras(userInfo.group_member, member);
        if (extras == null || extras.isEmpty()) return null;

        return ExternalViewerResolver.resolveFromExtras(extras, viewerSpaceId);
    }

    /**
     * Convenience: should we force-hide the "发送消息" button for this user?
     *
     * <p>Aligned with web PR#1021: external members never offer a DM entry point
     * regardless of follow status. Self-view / non-group entry / same-Space
     * members all fall through and keep the existing follow-driven behaviour.
     */
    public static boolean shouldHideSendMessageButton(ExternalViewerResolver.Resolution resolution) {
        return resolution != null && resolution.isExternal();
    }

    /**
     * YUJ-177 (对齐 web PR#1013/1091 · iOS YUJ-136)：决定 UserInfo 页「申请加好友」
     * 按钮是否可见。跨 Space 外部成员必须强制隐藏，阻断 Space 边界越权入口；
     * 同 Space / 自看 / 非群路径回退到原有 {@code follow} + {@code vercode} 逻辑。
     *
     * @param isExternalUser 上游 {@code UserDetailActivity.isExternalUser} 判定结果
     * @param follow         {@code UserInfo.follow}（0 = 陌生，1 = 已加好友）
     * @param hasVercode     是否持有 {@code vercode}（进入用户详情携带的申请入场券）
     * @return {@code true} 表示 applyBtn 应设为 {@code View.VISIBLE}；
     *         {@code false} 表示 {@code View.GONE}
     */
    public static boolean shouldShowApplyButton(
            boolean isExternalUser, int follow, boolean hasVercode) {
        if (isExternalUser) return false;
        if (follow != 0) return false;
        return hasVercode;
    }

    /**
     * Data-source merge extracted from {@code UserDetailActivity.applyExternalSourceRow}:
     * fresh response with home_space_id wins, else fall back to WKIM cache, else
     * use legacy fields from the fresh response. See codex review P2 for rationale.
     */
    static Map<String, Object> buildExtras(GroupMember fresh, WKChannelMember member) {
        boolean freshHasHomeSpace = fresh != null && !isNullOrEmpty(fresh.home_space_id);
        Map<String, Object> extras = null;

        if (freshHasHomeSpace) {
            extras = new HashMap<>();
            if (member != null && member.extraMap != null) {
                extras.putAll(member.extraMap);
            }
            extras.put(WKChannelMemberExtras.homeSpaceID, fresh.home_space_id);
            if (!isNullOrEmpty(fresh.home_space_name)) {
                extras.put(WKChannelMemberExtras.homeSpaceName, fresh.home_space_name);
            } else {
                extras.remove(WKChannelMemberExtras.homeSpaceName);
            }
            extras.put(WKChannelMemberExtras.isExternal, fresh.is_external);
            if (!isNullOrEmpty(fresh.source_space_name)) {
                extras.put(WKChannelMemberExtras.sourceSpaceName, fresh.source_space_name);
            }
        } else if (member != null && member.extraMap != null && !member.extraMap.isEmpty()) {
            extras = member.extraMap;
        } else if (fresh != null) {
            extras = new HashMap<>();
            if (fresh.is_external != 0) {
                extras.put(WKChannelMemberExtras.isExternal, fresh.is_external);
            }
            if (!isNullOrEmpty(fresh.source_space_name)) {
                extras.put(WKChannelMemberExtras.sourceSpaceName, fresh.source_space_name);
            }
        }
        return extras;
    }

    /**
     * JVM-friendly alternative to {@code TextUtils.isEmpty}: used so the helper
     * can be unit-tested without Robolectric. YUJ-86 EP1 pinned the same pattern
     * for data-layer paths after the codex P1 review.
     */
    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
