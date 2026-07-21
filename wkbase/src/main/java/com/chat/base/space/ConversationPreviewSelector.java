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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

/**
 * 会话列表 preview / timestamp 的<b>计算属性 selector</b>（对齐 iOS
 * {@link com.chat.base.space.ConversationPreviewSelector iOS spaceFilteredLastMessage}
 * / {@code WKConversationWrapModel.m}）。
 *
 * <p><b>设计原则</b>：不 mutate SDK 的 {@link WKUIConversationMsg} 字段，只作为
 * wrap getter 提供渲染 / 排序需要的"显示态"数据。SDK 后续刷新 conversation 时
 * 不会覆盖显示态（对齐 iOS），从根本避免"进聊天页返回排序又乱"的问题。
 *
 * <p><b>过滤规则</b>（对齐 iOS {@code WKConversationWrapModel.spaceFilteredLastMessage}
 * 与 web {@code SpaceService.tsx :: SYSTEM_BOTS = new Set(["botfather"])}，但真人 DM
 * 收窄到旧严格行为以避免影响原有业务）：
 * <ul>
 *     <li>rawMsg 有 {@code space_id} 且等于当前 space → 返回 rawMsg</li>
 *     <li>rawMsg 无 {@code space_id}:
 *         <ul>
 *             <li>非 BotFather (含 SystemBot 和真人 DM) → 返回 rawMsg（"AI 回复默认属于当前
 *                 space" / 真人 DM 向前兼容老消息）</li>
 *             <li>BotFather → 从本地消息表分页查找带当前 {@code space_id} 的消息，
 *                 找不到返回 null</li>
 *         </ul>
 *     </li>
 *     <li>rawMsg 的 {@code space_id} 属于其它 space:
 *         <ul>
 *             <li><b>真人 DM (非 SystemBot)</b>：直接返回 null（旧严格行为，preview 空。
 *                 用户明确要求"改动只对 SystemBot 生效，不影响真人私聊/群/子区的
 *                 现有 Space 隔离语义"）</li>
 *             <li>SystemBot (含 BotFather)：分页查找当前 space；
 *                 非 BotFather 找不到用第一条无 space_id 的消息兜底</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p><b>SystemBot 的空 entry 兜底</b>：{@link SystemBotsFallback#buildPlaceholder} 合成的
 * 占位 entry {@code wkMsg=null / lastMsgTimestamp=0}，或 SDK 推的 entry {@code clientMsgNo}
 * 在本地消息表查不到（如 {@code u_10000} 场景，用户报"通知助手 AI 没预览、不按时间排序、
 * 有时看不到新通知"的第 2 类根因）→ selector 直接从消息表按 {@code order_seq desc}
 * 拿最新一条，SQL 只排除 {@code type=0/99}，覆盖交互式卡片 (type=17) / markdown /
 * 其它业务扩展 bot 消息类型（老路径 {@code searchMsgWithChannelAndContentTypes(WK_TEXT)}
 * 会错过所有非文本 bot 消息）。
 *
 * <p><b>没有缓存的选择</b>：SystemBot 数量少（3-5 个），每次 render 查一次 DB 走
 * {@code order_seq} 索引应为亚毫秒级；顶层 comparator 一次排序也只调用一次每 bot。
 * 若性能观察到问题，可加 in-memory {@code Map<channelID, WKMsg>} 缓存 + SDK
 * conversation listener 里 invalidate（对齐 iOS {@code cachedSpaceLastMessage} +
 * {@code setLastMessage} clear 的时序）。
 *
 * <p><b>纯类 static 方法</b>：不持有状态，安全线程共享。所有 DB / space 依赖走
 * {@link MsgDbManager} / {@link SpaceFilter} 现有单例，测试通过 helper hook。
 */
public final class ConversationPreviewSelector {

    /** BotFather 频道 ID —— 严格 space 过滤的唯一 SystemBot（对齐 web {@code SYSTEM_BOTS}）。 */
    public static final String BOTFATHER_ID = "botfather";

    /** DB 分页查找的每页大小，对齐 iOS {@code getMessages limit:200}。 */
    private static final int PAGE_SIZE = 200;

    /** 分页查找最多扫描多少页（防御恶意长历史；iOS 无硬限，靠 orderSeq==0 终止）。 */
    private static final int MAX_PAGES = 5;

    private ConversationPreviewSelector() {
    }

    /**
     * 选出用于会话列表 preview 显示的 {@link WKMsg}。语义详见类文档。
     *
     * @return 用于渲染的 msg，或 null（表示无匹配消息，UI 层显示空 preview）
     */
    @Nullable
    public static WKMsg selectDisplayMessage(@Nullable WKUIConversationMsg uc) {
        return selectDisplayMessage(uc, SpaceFilter.getCurrentSpaceId());
    }

