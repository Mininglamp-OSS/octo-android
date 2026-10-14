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

package com.chat.base.search.global.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 全局搜索通用 filters，语义等同 octo-server `modules/messages_search` 的
 * `_search_global_messages` / `_search_global_groups`。
 *
 * 服务端约束（详见 `04-aggregation-api-spec.md` §2.1 与 `global-search-api.md`）：
 *  - senderIds / memberUids ≤ 50 个
 *  - channelTypes ⊆ {1(DM), 2(Group), 5(Thread)}
 *  - contentTypes ⊆ {1(text), 2(image), 5(video), 8(file), 11(forward), 14(richtext)}
 *  - sentAtFrom / sentAtTo 支持 YYYY-MM-DD 或 RFC3339；from ≤ to
 */
@Parcelize
class GlobalSearchFilters(
    val senderIds: List<String>? = null,
    val memberUids: List<String>? = null,
    /** legacy 单选兼容字段，一般用 [memberUids]。 */
    val memberUid: String? = null,
    /** 指定群/子区/DM。key=channel_id，type∈{1,2,5}。L1 一般不传。 */
    val channelIds: List<GlobalChannelRef>? = null,
    val channelTypes: List<Byte>? = null,
    val contentTypes: List<Int>? = null,
    val sentAtFrom: String? = null,
    val sentAtTo: String? = null,
    /** 文件 tab 专用：扩展名小写、无前导点（如 "pdf"）。L1/L2 消息搜索不使用。 */
    val fileExts: List<String>? = null,
    /**
     * 时间正序（最早在前）。**不是 filters 的一部分**——对齐 iOS
     * `WKChannelHistorySearchFilter.sort`：序列化时作为请求体**顶层** `sort` 字段下发
     * （`"time_asc"` / `"time_desc"`），且仅 L2 `_search_global_messages` 消费；
     * L1 `_search_global_groups` 与 `_search_global_files` 都不发送该字段。
     *
     * 因此它既不进 [toApiDict] 风格的 filters JSON，也不计入 [isEmpty]
     * （对齐 iOS `hasEffectiveFilters` 排除 sort、仅 `hasAnyFilter` 包含它的区分）。
     */
    val sortTimeAsc: Boolean = false,
) : Parcelable {
    /**
     * 是否不含任何**实质筛选条件**，对齐 iOS `hasEffectiveFilters`（取反）。
     * 刻意不计入 [sortTimeAsc]：排序不是筛选，单独改排序不应让「浏览态」被判定为有筛选。
     */
    fun isEmpty(): Boolean =
        senderIds.isNullOrEmpty() &&
            memberUids.isNullOrEmpty() &&
            memberUid.isNullOrEmpty() &&
            channelIds.isNullOrEmpty() &&
            channelTypes.isNullOrEmpty() &&
            contentTypes.isNullOrEmpty() &&
            sentAtFrom.isNullOrEmpty() &&
            sentAtTo.isNullOrEmpty() &&
            fileExts.isNullOrEmpty()

    /** L1 触发条件：至少一项非空（见 §2.3），否则服务端 400 VALIDATION_ERROR。 */
    fun canTriggerL1(hasKeyword: Boolean): Boolean =
        hasKeyword ||
            !senderIds.isNullOrEmpty() ||
            !memberUids.isNullOrEmpty() ||
            !memberUid.isNullOrEmpty() ||
            !channelIds.isNullOrEmpty()

    /**
     * 保留其余所有字段，仅覆盖 [channelIds]。
     *
     * 用于 L2 下钻场景：L1 筛选条件（时间/发送人/文件类型等）需继续生效，
     * 同时追加"限定在该群/子区/DM"的约束——两者是 AND 关系，不是互斥替换
     * （对齐 iOS WKGlobalSearchV2API 的 `filter.toApiDict + channel_ids` 合并方式）。
     */
    fun withChannelIds(channelIds: List<GlobalChannelRef>): GlobalSearchFilters =
        GlobalSearchFilters(
            senderIds = senderIds,
            memberUids = memberUids,
            memberUid = memberUid,
            channelIds = channelIds,
            channelTypes = channelTypes,
            contentTypes = contentTypes,
            sentAtFrom = sentAtFrom,
            sentAtTo = sentAtTo,
            fileExts = fileExts,
            sortTimeAsc = sortTimeAsc,
        )
}

@Parcelize
class GlobalChannelRef(
    val channelId: String,
    val channelType: Byte,
) : Parcelable
