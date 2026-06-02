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

    /**
     * remoteExtraMap 中当前用户在该群的 source_space_id（外部成员通过哪个 Space 加入）的
     * 字面 key——保留作为后端 sync payload 的接线契约（{@code gh251_constant_matchesBackendContract}
     * 测试锁定）。
     *
     * <p><b>GH dmwork-android#251 Round-2</b>：实际存储位置已从
     * {@code channel.remoteExtraMap} 搬到 {@code ConversationManager.convSyncExternalMap}
     * 独立缓存——因为 {@code ChannelManager.updateChannel(WKChannel)} 在 channelInfo
     * 异步水合时会整体替换 {@code remoteExtraMap}，conv sync 预填的
     * {@code my_source_space_id}（channelInfo 通常不带此字段）会被一次性抹掉，竞态又回来。
     * 这个常量保留是为了：(a) 跨端字面对齐（web/iOS 同名 key），(b) 单元测试合约锁定，
     * (c) 万一未来某条路径仍需要从 remoteExtraMap 读，避免字符串散落。
     */
    public static final String CHANNEL_EXTRA_MY_SOURCE_SPACE_ID = "my_source_space_id";

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

        /**
         * GH dmwork-android#251 / octo-server PR #154：读 conv sync 写入的
         * {@code my_source_space_id}。
         *
         * <p>用途：当 my-row 还没 sync 下来（{@link #isMyMembershipCached} 返回 false）时，
         * 优先用 conv sync 给的 my_source_space_id 做外部成员判定，避免 fail-open 让
         * 跨 Space 群泄漏到当前视图。后端未部署 PR #154 时返回 null，走原有 fail-open 路径。
         *
         * <p><b>GH #251 Round-2</b>：默认实现现在读
         * {@code ConversationManager.convSyncExternalMap} 而不是
         * {@code channel.remoteExtraMap}——因为后者会被 channelInfo 异步水合整体替换覆盖。
         * 单元测试通过 stub 注入。
         */
        @Nullable
        default String getConvSyncMySourceSpaceId(String channelID, byte channelType) {
            return null;
        }

        default boolean isSpaceCacheAuthoritative() {
            return false;
        }
    }

    private static final ChannelInfoProvider DEFAULT_PROVIDER = new ChannelInfoProvider() {
        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            // 优先源：channel.remoteExtraMap[space_id]（channelInfo 异步水合写入，权威）。
            // Fallback：ConversationManager.convSyncSpaceMap（conv sync 预填，channelInfo
            // 没回此字段或还没水合时兜底）。
            //
            // GH dmwork-android#251 Round-2：原 PR 把 conv sync 的 space_id 也写进
            // remoteExtraMap，但 ChannelManager.updateChannel 在 channelInfo 水合时
            // 会整体替换 remoteExtraMap → conv sync 写入的两个键会被一次性抹掉。
            // 改为独立缓存 + 两段式 fallback 后，权威源缺失时 conv sync 仍能兜底，
            // 权威源到位时优先级自然回正。
            try {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                if (channel != null && channel.remoteExtraMap != null) {
                    Object v = channel.remoteExtraMap.get(CHANNEL_EXTRA_SPACE_ID);
                    if (v != null) {
                        String s = v.toString();
                        if (!TextUtils.isEmpty(s)) return s;
                    }
                }
            } catch (Throwable ignored) {
                // SDK 尚未初始化或线程异常时落 conv sync fallback
            }
            try {
                String fallback = WKIM.getInstance().getConversationManager()
                        .getConvSyncSpaceId(channelID);
                return TextUtils.isEmpty(fallback) ? null : fallback;
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            // 优先源：member DB（subscriber 表里我自己的 row.source_space_id，member sync 写入）。
            // Fallback：ConversationManager.convSyncExternalMap（conv sync 预填，member sync
            // 还没把我自己的 row 推下来时兜底）。
            //
            // GH dmwork-android#251 Round-2：与 getChannelSpaceId 同理，把 conv sync 的
            // my_source_space_id 从 remoteExtraMap 搬到独立缓存，避开 channelInfo 水合
            // 整体替换的覆盖窗口。
            try {
                String myUid = WKConfig.getInstance().getUid();
                if (!TextUtils.isEmpty(myUid)) {
                    WKChannelMember me = WKIM.getInstance().getChannelMembersManager()
                            .getMember(channelID, channelType, myUid);
                    if (me != null && me.extraMap != null) {
                        Object v = me.extraMap.get(WKChannelMemberExtras.sourceSpaceID);
                        if (v != null) {
                            String s = v.toString();
                            if (!TextUtils.isEmpty(s)) return s;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // member DB 不可读时落 conv sync fallback
            }
            try {
                String fallback = WKIM.getInstance().getConversationManager()
                        .getConvSyncMySourceSpaceId(channelID);
                return TextUtils.isEmpty(fallback) ? null : fallback;
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

        /**
         * GH dmwork-android#251 Round-2：读
         * {@code ConversationManager.convSyncExternalMap}（不再读 channel.remoteExtraMap）。
         * 改读独立缓存的原因见 {@link #getMyMembershipSourceSpaceId} javadoc。
         */
        @Override
        public String getConvSyncMySourceSpaceId(String channelID, byte channelType) {
            try {
                String v = WKIM.getInstance().getConversationManager()
                        .getConvSyncMySourceSpaceId(channelID);
                return TextUtils.isEmpty(v) ? null : v;
            } catch (Throwable ignored) {
                // SDK 未就绪或线程异常时返回 null，让上层走 fail-open / member sync 兜底
                return null;
            }
        }

        @Override
        public boolean isSpaceCacheAuthoritative() {
            try {
                return WKIM.getInstance().getConversationManager().isSpaceCacheAuthoritative();
            } catch (Throwable ignored) {
                return false;
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

    /** 从子区 channelID（{parentGroupId}____{threadId}）中提取父群 ID。 */
    @Nullable
    public static String extractParentGroupId(@Nullable String channelID) {
        if (channelID == null) return null;
        int sep = channelID.indexOf("____");
        return sep > 0 ? channelID.substring(0, sep) : null;
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
     * 查 conv sync 写入的 my_source_space_id（未部署 PR #154 / 该群没有外部成员关系返回 null）。
     * GH dmwork-android#251 / octo-server PR #154。
     */
    @Nullable
    public static String getConvSyncMySourceSpaceId(String channelID, byte channelType) {
        return DEFAULT_PROVIDER.getConvSyncMySourceSpaceId(channelID, channelType);
    }

    /**
     * 是否跳过该频道（对齐 web {@code shouldSkipChannelForSpace} 双路径判定）。
     *
     * <p>分支顺序（对齐 web + iOS EP3 跨端一致性修复 + codex 二次审查修正 + GH #251 conv-sync 短路）：
     * <ol>
     *     <li><b>space-empty-pass</b>: currentSpaceId 为空 → false（非 Space 模式不过滤）</li>
     *     <li><b>space-prefix-keep</b>: channelID 有 {@code s{spaceId}_} 前缀且匹配 currentSpaceId → false（Keep 快速路径）</li>
     *     <li><b>person-pass</b>: 私聊 → 不在此函数过滤（用 {@link #shouldSkipMessageForSpace(WKMsg)}）</li>
     *     <li><b>cached-match</b>: 群归属 Space 命中 currentSpaceId → false</li>
     *     <li><b>convsync-external-member / convsync-mismatch</b>（GH #251）: conv sync 已带
     *         {@code my_source_space_id} 时跳过 my-row sync 等待，直接判定。
     *         {@code convsync-external-member}：我以当前 Space 身份加入 → false；
     *         {@code convsync-mismatch}：source_space 与 currentSpace 不一致 → true。</li>
     *     <li><b>my-row-not-cached-fail-open</b>: conv sync 没给 my_source_space_id 且 my-row 未 sync → false</li>
     *     <li><b>cached-external-member</b>: 我是以 currentSpaceId 身份加入的外部成员 → false</li>
     *     <li><b>cached-mismatch</b>: 我的 row 已 sync 但非外部成员 → true</li>
     *     <li><b>convsync-*-no-group-space</b>（GH #251）: 末尾 fail-open 之前再试一次 conv sync
     *         的 my_source_space_id；命中即出 Keep/Skip，避免 group space_id 缺失也走 fail-open。</li>
     *     <li><b>fail-open</b>: 完全无 Space 信息 → false（等 channelInfo 回调后二次检查）</li>
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
     *
     * <p><b>GH #251 修复（octo-server PR #154 配套）</b>：octo-server 现在在 conversation
     * sync 响应里 resolved 的 group {@code space_id} 和 viewer 的 {@code my_source_space_id}
     * 直接回填到 conversation entry。SDK 把这两个字段写进
     * {@code ConversationManager.convSyncSpaceMap} / {@code convSyncExternalMap}
     * 两张独立内存缓存（<b>Round-2 关键</b>：不再写 {@code channel.remoteExtraMap}，
     * 否则会被 {@code ChannelManager.updateChannel} 在 channelInfo 异步水合时整体替换抹掉）。
     * {@link ChannelInfoProvider#getChannelSpaceId} / {@link ChannelInfoProvider#getMyMembershipSourceSpaceId}
     * 现在以「权威源（channelInfo / member DB）→ conv sync 缓存」两段式读取；
     * {@link ChannelInfoProvider#getConvSyncMySourceSpaceId} 仍单独暴露给 SpaceFilter 用于
     * 在 my-row 未缓存阶段做权威短路。老后端无此字段时 conv sync 缓存为空，行为退化到
     * 原 fail-open 路径，保持向后兼容。
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

        // 3.5 thread-delegate: 子区（COMMUNITY_TOPIC）委托给父群判断。
        // 子区 channelID 格式: {parentGroupId}____{threadId}，提取父群 ID 递归调用。
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            String parentGroupId = extractParentGroupId(channelID);
            if (!isBlank(parentGroupId)) {
                boolean skip = shouldSkipChannelForSpace(parentGroupId, WKChannelType.GROUP,
                        currentSpaceId, provider);
                diagLog(channelID, channelType, currentSpaceId, null, prefix, null, null,
                        "thread-delegate(parent=" + parentGroupId + ")", skip);
                return skip;
            }
            // 无法提取父群 ID，fall-through 到后续逻辑
        }

        // 4-7: Group —— 先拿到群归属 Space；优先读 channelInfo.orgData.space_id，
        // 读不到时兜底用 channel_id 前缀（prefix 的 spaceId 就是群归属）。
        String groupSpaceId = provider.getChannelSpaceId(channelID, channelType);
        if (isBlank(groupSpaceId)) {
            groupSpaceId = prefix;
        }

        if (!isBlank(groupSpaceId)) {
            if (currentSpaceId.equals(groupSpaceId)) {
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, null, null,
                        "cached-match", false);
                return false;
            }

            String convSyncMySource = provider.getConvSyncMySourceSpaceId(channelID, channelType);
            if (!isBlank(convSyncMySource)) {
                if (currentSpaceId.equals(convSyncMySource)) {
                    diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, convSyncMySource, null,
                            "convsync-external-member", false);
                    return false;
                }
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, convSyncMySource, null,
                        "convsync-mismatch", true);
                return true;
            }

            boolean authoritative = provider.isSpaceCacheAuthoritative();
            if (authoritative) {
                diagLog(channelID, channelType, currentSpaceId, groupSpaceId, prefix, null, null,
                        "cached-mismatch", true);
                return true;
            }

            // 非权威缓存（老后端 / 首次 sync 前）：保留 member DB 检查 + fail-open 兜底
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
            return true;
        }

        // 8. 无 groupSpaceId（新群 / channelInfo 未到）：fail-open 等数据补齐后自动修正
        String convSyncMySource = provider.getConvSyncMySourceSpaceId(channelID, channelType);
        if (!isBlank(convSyncMySource)) {
            if (currentSpaceId.equals(convSyncMySource)) {
                diagLog(channelID, channelType, currentSpaceId, null, prefix, convSyncMySource, null,
                        "convsync-external-member-no-group-space", false);
                return false;
            }
            diagLog(channelID, channelType, currentSpaceId, null, prefix, convSyncMySource, null,
                    "convsync-mismatch-no-group-space", true);
            return true;
        }

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
            String channelName = "";
            if (channelType == WKChannelType.GROUP) {
                WKChannel ch = com.xinbida.wukongim.WKIM.getInstance().getChannelManager()
                        .getChannel(channelID, channelType);
                if (ch != null && ch.channelName != null) channelName = ch.channelName;
            }
            String line = "SpaceFilter#"
                    + " branch=" + branch
                    + " skip=" + skip
                    + " channelID=" + channelID
                    + " name=" + channelName
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