    /**
     * 纯函数版本（currentSpaceId 注入）。生产走单参数重载读 SharedPrefs，测试注入。
     */
    @VisibleForTesting
    @Nullable
    static WKMsg selectDisplayMessage(@Nullable WKUIConversationMsg uc,
                                      @Nullable String currentSpaceId) {
        if (uc == null) return null;
        WKMsg raw = safeGetWkMsg(uc);

        // 非 Space 模式 / 非 PERSONAL：不走 space 过滤，直接返回 raw
        // （raw 为 null 时对 SystemBot 走空 entry 兜底；其它频道 raw==null 就是没消息，null 是对的）
        boolean inSpace = currentSpaceId != null && !currentSpaceId.isEmpty();
        boolean isPersonal = uc.channelType == WKChannelType.PERSONAL;
        if (!inSpace || !isPersonal) {
            return raw != null ? raw : hydrateSystemBotEmpty(uc);
        }

        // PERSONAL + 多 space
        boolean isBotFather = BOTFATHER_ID.equals(uc.channelID);
        boolean isSystemBot = SystemBotsFallback.isSystemBot(uc.channelID);

        if (raw != null) {
            String rawSpaceId = SpaceFilter.extractSpaceIdFromMsg(raw);
            if (rawSpaceId != null && rawSpaceId.equals(currentSpaceId)) {
                return raw;                    // 明确匹配当前 space
            }
            if (rawSpaceId == null || rawSpaceId.isEmpty()) {
                if (!isBotFather) {
                    return raw;                // 非 BotFather：AI 回复默认属于当前 space
                }
                // BotFather 无 space_id → 走下面的 DB 分页查找当前 space 消息
            } else {
                // 有 space_id 但不匹配当前 space
                // 真人 DM（非 SystemBot）：保持旧严格行为 —— 直接返回 null 让 preview 空,
                // 不做 DB 分页 / 无 space_id 兜底。避免"跨 Space 老消息被显示"的风险。
                // 用户强调改动只对 SystemBot 生效，不影响真人私聊/群/子区的现有隔离语义。
                // 只有 SystemBot（含 BotFather）才允许走下面的 DB 分页找当前 space 消息。
                if (!isSystemBot) {
                    return null;
                }
            }
        } else {
            // raw==null（SystemBot placeholder / stale clientMsgNo）：
            // - 非 BotFather 且 SystemBot：从消息表拿最新一条（对齐 iOS "非 BotFather"）
            // - BotFather：走下面的 DB 分页找当前 space
            // - 真人 DM（raw==null 就是没消息）：hydrateSystemBotEmpty 内部判 !isSystemBot 返回 null
            if (!isBotFather) {
                return hydrateSystemBotEmpty(uc);
            }
        }

        // 到这里的场景：BotFather，或 SystemBot 且 raw 属于其它 space
        // 真人 DM 的对应分支已在上面 return null 提前 short-circuit
        // isBotFather=true → 严过滤(找不到不兜底); 其它 SystemBot → 允许无 space_id 兜底
        return findSpaceScopedMessage(uc.channelID, uc.channelType, currentSpaceId, isBotFather);
    }

    /**
     * 选出用于排序 / 时间显示的 timestamp。对齐 iOS
     * {@code -[WKConversationWrapModel lastMsgTimestamp]}:
     * <ul>
     *     <li>非 PERSONAL / 非 SystemBot / 非多 space 模式 → 原生 {@code uc.lastMsgTimestamp}</li>
     *     <li>SystemBot 多 space 模式 → {@code selectDisplayMessage(uc).timestamp}，
     *         这样排序键与 preview 内容永远同源</li>
     * </ul>
     *
     * <p>保证一致性：排序 comparator 与 render 层 {@code showTime} 都调此方法，
     * 不会出现 "位置排到最新但显示旧时间" 或反之。
     */
    public static long selectDisplayTimestamp(@Nullable WKUIConversationMsg uc) {
        return selectDisplayTimestamp(uc, SpaceFilter.getCurrentSpaceId(), null);
    }

    /**
     * 复用外部已算好的 displayMsg —— 会话列表 bind 时 {@code showTime} 之外
     * ({@code showContent} / {@code showCompactReminders}) 通常也需要 displayMsg，
     * 传入这里就能避免 SystemBot 分支重复一次 DB 分页 (5×200)。
     *
     * @param cachedDisplayMsg 传 null 走默认 {@link #selectDisplayMessage} 内部计算；
     *                         传非 null 时必须是同一 {@code uc} 与同一 currentSpaceId
     *                         下的结果，否则语义漂移
     */
    public static long selectDisplayTimestamp(@Nullable WKUIConversationMsg uc,
                                              @Nullable WKMsg cachedDisplayMsg) {
        return selectDisplayTimestamp(uc, SpaceFilter.getCurrentSpaceId(), cachedDisplayMsg);
    }

