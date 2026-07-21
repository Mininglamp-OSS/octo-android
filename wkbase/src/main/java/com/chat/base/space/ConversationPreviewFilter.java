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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

/**
 * -B · Layer B · Conversation list preview 的 render-time Space 过滤
 * （对齐 iOS {@code WKConversationWrapModel.spaceFilteredLastMessage} + Web
 * {@code Model.tsx :: lastMessage/unread getter} + Web {@code SpaceService.getSpaceFilteredLastMessage}）。
 *
 * <p><b>背景</b>：Android 的 conversation list 渲染对 {@code uiConversationMsg.wkMsg}
 * 零过滤，直接拿 {@code WKMsg.content} 原文出去。push 路径即使有 Layer A gate，
 * 以下 edge case 仍可能让跨 Space 内容浮到 preview 上：
 * <ol>
 *     <li>WKSDK 本地 DB 持久化无 Space 感知 → 历史脏数据会被 DB 读回</li>
 *     <li>SystemBot 的 {@code allConversations} entry 跨 Space 共享，冷启动一瞬间
 *         没过 gate 直接渲染可能泄露另一 Space 的最新消息</li>
 *     <li>外部群的 race 窗口 fail-open → Layer A 已经挡住，但渲染层再来一次零成本兜底</li>
 * </ol>
 *
 * <p><b>语义契约</b>（完全对齐 Web {@code getSpaceFilteredLastMessage}）：
 * <ul>
 *     <li>非 Space 模式（currentSpaceId 为空）→ 返回原消息</li>
 *     <li>PERSONAL:
 *         <ul>
 *             <li>{@code msg.space_id == currentSpaceId} → 原消息</li>
 *             <li>{@code msg.space_id != currentSpaceId && != null} → null（跨 Space 污染）</li>
 *             <li>{@code msg.space_id == null} + BotFather → null（对齐 iOS/web:
 *                 只 BotFather 参与消息级 Space 过滤）</li>
 *             <li>{@code msg.space_id == null} + 其它 SystemBot / 普通 DM → 原消息
 *                 （对齐 iOS "非 BotFather：视为属于当前空间"；这是通知助手 AI /
 *                 u_10000 / fileHelper 能在多 space 下正常显示预览的关键）</li>
 *         </ul>
 *     </li>
 *     <li>GROUP / COMMUNITY_TOPIC（Android 独有的防御性扩展，iOS/Web 靠 conversation
 *         list 本身不 include 跨 Space 群达到等价效果）：
 *         频道不属于当前 Space → null</li>
 * </ul>
 *
 * <p><b>纯函数 · host-side 可测</b>：所有 side-effect（currentSpaceId / isSystemBot /
 * shouldSkipChannelForSpace）通过参数注入，生产入口用 {@link SpaceFilter} + {@link SystemBotsFallback} 默认路径。
 *
 * <p><b>硬约束</b>：
 * <ul>
 *     <li>不改 {@link SpaceFilter} 7 分支纯函数</li>
 *     <li>不触碰 WKSDK 持久化；只影响 UI 层 getter</li>
 *     <li>返回 null 表示"渲染层应展示空"；调用方决定显示占位符 / 降级旧值</li>
 * </ul>
 */
public final class ConversationPreviewFilter {

    private ConversationPreviewFilter() {
    }

    /**
     * 渲染 preview / unread / timestamp 前的 Space 门禁。调用方应：
     * <ul>
     *     <li>true → preview 显示空（或兜底查 DB 找当前 Space 的更老消息）；unread 置 0；
     *         timestamp 不使用 {@code wkMsg} 的最新值来冒顶排序</li>
     *     <li>false → 正常渲染</li>
     * </ul>
     */
    public static boolean isMessageCrossSpace(@Nullable WKUIConversationMsg uc) {
        return isMessageCrossSpace(uc, SpaceFilter.getCurrentSpaceId());
    }

