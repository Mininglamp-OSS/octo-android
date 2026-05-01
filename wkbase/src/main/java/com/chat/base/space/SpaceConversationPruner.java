package com.chat.base.space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 外部群 · Space 隔离过滤兜底清扫（YUJ-217 · 对齐 iOS YUJ-215 PR#95 Defense-in-Depth）。
 *
 * <p>对齐 iOS {@code pruneNonCurrentSpaceGroups}：在 Space 切换 / 连接后 sync / DB 回放等
 * 状态变化点做一次全量扫描，把不属于当前 Space 的群会话从 in-memory list 和白名单里剔掉。
 *
 * <p>职责边界（硬约束）：
 * <ul>
 *     <li>不改 {@link SpaceFilter} 纯函数——本类只调用它做判定，不重新实现 7 分支逻辑。</li>
 *     <li>不写 WKSDK 持久化层——只裁内存 list / 白名单 Set，DB 回放时 SpaceFilter 会自然二次校验。</li>
 *     <li>PERSONAL 频道交给 {@link SpaceFilter#shouldSkipMessageForSpace} 做消息级过滤，
 *         本类不对私聊做 channel 级剔除，避免误杀跨 Space 共享的系统 Bot（如 botfather）。</li>
 * </ul>
 *
 * <p>Pattern 来源：iOS WKConversationListVM.pruneNonCurrentSpaceGroups（PR#95）扫
 * conversationWrapModels 按 SpaceFilter Skip 踢出。Android 此前 Round-2（PR#155）在
 * 新消息路径 + 批次路径插入 SpaceFilter，但缺失「状态变化后回扫」分层，导致冷启动 race
 * 和白名单污染仍可能漏入残留 entry。Round-3 补齐这一层兜底。
 */
public final class SpaceConversationPruner {

    private SpaceConversationPruner() {
    }

    /**
     * 判定一条 {@link WKUIConversationMsg} 是否应从当前 Space 的 in-memory 列表中剔除。
     *
     * <p>规则（与 iOS 对齐）：
     * <ul>
     *     <li>currentSpaceId 为空（非 Space 模式）→ 永不剔除</li>
     *     <li>{@link WKChannelType#PERSONAL} → 永不剔除（交给消息级过滤 / SYSTEM_BOTS 兜底）</li>
     *     <li>{@link WKChannelType#GROUP} → 调 {@link SpaceFilter#shouldSkipChannelForSpace}
     *         判定；Skip == true 则剔除</li>
     *     <li>其他 channelType（COMMUNITY_TOPIC 等）不走 prune 路径（上游已过滤）</li>
     * </ul>
     */
    @VisibleForTesting
    public static boolean shouldPrune(@Nullable String channelID,
                                      byte channelType,
                                      @Nullable String currentSpaceId,
                                      @NonNull SpaceFilter.ChannelInfoProvider provider) {
        if (channelID == null || channelID.isEmpty()) return false;
        if (currentSpaceId == null || currentSpaceId.isEmpty()) return false;
        if (channelType != WKChannelType.GROUP) return false;
        return SpaceFilter.shouldSkipChannelForSpace(channelID, channelType, currentSpaceId, provider);
    }

    /**
     * 生产入口：沿用 {@link SpaceFilter} 的默认 provider + currentSpaceId。
     */
    public static boolean shouldPrune(@Nullable String channelID, byte channelType) {
        return SpaceFilter.shouldSkipChannelForSpace(channelID, channelType);
    }

    /**
     * 对一个 conversation 列表做全量扫描，返回应剔除的索引（倒序，便于原地 remove）。
     *
     * <p>纯函数，不 mutate 入参列表，便于 host-side 单元测试。
     */
    @NonNull
    public static List<Integer> collectIndicesToPrune(@Nullable List<WKUIConversationMsg> list,
                                                       @Nullable String currentSpaceId,
                                                       @NonNull SpaceFilter.ChannelInfoProvider provider) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        if (currentSpaceId == null || currentSpaceId.isEmpty()) return Collections.emptyList();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0, n = list.size(); i < n; i++) {
            WKUIConversationMsg item = list.get(i);
            if (item == null) continue;
            if (shouldPrune(item.channelID, item.channelType, currentSpaceId, provider)) {
                indices.add(i);
            }
        }
        // 倒序返回，调用方从高索引向低索引 remove 不会错位
        Collections.reverse(indices);
        return indices;
    }

    /**
     * 从白名单 Set 中剔除非当前 Space 的 channelKey（Fix D 状态变化回扫用）。
     *
     * <p>key 约定格式：{@code channelID + "_" + channelType}（与 ChatFragment#channelKey 对齐）。
     * 返回被剔除的 key 数量，供调用方日志 / 诊断。
     */
    public static int pruneWhitelist(@Nullable Set<String> whitelistKeys,
                                     @Nullable String currentSpaceId,
                                     @NonNull SpaceFilter.ChannelInfoProvider provider) {
        if (whitelistKeys == null || whitelistKeys.isEmpty()) return 0;
        if (currentSpaceId == null || currentSpaceId.isEmpty()) return 0;
        int removed = 0;
        java.util.Iterator<String> it = whitelistKeys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            ParsedKey p = parseKey(key);
            if (p == null) continue;
            if (shouldPrune(p.channelID, p.channelType, currentSpaceId, provider)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** 解析 {@code channelID_channelType} 格式的白名单 key，格式不合法返回 null。 */
    @Nullable
    @VisibleForTesting
    static ParsedKey parseKey(@Nullable String key) {
        if (key == null) return null;
        int idx = key.lastIndexOf('_');
        if (idx <= 0 || idx == key.length() - 1) return null;
        String cid = key.substring(0, idx);
        String typeStr = key.substring(idx + 1);
        try {
            byte ct = Byte.parseByte(typeStr);
            return new ParsedKey(cid, ct);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static final class ParsedKey {
        final String channelID;
        final byte channelType;

        ParsedKey(String channelID, byte channelType) {
            this.channelID = channelID;
            this.channelType = channelType;
        }
    }
}
