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

package com.chat.uikit.search.remote.filter

import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters

/**
 * 筛选页编辑中的可变状态。[toFilters] 转换为提交给搜索接口的不可变 [GlobalSearchFilters]。
 *
 * [sortDescending] 对应 iOS `WKChannelHistorySearchFilter.sort`。它**不属于 filters**：
 * iOS 把它作为请求体顶层字段 `sort: "time_asc"|"time_desc"` 单独下发，且只有 L2
 * `_search_global_messages` 消费——L1 `_search_global_groups` 与 `_search_global_files`
 * 都不发送。因此它不参与 [toFilters]，而是经 [GlobalSearchFilters.sortTimeAsc] 透传给下钻页。
 *
 * [memberUid] 是 legacy 单选兼容字段，筛选页无对应 UI，但必须原样透传——否则任何
 * "打开筛选页→应用"的往返都会把调用方已设置的该字段静默清空。
 */
class FilterDraft(initial: GlobalSearchFilters) {
    var senderIds: MutableList<String> = initial.senderIds.orEmpty().toMutableList()
    var memberUids: MutableList<String> = initial.memberUids.orEmpty().toMutableList()
    var channelIds: MutableList<GlobalChannelRef> = initial.channelIds.orEmpty().toMutableList()
    // LinkedHashSet：保留插入顺序，与 iOS NSMutableArray + NSMutableOrderedSet 去重后的顺序语义一致。
    var channelTypes: MutableSet<Byte> = LinkedHashSet(initial.channelTypes.orEmpty())
    var contentTypes: MutableSet<Int> = LinkedHashSet(initial.contentTypes.orEmpty())
    var fileExts: MutableSet<String> = LinkedHashSet(initial.fileExts.orEmpty())
    var sentAtFrom: String? = initial.sentAtFrom
    var sentAtTo: String? = initial.sentAtTo
    var sortDescending: Boolean = !initial.sortTimeAsc

    /** 筛选页不编辑，仅透传。 */
    private val memberUid: String? = initial.memberUid

    fun toFilters(): GlobalSearchFilters = GlobalSearchFilters(
        senderIds = senderIds.ifEmpty { null },
        memberUids = memberUids.ifEmpty { null },
        memberUid = memberUid,
        channelIds = channelIds.ifEmpty { null },
        channelTypes = channelTypes.toList().ifEmpty { null },
        contentTypes = contentTypes.toList().ifEmpty { null },
        sentAtFrom = sentAtFrom?.takeIf { it.isNotEmpty() },
        sentAtTo = sentAtTo?.takeIf { it.isNotEmpty() },
        fileExts = fileExts.toList().ifEmpty { null },
        sortTimeAsc = !sortDescending,
    )

    fun reset() {
        senderIds.clear()
        memberUids.clear()
        channelIds.clear()
        channelTypes.clear()
        contentTypes.clear()
        fileExts.clear()
        sentAtFrom = null
        sentAtTo = null
        sortDescending = true
    }

    /**
     * "单聊"/"群聊" 两行各自是**独立可取消的开关**（不是单选），语义与取值逐行对齐 iOS
     * `WKChannelHistoryFilterVC.toggleChatTypeRow:`：
     *  - 单聊 = `[1]`，异或切换；
     *  - 群聊 = `[2, 5]` 原子对（子区随群），只要 2 或 5 任一存在即视为选中，取消时两者一起移除；
     *  - 两行都不选 → `channelTypes` 为空 → 序列化时整个 `channel_types` 字段省略 = 不限；
     *  - 两行都选 → `[1, 2, 5]`，chip 展示"单聊+群聊"。
     */
    fun toggleChatTypeSingle() {
        if (channelTypes.contains(SINGLE)) channelTypes.remove(SINGLE) else channelTypes.add(SINGLE)
    }

    fun toggleChatTypeGroup() {
        val on = isChatTypeGroupSelected()
        channelTypes.removeAll(GROUP_TYPES)
        if (!on) channelTypes.addAll(GROUP_TYPES)
    }

    fun isChatTypeSingleSelected(): Boolean = channelTypes.contains(SINGLE)

    /** 群聊选中判定与 iOS 一致：2 或 5 任一存在即为选中（容忍服务端/历史数据只给其中一个）。 */
    fun isChatTypeGroupSelected(): Boolean = channelTypes.any { it in GROUP_TYPES }

    companion object {
        private const val SINGLE: Byte = 1
        private val GROUP_TYPES = setOf<Byte>(2, 5)
    }
}
