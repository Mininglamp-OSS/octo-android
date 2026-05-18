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
 * <p> (DM 骚扰治理 Phase 1 / 对齐 web PR#1021)：UserInfo 面板进入路径可能来自外部群，
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
     *  (对齐 web PR#1013/1091 · iOS )：决定 UserInfo 页「申请加好友」
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
     * （对齐 web {@code UserInfo/index.tsx:52-55} / 企微语义）：
     * Space 模式下同 Space 非好友 → 直接「发送消息」，跳过「申请加好友」。
     *
     * <p>嘉伟 2026-05-01 Android 真机实测复现：外部群里点成员显示「申请加好友」。
     * 根因之一是 UserDetailActivity 对 {@code isExternalUser=false} 的分支
     * 未区分 Space 模式，同 Space 非好友回落到 {@link #shouldShowApplyButton}，
     * 结果当 UserInfo 携带 vercode 时错误显示 applyBtn。
     *
     * <p>按任务描述锁定的优先级（external hint &gt; self &gt; Space-mode 非bot
     * → sendMsg &gt; Space-mode bot &gt; 非Space-mode follow）：
     * <ul>
     *   <li>{@code isExternalUser=true} → 返回 false（交给 bottomPanel 隐藏分支）</li>
     *   <li>{@code follow=1} → 返回 false（原有「已是好友 → sendMsg」分支仍生效）</li>
     *   <li>{@code isBot=true} → 返回 false（保留 bot_add_friend 审批流，即使在 Space 模式）</li>
     *   <li>{@code viewerSpaceId} 为空 → 返回 false（非 Space 模式，走 follow + vercode 老路径）</li>
     *   <li>其它 → true（Space 模式 + 非好友 + 人类 → 直接 sendMsg，隐藏 applyBtn）</li>
     * </ul>
     *
     * @param isExternalUser 上游已判定的「相对 viewer 是否跨 Space 外部成员」
     * @param viewerSpaceId  {@code MsgModel.getInstance().getCurrentSpaceId()}（
     *                       空串 / null 视为非 Space 模式）
     * @param isBot          {@code UserInfo.robot == 1}
     * @param follow         {@code UserInfo.follow}（0 = 陌生，1 = 已加好友）
     */
    public static boolean shouldUseSpaceModeSendMessage(
            boolean isExternalUser, String viewerSpaceId, boolean isBot, int follow) {
        if (isExternalUser) return false;
        if (follow != 0) return false;
        if (isBot) return false;
        return !isNullOrEmpty(viewerSpaceId);
    }

    /**
     * （对齐 web PR#1021 `UserInfo/index.tsx:29-48` — isExternalToViewer=true
     * 时整个 bottomPanel 被 `.wk-userinfo-footer-external-hint` 替换）：
     * 外部成员视角下 UserInfo 页底部所有交互按钮（applyBtn / sendMsgBtn /
     * deleteLayout / pushBlackLayout）必须一起隐藏。单一判定入口方便单测覆盖。
     */
    public static boolean shouldHideBottomPanel(boolean isExternalUser) {
        return isExternalUser;
    }

    /**
     * ：与 {@link #shouldHideBottomPanel(boolean)} 对偶 —
     * 外部成员必须显示「仅可在群内交流」提示文案，语义上是 bottomPanel 的替代物。
     * 独立一个 helper 是为了让 activity 端不用重复写条件，也方便单测对齐 web 行为。
     */
    public static boolean shouldShowExternalHint(boolean isExternalUser) {
        return isExternalUser;
    }

    /**
     * （对齐 web Subscribers/list.tsx:320 `@<SourceSpaceName>`）：
     * 决定 UserInfo 页「姓名旁 / 来源行」要渲染的 Space 名。
     *
     * <p>非外部成员一律返回 null（不渲染）。外部成员按优先级：
     * <ol>
     *   <li>viewer-relative resolver 拿到的 {@code sourceSpaceName}（来自
     *       group_member / WKIM 成员缓存 extras）—— 最贴近 web subscriber.orgData；</li>
     *   <li>UserInfo 顶层 {@code home_space_name}（对齐 web channelInfo.orgData，
     *       老后端/缺 group_member 时的兜底）；</li>
     *   <li>legacy {@code source_space_name}（最老的字段，仅在 is_external=1 路径下
     *       有效）。</li>
     * </ol>
     * 三路径都空时返回 null，activity 端走「只显示 hint 不渲染 @SpaceName」的降级。
     */
    public static String resolveSourceSpaceLabel(
            boolean isExternalUser,
            ExternalViewerResolver.Resolution resolution,
            UserInfo userInfo) {
        if (!isExternalUser) return null;
        if (resolution != null && resolution.isExternal()) {
            String name = resolution.getSourceSpaceName();
            if (name != null && !name.isEmpty()) return name;
        }
        if (userInfo == null) return null;
        if (!isNullOrEmpty(userInfo.home_space_name)) {
            return userInfo.home_space_name;
        }
        if (!isNullOrEmpty(userInfo.source_space_name)) {
            return userInfo.source_space_name;
        }
        return null;
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
     * can be unit-tested without Robolectric.  EP1 pinned the same pattern
     * for data-layer paths after the codex P1 review.
     */
    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
