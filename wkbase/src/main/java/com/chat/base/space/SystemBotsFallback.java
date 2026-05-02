package com.chat.base.space;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.entity.WKAPPConfig;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统 Bot（botfather / u_10000 / fileHelper 等）本地兜底（YUJ-217 · Fix C · 对齐 iOS YUJ-215 思路；
 * YUJ-219 · A3 扩展为读取 appconfig {@code system_bot_uids} 动态白名单）。
 *
 * <p>背景：系统 Bot 是跨 Space 共享的 PERSONAL 频道，后端 sync 可能在某些 Space 下不返回其
 * conversation entry（时序 / 索引问题），导致在该 Space 下对应 Bot 完全不显示。同时消息级
 * 过滤 / prune 逻辑需要识别这些跨 Space Bot 以便正确放行。
 *
 * <p>方案：
 * <ul>
 *     <li>本地合成占位：不写入 WKSDK cache，也不触碰 SDK 持久化层——只在 Fragment 的 in-memory
 *         会话列表中，若缺失系统 Bot 则本地合成一条最简 {@link WKUIConversationMsg} 占位。</li>
 *     <li>白名单来源：优先读 {@link WKAPPConfig#system_bot_uids}（后端下发，与 web / iOS 对齐），
 *         appconfig 未返回时走本地 fallback {@link #FALLBACK_SYSTEM_BOT_IDS}。</li>
 * </ul>
 *
 * <p>硬约束（与 iOS 对齐）：
 * <ul>
 *     <li>只影响展示，不调 {@code WKIM.getConversationManager().addOrUpdate()}</li>
 *     <li>不写 subscriber / channelInfo，避免污染真正的后端同步结果</li>
 *     <li>一旦后端下发真实 entry，{@link WKUIConversationMsg#channelID} 匹配即合并（调用方职责）</li>
 * </ul>
 */
public final class SystemBotsFallback {

    /**
     * appconfig 未返回 {@code system_bot_uids} 时的本地 fallback 白名单。
     *
     * <p>包含：
     * <ul>
     *     <li>{@code botfather} — 引导 / 命令 Bot</li>
     *     <li>{@code u_10000} — 系统团队账号（{@link WKSystemAccount#system_team}）</li>
     *     <li>{@code fileHelper} — 文件助手（{@link WKSystemAccount#system_file_helper}）</li>
     * </ul>
     */
    public static final Set<String> FALLBACK_SYSTEM_BOT_IDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    "botfather",
                    WKSystemAccount.system_team,
                    WKSystemAccount.system_file_helper)));

    /**
     * @deprecated 历史 API。保留但语义变更为"当前生效的系统 Bot 白名单"，等价于
     *     {@link #getSystemBotIds()}。新代码请直接调用 {@link #getSystemBotIds()} 或
     *     {@link #isSystemBot(String)}。
     */
    @Deprecated
    public static final Set<String> SYSTEM_BOT_IDS = FALLBACK_SYSTEM_BOT_IDS;

    /** 缓存：最近一次解析出的动态白名单（nullable，null 表示需要重新读 appconfig）。 */
    private static volatile Set<String> cachedSystemBotIds = null;

    private SystemBotsFallback() {
    }

    /**
     * 返回当前生效的系统 Bot 白名单：优先 appconfig {@code system_bot_uids}，缺失则 fallback。
     *
     * <p>线程安全：{@link #cachedSystemBotIds} 为 volatile，写入新不可变 {@link Set}；
     * 并发读仅返回引用不修改内容。{@link WKConfig#saveAppConfig} 写入时会调
     * {@link #invalidateCache()} 清缓存。
     */
    @NonNull
    public static Set<String> getSystemBotIds() {
        Set<String> cached = cachedSystemBotIds;
        if (cached != null) return cached;
        return reloadFromAppConfig();
    }

    /** 清缓存，下次 {@link #getSystemBotIds()} 重新从 appconfig 读取。 */
    public static void invalidateCache() {
        cachedSystemBotIds = null;
    }

    @NonNull
    private static synchronized Set<String> reloadFromAppConfig() {
        Set<String> cached = cachedSystemBotIds;
        if (cached != null) return cached;
        Set<String> resolved = FALLBACK_SYSTEM_BOT_IDS;
        try {
            WKAPPConfig cfg = WKConfig.getInstance().getAppConfig();
            List<String> remote = cfg == null ? null : cfg.system_bot_uids;
            if (remote != null && !remote.isEmpty()) {
                Set<String> ids = new LinkedHashSet<>();
                for (String s : remote) {
                    if (!TextUtils.isEmpty(s)) ids.add(s);
                }
                if (!ids.isEmpty()) {
                    resolved = Collections.unmodifiableSet(ids);
                }
            }
        } catch (Throwable ignored) {
            // WKConfig 未初始化（测试 / 冷启动早期） → 走 fallback
        }
        cachedSystemBotIds = resolved;
        return resolved;
    }

    /**
     * 计算当前 in-memory conversation list 缺失哪些系统 Bot（用 {@code channelID + "_" + channelType}
     * 作 key 跟 whitelist 对齐）。
     *
     * @param existingChannelKeys 现有列表的 channelKey 集合（格式：channelID_channelType）
     * @return 缺失的 bot channelID（未按 channelType 扩展，PERSONAL 单一假设）
     */
    @NonNull
    public static Set<String> findMissingBotIds(@Nullable Collection<String> existingChannelKeys) {
        Set<String> missing = new HashSet<>();
        Set<String> existingIds = new HashSet<>();
        if (existingChannelKeys != null) {
            for (String key : existingChannelKeys) {
                if (key == null) continue;
                int idx = key.lastIndexOf('_');
                String cid = idx > 0 ? key.substring(0, idx) : key;
                existingIds.add(cid);
            }
        }
        for (String botId : getSystemBotIds()) {
            if (!existingIds.contains(botId)) {
                missing.add(botId);
            }
        }
        return missing;
    }

    /**
     * 为缺失的系统 Bot 合成最简 {@link WKUIConversationMsg}。
     *
     * <p>字段设定：
     * <ul>
     *     <li>{@code channelID = botId}, {@code channelType = PERSONAL}</li>
     *     <li>{@code lastMsgTimestamp = 0} — 排序沉底，不抢占真实会话</li>
     *     <li>{@code unreadCount = 0}</li>
     *     <li>{@code wkMsg = null} — UI 层 {@code getContent(null)} 返回空串</li>
     * </ul>
     */
    @NonNull
    public static WKUIConversationMsg buildPlaceholder(@NonNull String botId) {
        WKUIConversationMsg msg = new WKUIConversationMsg();
        msg.channelID = botId;
        msg.channelType = WKChannelType.PERSONAL;
        msg.lastMsgTimestamp = 0;
        msg.unreadCount = 0;
        msg.clientMsgNo = "";
        msg.lastMsgSeq = 0;
        msg.isDeleted = 0;
        return msg;
    }

    /**
     * 便捷批量合成：给定现有 key 集合，返回需要追加到列表的占位 {@link WKUIConversationMsg}。
     * 纯函数，便于 host-side 测试。
     */
    @NonNull
    public static List<WKUIConversationMsg> synthesizeMissing(@Nullable Collection<String> existingChannelKeys) {
        Set<String> missing = findMissingBotIds(existingChannelKeys);
        if (missing.isEmpty()) return Collections.emptyList();
        List<WKUIConversationMsg> out = new ArrayList<>(missing.size());
        for (String botId : missing) {
            out.add(buildPlaceholder(botId));
        }
        return out;
    }

    /** 给定 channelID 是否是系统 Bot（供 prune / filter 分支判定调用）。 */
    public static boolean isSystemBot(@Nullable String channelID) {
        return !TextUtils.isEmpty(channelID) && getSystemBotIds().contains(channelID);
    }

    /** 测试钩子：重建 {@code channelID_channelType} 格式的 key，与 ChatFragment 对齐。 */
    @VisibleForTesting
    @NonNull
    static String channelKey(@NonNull String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }

    /** 测试钩子：手动注入白名单（仅供单测使用）。传 null 表示恢复 appconfig 读取。 */
    @VisibleForTesting
    static void overrideSystemBotIdsForTest(@Nullable Set<String> ids) {
        cachedSystemBotIds = ids == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }
}
