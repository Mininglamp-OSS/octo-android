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

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部群 · Space 隔离过滤（EP3 · ）
 *
 * <p>与 的 {@code shouldSkipChannelForSpace} 双路径判定对齐：
 * <ul>
 *     <li>客户端兜底：不完全信任后端 {@code SetEffectiveSpaceID}，本地做一层过滤。</li>
 *     <li>channel-level 用于会话列表；message-level 用于私聊消息 payload 过滤。</li>
 *     <li>对外部群成员（{@code source_space_id == currentSpaceId}）放行，保证
 *         「我在 Space A 以外部成员身份加入 Space B 的群 G」在 Space A 能看到 G。</li>
 * </ul>
 *
 * <p>该类对 Android SDK 的访问封装在 {@link ChannelInfoProvider} 中，便于 host-side 单元测试。
 *
 * <p>Web 对应 PR：#1036 source_space_id 兜底 / #1037 字段名对齐 / #1043 debug log。
 */
public final class SpaceFilter {

    /** 对应 web {@code SPACE_PREFIX_RE}：{@code s{spaceId}_...}，spaceId 为 32 位十六进制。 */
    private static final Pattern SPACE_PREFIX_RE = Pattern.compile("^s([0-9a-f]{32})_.*");

    /** remoteExtraMap 中群归属 Space 的 key（与后端 sync payload 约定）。 */
    public static final String CHANNEL_EXTRA_SPACE_ID = "space_id";

    private SpaceFilter() {
    }

    // ------------------------------------------------------------------
    // 可注入的基础设施（生产走 DEFAULT_PROVIDER，单元测试注入 stub）
    // ------------------------------------------------------------------

    /** 封装 Channel / Member 信息查询，方便在 host-side 单元测试里替换。 */
    public interface ChannelInfoProvider {
        /** 群归属 Space（从 {@code channelSpaceMap} 缓存 / {@code channelInfo.orgData.space_id} 读取）。 */
        @Nullable
        String getChannelSpaceId(String channelID, byte channelType);

        /** 查当前登录用户在该频道的 subscriber，返回 source_space_id（null 表示不是外部成员或记录缺失）。 */
        @Nullable
        String getMyMembershipSourceSpaceId(String channelID, byte channelType);

        /**
         * <b>我自己的</b> subscriber 行是否已同步到本地缓存。
         *
         * <p>P2 修复（对齐 iOS EP3 + codex 二次审查）：
         * 之前用「members 列表非空」作为加载信号不可靠——可能只有发消息者的 row 先同步下来，
         * 我自己的外部成员 row 还在路上。此时会误判 cached-mismatch → 错杀外部群。
         *
         * <p>正确信号：只检查 my own row 是否在本地 DB。若我的 row 存在，source_space_id
         * 就可信；若不存在，必是 race 窗口（conversation list 里能看到的 channel 按约定
         * 我必是 subscriber） → fail-open 等二次校准。
         */
        boolean isMyMembershipCached(String channelID, byte channelType);
    }