    @VisibleForTesting
    static long selectDisplayTimestamp(@Nullable WKUIConversationMsg uc,
                                       @Nullable String currentSpaceId) {
        return selectDisplayTimestamp(uc, currentSpaceId, null);
    }

    @VisibleForTesting
    static long selectDisplayTimestamp(@Nullable WKUIConversationMsg uc,
                                       @Nullable String currentSpaceId,
                                       @Nullable WKMsg cachedDisplayMsg) {
        if (uc == null) return 0L;
        boolean inSpace = currentSpaceId != null && !currentSpaceId.isEmpty();
        boolean isPersonal = uc.channelType == WKChannelType.PERSONAL;
        boolean isSystemBot = SystemBotsFallback.isSystemBot(uc.channelID);
        if (inSpace && isPersonal && isSystemBot) {
            WKMsg displayMsg = cachedDisplayMsg != null
                    ? cachedDisplayMsg
                    : selectDisplayMessage(uc, currentSpaceId);
            return displayMsg != null ? displayMsg.timestamp : 0L;
        }
        return uc.lastMsgTimestamp;
    }

    // ------------------------------------------------------------------
    // internal helpers
    // ------------------------------------------------------------------

    @Nullable
    private static WKMsg safeGetWkMsg(@NonNull WKUIConversationMsg uc) {
        try {
            return uc.getWkMsg();
        } catch (Throwable ignored) {
            // host-side / SDK 未初始化：保守返回 null
            return null;
        }
    }

    /**
     * SystemBot placeholder / stale clientMsgNo 场景：从消息表按 {@code order_seq desc}
     * 拿最新一条，覆盖交互式卡片等非文本类型（{@code queryMaxOrderSeqMsgWithChannel}
     * 的 SQL 只排除 {@code type=0/99}）。仅对 SystemBot 生效。
     */
    @Nullable
    static WKMsg hydrateSystemBotEmpty(@NonNull WKUIConversationMsg uc) {
        if (!SystemBotsFallback.isSystemBot(uc.channelID)) return null;
        return queryLatestMsg(uc.channelID, uc.channelType);
    }

    /**
     * 分页扫本地消息表找带 {@code currentSpaceId} 的消息。找不到的兜底：
     * <ul>
     *     <li>{@code useFallbackNoSpaceId=true}（非 BotFather）→ 用第一条无 space_id 的消息</li>
     *     <li>{@code useFallbackNoSpaceId=false}（BotFather）→ null（显示空）</li>
     * </ul>
     * 对齐 iOS {@code WKConversationWrapModel.m:319-352} 的分页 + noSpaceIdMessage 兜底逻辑。
     */
    @Nullable
    static WKMsg findSpaceScopedMessage(String channelID, byte channelType,
                                        String currentSpaceId, boolean isBotFather) {
        WKMsg noSpaceIdFallback = null;
        long cursor = 0L;
        for (int page = 0; page < MAX_PAGES; page++) {
            java.util.List<WKMsg> batch = querySearchMsgs(channelID, channelType, cursor, PAGE_SIZE);
            if (batch == null || batch.isEmpty()) break;
            for (WKMsg msg : batch) {
                String sid = SpaceFilter.extractSpaceIdFromMsg(msg);
                if (sid != null && sid.equals(currentSpaceId)) {
                    return msg;
                }
                if (!isBotFather && noSpaceIdFallback == null
                        && (sid == null || sid.isEmpty())) {
                    noSpaceIdFallback = msg;
                }
            }
            WKMsg oldest = batch.get(batch.size() - 1);
            if (oldest.orderSeq == 0L || batch.size() < PAGE_SIZE) break;
            cursor = oldest.orderSeq;
        }
        return noSpaceIdFallback;
    }

    // --- DB access indirection (可被单测替换) ---

    @Nullable
    static WKMsg queryLatestMsg(String channelID, byte channelType) {
        try {
            return MsgDbManager.getInstance()
                    .queryMaxOrderSeqMsgWithChannel(channelID, channelType);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    static java.util.List<WKMsg> querySearchMsgs(String channelID, byte channelType,
                                                 long oldestOrderSeq, int limit) {
        // SDK searchMsgWithChannelAndContentTypes 要求 contentTypes 非空；这里传一个较广的
        // 白名单（涵盖内置 1-13 + 交互式卡片 17 + 常见业务扩展 100..109）覆盖 SystemBot
        // 可能推的类型。老 SystemBot preview 只查 WK_TEXT 会错过所有非文本 bot 消息。
        // 若将来 bot 引入新 type，扩这里即可。
        int[] types = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 17,
                100, 101, 102, 103, 104, 105, 106, 107, 108, 109};
        try {
            return MsgDbManager.getInstance()
                    .searchWithChannelAndContentTypes(channelID, channelType, oldestOrderSeq, limit, types);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
