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
) {
    fun isEmpty(): Boolean =
        senderIds.isNullOrEmpty() &&
            memberUids.isNullOrEmpty() &&
            memberUid.isNullOrEmpty() &&
            channelIds.isNullOrEmpty() &&
            channelTypes.isNullOrEmpty() &&
            contentTypes.isNullOrEmpty() &&
            sentAtFrom.isNullOrEmpty() &&
            sentAtTo.isNullOrEmpty()

    /** L1 触发条件：至少一项非空（见 §2.3），否则服务端 400 VALIDATION_ERROR。 */
    fun canTriggerL1(hasKeyword: Boolean): Boolean =
        hasKeyword ||
            !senderIds.isNullOrEmpty() ||
            !memberUids.isNullOrEmpty() ||
            !memberUid.isNullOrEmpty() ||
            !channelIds.isNullOrEmpty()
}

class GlobalChannelRef(
    val channelId: String,
    val channelType: Byte,
)
