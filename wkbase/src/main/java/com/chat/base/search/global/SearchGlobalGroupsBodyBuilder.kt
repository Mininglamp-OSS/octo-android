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

package com.chat.base.search.global

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalGroupsReq

/**
 * 序列化 L1 请求体为 FastJSON [JSONObject]，对齐 octo-server
 * `modules/messages_search/search_global_groups.go` 的 `SearchGlobalGroupsReq` 与
 * `04-aggregation-api-spec.md` §2.1。
 *
 * 关键规则：
 *  - keyword 为空**不写入** body（触发门交给 [GlobalSearchFilters.canTriggerL1] 判断，此处不做二次校验）。
 *  - sequence 恒写入（服务端原样回带，用于 UI 层丢弃过期响应）。
 *  - filters 各字段为空/空集合时**省略**该字段，避免出现空数组导致的语义歧义。
 *  - filters 完全为空时整个 filters 字段省略。
 *  - 客户端不做长度/枚举校验，交由服务端 400 反馈以避免规则漂移。
 */
internal object SearchGlobalGroupsBodyBuilder {

    fun build(req: SearchGlobalGroupsReq): JSONObject {
        val body = JSONObject()
        if (!req.keyword.isNullOrEmpty()) {
            body["keyword"] = req.keyword
        }
        body["sequence"] = req.sequence
        req.filters?.takeUnless { it.isEmpty() }?.let { body["filters"] = filtersToJson(it) }
        return body
    }

    private fun filtersToJson(f: GlobalSearchFilters): JSONObject {
        val out = JSONObject()
        f.senderIds?.takeIf { it.isNotEmpty() }?.let { out["sender_ids"] = it }
        f.memberUids?.takeIf { it.isNotEmpty() }?.let { out["member_uids"] = it }
        f.memberUid?.takeIf { it.isNotEmpty() }?.let { out["member_uid"] = it }
        f.channelIds?.takeIf { it.isNotEmpty() }?.let { out["channel_ids"] = channelRefsToJson(it) }
        f.channelTypes?.takeIf { it.isNotEmpty() }?.let { list ->
            out["channel_types"] = list.map { it.toInt() }
        }
        f.contentTypes?.takeIf { it.isNotEmpty() }?.let { out["content_types"] = it }
        f.sentAtFrom?.takeIf { it.isNotEmpty() }?.let { out["sent_at_from"] = it }
        f.sentAtTo?.takeIf { it.isNotEmpty() }?.let { out["sent_at_to"] = it }
        return out
    }

    private fun channelRefsToJson(refs: List<GlobalChannelRef>): JSONArray {
        val arr = JSONArray()
        for (r in refs) {
            val o = JSONObject()
            o["channel_id"] = r.channelId
            o["channel_type"] = r.channelType.toInt()
            arr.add(o)
        }
        return arr
    }
}