    private static final ChannelInfoProvider DEFAULT_PROVIDER = new ChannelInfoProvider() {
        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            try {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                if (channel != null && channel.remoteExtraMap != null) {
                    Object v = channel.remoteExtraMap.get(CHANNEL_EXTRA_SPACE_ID);
                    if (v != null) {
                        String s = v.toString();
                        return TextUtils.isEmpty(s) ? null : s;
                    }
                }
            } catch (Throwable ignored) {
                // SDK 尚未初始化或线程异常时直接走 fail-open 分支
            }
            return null;
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            try {
                String myUid = WKConfig.getInstance().getUid();
                if (TextUtils.isEmpty(myUid)) return null;
                WKChannelMember me = WKIM.getInstance().getChannelMembersManager()
                        .getMember(channelID, channelType, myUid);
                if (me == null || me.extraMap == null) return null;
                Object v = me.extraMap.get(WKChannelMemberExtras.sourceSpaceID);
                if (v == null) return null;
                String s = v.toString();
                return TextUtils.isEmpty(s) ? null : s;
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public boolean isMyMembershipCached(String channelID, byte channelType) {
            try {
                String myUid = WKConfig.getInstance().getUid();
                if (TextUtils.isEmpty(myUid)) return false;
                WKChannelMember me = WKIM.getInstance().getChannelMembersManager()
                        .getMember(channelID, channelType, myUid);
                return me != null;
            } catch (Throwable ignored) {
                return false; // SDK 异常视为未缓存 → fail-open
            }
        }
    };

    // ------------------------------------------------------------------
    // 对外 API（生产入口）
    // ------------------------------------------------------------------

    /** 读取当前 Space ID，未设置返回空串（非 Space 模式）。 */
    @NonNull
    public static String getCurrentSpaceId() {
        try {
            String v = WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id");
            return v == null ? "" : v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** {@code s{spaceId}_...} 前缀匹配（对齐 web SPACE_PREFIX_RE）。 */
    public static boolean hasSpacePrefix(@Nullable String channelID) {
        return channelID != null && SPACE_PREFIX_RE.matcher(channelID).matches();
    }

    /** 提取 channelID 中的 space 前缀（无前缀时返回 null）。 */
    @Nullable
    public static String extractSpacePrefix(@Nullable String channelID) {
        if (channelID == null) return null;
        Matcher m = SPACE_PREFIX_RE.matcher(channelID);
        return m.matches() ? m.group(1) : null;
    }

    /** 查当前用户在该频道的 source_space_id（非外部成员或 SDK 未就绪返回 null）。 */
    @Nullable
    public static String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
        return DEFAULT_PROVIDER.getMyMembershipSourceSpaceId(channelID, channelType);
    }

    /** 查群归属 Space（从 channel.remoteExtraMap 读取，未就绪返回 null）。 */
    @Nullable
    public static String getChannelSpaceId(String channelID, byte channelType) {
        return DEFAULT_PROVIDER.getChannelSpaceId(channelID, channelType);
    }

    /**
     * 是否跳过该频道（对齐 web {@code shouldSkipChannelForSpace} 双路径判定）。
     *
     * <p>分支顺序（对齐 web + iOS EP3 跨端一致性修复 + codex 二次审查修正）：
     * <ol>
     *     <li><b>space-empty-pass</b>: currentSpaceId 为空 → false（非 Space 模式不过滤）</li>
     *     <li><b>space-prefix-keep</b>: channelID 有 {@code s{spaceId}_} 前缀且匹配 currentSpaceId → false（Keep 快速路径）</li>
     *     <li><b>person-pass</b>: 私聊 → 不在此函数过滤（用 {@link #shouldSkipMessageForSpace(WKMsg)}）</li>
     *     <li><b>cached-match</b>: 群归属 Space 命中 currentSpaceId → false</li>
     *     <li><b>my-row-not-cached-fail-open</b>: 我自己的 subscriber 行尚未 sync 到本地 → false（竞态 fail-open）</li>
     *     <li><b>cached-external-member</b>: 我是以 currentSpaceId 身份加入的外部成员 → false</li>
     *     <li><b>cached-mismatch</b>: 我的 row 已 sync 但非外部成员 → true</li>
     *     <li><b>fail-open</b>: 无任何 Space 信息 → false（等 channelInfo 回调后二次检查）</li>
     * </ol>
     *
     * <p><b>P2 #1 修复（对齐 iOS EP3）</b>：space 前缀不匹配时 <b>fall-through</b> 到后续判定，
     * 而不是直接 return true。否则会错杀外部群：群 G 的 channel_id 带 Space B 前缀，
     * 我在 Space A 以外部成员身份加入（source_space_id=A），currentSpaceId=A 时前缀
     * 不匹配 → 如果直接 skip 就绕过了 external-member 兜底。
     *
     * <p><b>P2 #2 修复（对齐 iOS EP3 + codex 二次审查）</b>：用 {@code isMyMembershipCached}
     * （检查 my own row 是否在本地 DB）作为竞态信号，而不是「members 列表非空」——
     * 因为发送者的 row 可能先 sync 下来，我的外部成员 row 还在路上，此时「非空」会误判
     * cached-mismatch 错杀外部群。my row 存在才可信；不存在 → 进入 race 窗口，fail-open。
     */
    public static boolean shouldSkipChannelForSpace(String channelID, byte channelType) {
        return shouldSkipChannelForSpace(channelID, channelType, getCurrentSpaceId(), DEFAULT_PROVIDER);
    }

    /** 纯函数版本：所有 side-effect 通过参数注入，便于 host-side 单元测试。 */
    @VisibleForTesting
    public static boolean shouldSkipChannelForSpace(String channelID,
                                                    byte channelType,
                                                    @Nullable String currentSpaceId,
                                                    @NonNull ChannelInfoProvider provider) {
        // 1. space-empty-pass
        if (isBlank(currentSpaceId)) {
            diagLog(channelID, channelType, currentSpaceId, null, null, null, null,
                    "space-empty-pass", false);
            return false;
        }

        // 2. space-prefix Keep 快速路径（匹配即放行；不匹配 fall-through 给外部成员兜底机会）
        String prefix = extractSpacePrefix(channelID);
        if (prefix != null && currentSpaceId.equals(prefix)) {
            diagLog(channelID, channelType, currentSpaceId, null, prefix, null, null,
                    "space-prefix-keep", false);
            return false;
        }

        // 3. person-pass（旧兼容：私聊过滤交给 shouldSkipMessageForSpace）
        if (channelType == WKChannelType.PERSONAL) {
            diagLog(channelID, channelType, currentSpaceId, null, prefix, null, null,
                    "person-pass", false);
            return false;
        }

        // 4-7: Group —— 先拿到群归属 Space；优先读 channelInfo.orgData.space_id，
        // 读不到时兜底用 channel_id 前缀（prefix 的 spaceId 就是群归属）。
        String groupSpaceId = provider.getChannelSpaceId(channelID, channelType);
        if (isBlank(groupSpaceId)) {
            groupSpaceId = prefix; // prefix 不匹配当前 space 时 fall-through 到这里
        }

        if (!isBlank(groupSpaceId)) {
            if (currentSpaceId.equals(groupSpaceId)) {
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, null, null,
                        "cached-match", false);
                return false;
            }

            // P2 #2: 我自己的 subscriber 行未缓存时 fail-open，避免竞态错杀
            boolean myCached = provider.isMyMembershipCached(channelID, channelType);
            if (!myCached) {
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, null, Boolean.FALSE,
                        "my-row-not-cached-fail-open", false);
                return false;
            }

            String mySourceSpaceId = provider.getMyMembershipSourceSpaceId(channelID, channelType);
            if (!isBlank(mySourceSpaceId) && currentSpaceId.equals(mySourceSpaceId)) {
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, mySourceSpaceId, Boolean.TRUE,
                        "cached-external-member", false);
                return false;
            }
            diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, mySourceSpaceId, Boolean.TRUE,
                    "cached-mismatch", true);
            return true; // cached-mismatch（我的 row 已 sync 且非外部成员）
        }

        // 8. fail-open: 无任何 Space 信息
        diagLog(channelID, channelType, currentSpaceId, null, prefix, null, null,
                "fail-open", false);
        return false;
    }

    /**
     * 外部群漏显诊断日志（ /  问题 1）。
     *
     * <p>debug build 打印过滤决策全部关键变量：群 id / 归属 Space / 我自己的
     * source_space_id / 我的 row 是否已缓存 / 最终分支。用来定位「同账号
     * Web 端 2 个外部群全显示，Android 只显示 1 个」的数据缺失路径——
     * 不猜逻辑，先让日志说话。Release build 自动 no-op（{@code WKLogUtils.d}
     * 里判 {@code WKBinder.isDebug}）。
     *
     * <p>容错：{@code WKLogUtils.d} 依赖 Android framework（{@code Log}、
     * {@code TextUtils}），在 host-side 单元测试里会抛 "Stub!"；用 try/catch
     * 包住保证测试不被日志破坏（SpaceFilter 的纯函数路径必须保持 JVM-runnable）。
     */
    private static void diagLog(String channelID,
                                 byte channelType,
                                 @Nullable String currentSpaceId,
                                 @Nullable String groupSpaceId,
                                 @Nullable String prefix,
                                 @Nullable String mySourceSpaceId,
                                 @Nullable Boolean myCached,
                                 @NonNull String branch,
                                 boolean skip) {
        try {
            String line = "SpaceFilter#"
                    + " branch=" + branch
                    + " skip=" + skip
                    + " channelID=" + channelID
                    + " channelType=" + channelType
                    + " currentSpaceId=" + currentSpaceId
                    + " groupSpaceId=" + groupSpaceId
                    + " prefix=" + prefix
                    + " mySourceSpaceId=" + mySourceSpaceId
                    + " myMembershipCached=" + myCached;
            com.chat.base.utils.WKLogUtils.d(LOG_TAG, line);
        } catch (Throwable ignored) {
            // 忽略日志异常（host-side 单测或 Android stub 环境）
        }
    }

    private static final String LOG_TAG = "SpaceFilter";


    /**
     * 私聊消息级 Space 过滤（对齐 web {@code shouldSkipMessageForSpace}）。
     *
     * <p>返回 true 表示跳过该消息。私聊消息 payload 含 {@code space_id}，
     * 只有 space_id 明确不属于当前 Space 才跳过；缺字段时 fail-open。
     */
    public static boolean shouldSkipMessageForSpace(@Nullable WKMsg msg) {
        return shouldSkipMessageForSpace(msg, getCurrentSpaceId());
    }

    @VisibleForTesting
    public static boolean shouldSkipMessageForSpace(@Nullable WKMsg msg, @Nullable String currentSpaceId) {
        if (msg == null) return false;
        if (isBlank(currentSpaceId)) return false;
        String msgSpaceId = extractSpaceIdFromMsg(msg);
        if (isBlank(msgSpaceId)) return false; // fail-open
        return !currentSpaceId.equals(msgSpaceId);
    }

    /** 从消息中提取 space_id：优先 content JSON，其次 SDK 解码后的 baseContentMsgModel.spaceId。 */
    @Nullable
    public static String extractSpaceIdFromMsg(@Nullable WKMsg msg) {
        if (msg == null) return null;
        if (msg.content != null && !msg.content.isEmpty()) {
            try {
                JSONObject json = new JSONObject(msg.content);
                String sid = json.optString("space_id", "");
                if (sid != null && !sid.isEmpty()) return sid;
            } catch (Exception ignored) {
            }
        }
        if (msg.baseContentMsgModel != null
                && msg.baseContentMsgModel.spaceId != null
                && !msg.baseContentMsgModel.spaceId.isEmpty()) {
            return msg.baseContentMsgModel.spaceId;
        }
        return null;
    }

    /** 本地空串判断，不依赖 {@link TextUtils}，便于纯 JVM 单元测试。 */
    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isEmpty();
    }
}
