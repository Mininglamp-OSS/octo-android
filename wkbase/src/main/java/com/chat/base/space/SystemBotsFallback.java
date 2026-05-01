package com.chat.base.space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统 Bot（botfather 等）本地兜底（YUJ-217 · Fix C · 对齐 iOS YUJ-215 思路）。
 *
 * <p>背景：botfather 是跨 Space 共享的系统 Bot，后端 sync 可能在某些 Space 下不返回其
 * conversation entry（时序 / 索引问题），导致在该 Space 下 botfather 完全不显示。
 *
 * <p>方案：不写入 WKSDK cache，也不触碰 SDK 持久化层——只在 Fragment 的 in-memory
 * 会话列表中，若缺失 botfather 则本地合成一条最简 {@link WKUIConversationMsg} 占位，
 * 内容 / 未读数 / 时间戳由既有 UI 层兜底（{@code getContent(null)} 已安全返回空串）。
 *
 * <p>硬约束（与 iOS 对齐）：
 * <ul>
 *     <li>只影响展示，不调 {@code WKIM.getConversationManager().addOrUpdate()}</li>
 *     <li>不写 subscriber / channelInfo，避免污染真正的后端同步结果</li>
 *     <li>一旦后端下发真实 entry，{@link WKUIConversationMsg#channelID} 匹配即合并（调用方职责）</li>
 * </ul>
 */
public final class SystemBotsFallback {

    /** 需要跨 Space 本地兜底的系统 Bot channelID 集合。 */
    public static final Set<String> SYSTEM_BOT_IDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("botfather")));

    private SystemBotsFallback() {
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
        for (String botId : SYSTEM_BOT_IDS) {
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
        return channelID != null && SYSTEM_BOT_IDS.contains(channelID);
    }

    /** 测试钩子：重建 {@code channelID_channelType} 格式的 key，与 ChatFragment 对齐。 */
    @VisibleForTesting
    @NonNull
    static String channelKey(@NonNull String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }
}
