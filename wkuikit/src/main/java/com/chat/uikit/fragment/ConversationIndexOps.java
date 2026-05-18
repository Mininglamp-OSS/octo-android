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

package com.chat.uikit.fragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.uikit.enity.ChatConversationMsg;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  · key-based 会话去重纯函数工具（对齐 iOS {@code WKConversationListVM.channelIndex}）。
 *
 * <p>ChatFragment 的 {@code allConversations} 历来是裸 {@link java.util.ArrayList}，
 * 没有按 {@code (channelID, channelType)} 做 key 级别的唯一性约束。多处 cold-start
 * sync / 占位合成 / 单 msg 新增路径并行写入时，会出现同一 SystemBot（u_10000 /
 * botfather / fileHelper）在同一账号的多个 Space 下各插入一条的 UI 层重复，尽管
 * WKSDK DB 的 {@code UNIQUE INDEX(channel_id, channel_type)} 只保留 1 条。
 *
 * <p>本类把「列表 + 索引」的数据结构合约凝固为三个纯静态函数：{@link #upsert}、
 * {@link #removeByKey}、{@link #rebuildIndex}。所有修改 {@code allConversations}
 * 的路径都走这些函数，就能保证：
 * <ol>
 *     <li>任何时刻列表中同一 key 只有一个 entry；</li>
 *     <li>{@code conversationIndex} 和列表始终一致（没有只出现在 index 的鬼 entry，
 *         也没有未索引的 list entry）。</li>
 * </ol>
 *
 * <p>职责边界：
 * <ul>
 *     <li>不改 {@code SystemBotsFallback} / {@code ConversationPreviewFilter} /
 *         {@code SpaceFilter}；</li>
 *     <li>不触碰 WKSDK 持久化层；</li>
 *     <li>只负责「列表 + 索引」的一致性，不参与 Space 过滤 / SystemBot 兜底等业务决策。</li>
 * </ul>
 *
 * <p>与 iOS 的对照（参考）：
 * <pre>
 * // iOS WKConversationListVM.m
 * NSMutableArray *conversationWrapModels;
 * NSMutableDictionary *channelIndex;
 *
 * - (BOOL)ensureSystemBotsVisible {
 *     if ([self modelAtChannel:botfatherChannel]) return NO; // 已存在 → 直接 return
 *     ...
 * }
 * </pre>
 */
public final class ConversationIndexOps {

    private ConversationIndexOps() {
    }

    /**
     * 从 ChatConversationMsg 提取稳定的 channel key（与 ChatFragment.channelKey 同格式）。
     *
     * @return 非空 key；msg / uiConversationMsg / channelID 为空时返回 {@code null}
     */
    @Nullable
    public static String keyOf(@Nullable ChatConversationMsg msg) {
        if (msg == null || msg.uiConversationMsg == null) return null;
        String cid = msg.uiConversationMsg.channelID;
        if (cid == null || cid.isEmpty()) return null;
        return cid + "_" + msg.uiConversationMsg.channelType;
    }

    /**
     * Upsert 语义：列表已有同 key entry 时直接返回现有 entry（不插入）；不存在时
     * 追加到列表末尾并写入索引，返回新插入的 entry。
     *
     * <p>不会更新现有 entry 的字段——字段级合并由调用方处理（保留 ChatFragment
     * 已有的 {@code isResetCounter/isResetTime/…} flag 比较逻辑）。这里只负责
     * 「不让同一 key 出现两次」。
     *
     * <p>section header（{@code isSectionHeader}）没有 channelID → {@link #keyOf}
     * 返回 {@code null} → 直接 append 到列表不索引，避免影响 UI 分组渲染。
     *
     * @return 现有 entry（如果已存在）或新插入的 entry；{@code msg} 为 null 时返回 null
     */
    @Nullable
    public static ChatConversationMsg upsert(@NonNull List<ChatConversationMsg> list,
                                             @NonNull Map<String, ChatConversationMsg> index,
                                             @Nullable ChatConversationMsg msg) {
        if (msg == null) return null;
        String key = keyOf(msg);
        if (key == null) {
            // section header 或无效 channelID：直接 append，不索引（与 UI 分组渲染的
            // 一次性 section header 语义一致——sortMsg 重建时会被 drop）
            list.add(msg);
            return msg;
        }
        ChatConversationMsg existing = index.get(key);
        if (existing != null) {
            return existing;
        }
        list.add(msg);
        index.put(key, msg);
        return msg;
    }

    /**
     * 按 key 删除：从索引和列表中同时移除；列表里潜在的多余残留（历史污染）也一并清理。
     *
     * @return 至少删掉 1 条时返回 true
     */
    public static boolean removeByKey(@NonNull List<ChatConversationMsg> list,
                                      @NonNull Map<String, ChatConversationMsg> index,
                                      @Nullable String key) {
        if (key == null || key.isEmpty()) return false;
        boolean changed = index.remove(key) != null;
        // 防御性：同时扫列表扫残留（若 index 已丢失但列表还有漏网条目，历史污染场景）
        for (int i = list.size() - 1; i >= 0; i--) {
            String k = keyOf(list.get(i));
            if (k != null && k.equals(key)) {
                list.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 按 {@code (channelID, channelType)} 删除 — 等价 {@link #removeByKey} 但
     * 自动拼 key。
     */
    public static boolean removeByChannel(@NonNull List<ChatConversationMsg> list,
                                          @NonNull Map<String, ChatConversationMsg> index,
                                          @Nullable String channelID, byte channelType) {
        if (channelID == null || channelID.isEmpty()) return false;
        return removeByKey(list, index, channelID + "_" + channelType);
    }

    /**
     * 清空列表 + 索引。供 Space 切换 / Space resync / 冷启动清空路径统一调用。
     */
    public static void clearAll(@NonNull List<ChatConversationMsg> list,
                                @NonNull Map<String, ChatConversationMsg> index) {
        list.clear();
        index.clear();
    }

    /**
     * 根据当前列表重建索引。供 sortMsg 的 {@code clear + addAll} 批量替换路径、
     * 以及 {@code ensureSystemBotsVisible(allConversations)} 直接对列表追加后调用，
     * 保证索引与列表一致。
     *
     * <p>多 entry 同 key 时保留最先出现的（对齐 upsert「已存在直接 return」语义）；
     * 这种情况本身已经是 bug，日志可以在 ChatFragment 侧补。
     *
     * @return 被 drop 的重复 entry 数量（> 0 表示列表曾被污染，但已按去重策略收敛）
     */
    public static int rebuildIndex(@NonNull List<ChatConversationMsg> list,
                                   @NonNull Map<String, ChatConversationMsg> index) {
        index.clear();
        Set<String> seen = new HashSet<>();
        int droppedDuplicates = 0;
        // 正序遍历保留最先出现的 entry，对齐 upsert 的「已存在直接 return」语义。
        // 删除时不自增 i：list 左移补位后继续从当前位置检查下一条。
        int i = 0;
        while (i < list.size()) {
            ChatConversationMsg msg = list.get(i);
            String key = keyOf(msg);
            if (key == null) {
                // section header / 无效 entry：跳过，不进索引也不算 duplicate
                i++;
                continue;
            }
            if (seen.contains(key)) {
                list.remove(i);
                droppedDuplicates++;
            } else {
                seen.add(key);
                index.put(key, msg);
                i++;
            }
        }
        return droppedDuplicates;
    }
}
