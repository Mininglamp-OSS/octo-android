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

import com.chat.base.search.channel.dto.MessageHit

/**
 * L1 聚合桶。一个桶 = 一个群 / 一个子区 / 一个 DM。
 *
 * 语义（见 `04-aggregation-api-spec.md` §2.4）：
 *  - presence 精确（K2=500 内 100% 精确）：列出的桶点进 L2 至少 1 条可见，不会"点进去空"
 *  - [match_count] / [match_count_approx]=true：pre-visibility 近似，UI 显示 "约 N 条"
 *  - [preview] 每条已做可见性过滤，可直接渲染，[MessageHit.snippet] 已带 `<mark>` 高亮
 *  - DM 桶：channel_type=1，group_name 为对端用户名，无 parent_group_no / thread_*
 *  - 子区桶：channel_type=5，parent_group_no + thread_id + thread_name 均非空
 */
class GroupBucket {
    // 服务端契约保证非空，但客户端不用 lateinit：漏字段场景 lateinit 会抛
    // UninitializedPropertyAccessException 崩 UI 层；给默认空串让下游 `bucketToDataVO`
    // 走 fallback 分支（`channel_id.ifEmpty { ... }` / adapter 校验），可控降级。
    var channel_id: String = ""
    var channel_type: Byte = 0
    /** 群/子区共父群号；DM 桶为空。 */
    var parent_group_no: String? = null
    var group_name: String = ""
    /** 仅子区桶（channel_type=5）非空。 */
    var thread_id: String? = null
    var thread_name: String? = null
    var match_count: Long = 0L
    var match_count_approx: Boolean = true
    /** RFC3339。基于可见 hit 重算，不会泄露隐藏消息时间。 */
    var latest_at: String = ""
    var preview: List<MessageHit> = emptyList()
}