    /**
     * 纯函数版本（currentSpaceId 注入），便于 host-side 单测。
     *
     * <p>注意：GROUP / COMMUNITY_TOPIC 分支内部仍调用 {@link SpaceFilter#shouldSkipChannelForSpace(String, byte)}
     * 的单例路径（读 SharedPrefs 拿 currentSpaceId），此参数仅对 PERSONAL 分支生效。
     * 生产路径两条通道的 currentSpaceId 来源一致，无语义差异；host-side 单测可直接
     * 用 PERSONAL 路径测消息级过滤。
     */
    @VisibleForTesting
    public static boolean isMessageCrossSpace(@Nullable WKUIConversationMsg uc,
                                              @Nullable String currentSpaceId) {
        if (uc == null) return false;
        if (currentSpaceId == null || currentSpaceId.isEmpty()) return false;

        // GROUP / COMMUNITY_TOPIC 防御性兜底：跨 Space 频道直接视为 cross-space
        // （Layer A gate 已经挡掉 push，这里是冷启动 / DB 回放的二次保护）
        if (uc.channelType == WKChannelType.GROUP
                || uc.channelType == WKChannelType.COMMUNITY_TOPIC) {
            try {
                // 用 SpaceFilter 的缓存快速判定，读不到时 fail-open（保守放行，避免误杀
                // race 窗口的合法群）
                if (SpaceFilter.shouldSkipChannelForSpace(uc.channelID, uc.channelType)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // host-side 单测里 SDK 未初始化 → 直接走消息级分支
            }
            // GROUP/TOPIC 的 SpaceFilter 未命中就 fail-open，不再用 payload.space_id
            return false;
        }

        // PERSONAL
        WKMsg msg;
        try {
            msg = uc.getWkMsg();
        } catch (Throwable ignored) {
            // SDK 未初始化（host-side 单测）或 DB 异常 → 保守 fail-open
            msg = null;
        }
        if (msg == null) {
            // 无消息（SystemBotsFallback 占位 / 新会话）→ 不视为 cross-space
            return false;
        }
        String msgSpaceId = SpaceFilter.extractSpaceIdFromMsg(msg);
        if (msgSpaceId != null && !msgSpaceId.isEmpty()) {
            return !currentSpaceId.equals(msgSpaceId);
        }
        // msg 无 space_id: 只有 BotFather 视为跨 Space 污染需隐藏预览。
        // 对齐 iOS WKConversationWrapModel.spaceFilteredLastMessage (line 289-306)
        // 与 web SpaceService.tsx :: SYSTEM_BOTS = new Set(["botfather"])：只有 BotFather
        // 参与消息级 space 过滤；其它 SystemBot（通知助手 u_10000/notification/fileHelper 等）
        // 无 space_id 视为 "AI 回复默认属于当前 space" 放行 (iOS 注释原文："非 BotFather：
        // 视为属于当前空间")。之前 Android 走 SystemBotsFallback.isSystemBot 过严，把通知
        // 助手 AI 一并过滤，导致用户报 "没预览、不按时间排序、有时看不到新通知"。
        return ConversationPreviewSelector.BOTFATHER_ID.equals(uc.channelID);
    }

    /**
     * 渲染层取"空间过滤后的 wkMsg"——跨 Space 污染时返回 null（调用方应展示空预览或
     * 走 DB 查找当前 Space 的更老消息做兜底）。对齐 iOS {@code spaceFilteredLastMessage}
     * / Web {@code getSpaceFilteredLastMessage} 的 null 语义。
     */
    @Nullable
    public static WKMsg getSpaceFilteredWkMsg(@Nullable WKUIConversationMsg uc) {
        if (uc == null) return null;
        if (isMessageCrossSpace(uc)) return null;
        return uc.getWkMsg();
    }

    /**
     * 渲染层取"空间过滤后的 lastMsgTimestamp"——跨 Space 污染时返回 0，避免会话在列表
     * 里因被跨 Space push bump 而冒顶。Layer A gate 已阻止写入 allConversations；
     * 本函数用于 DB 回放 / 冷启动 race 的 UI 兜底。
     */
    public static long getSpaceFilteredTimestamp(@Nullable WKUIConversationMsg uc) {
        if (uc == null) return 0L;
        if (isMessageCrossSpace(uc)) return 0L;
        return uc.lastMsgTimestamp;
    }

    /**
     * 渲染层取"空间过滤后的 unreadCount"——跨 Space 污染时返回 0，避免显示错误红点。
     * 对齐 Web {@code unread getter} 对 SystemBot + 无 space_id 消息清零的语义。
     */
    public static int getSpaceFilteredUnread(@Nullable WKUIConversationMsg uc) {
        if (uc == null) return 0;
        if (isMessageCrossSpace(uc)) return 0;
        return uc.unreadCount;
    }
}
