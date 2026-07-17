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
import com.chat.base.search.global.dto.SearchGlobalMessagesReq

/**
 * 序列化 L2 请求体为 FastJSON [JSONObject]，对齐 octo-server
 * `search_global_messages.go` 的 `SearchGlobalMessagesReq` 与 `04-aggregation-api-spec.md` §3。
 *
 * 关键规则（与 L1 [SearchGlobalGroupsBodyBuilder] 保持一致，避免行为漂移）：
 *  - keyword 为空**不写入** body（浏览模式：仅按 filters 排序）。
 *  - cursor 为空**不写入** body（首页请求）。
 *  - filters 各字段为空/空集合时省略；filters 完全为空整个字段省略。
 *  - sort / page_size 恒写入。
 *  - 客户端不做长度/枚举校验，交由服务端 400 反馈以避免规则漂移。
 */
internal object SearchGlobalMessagesBodyBuilder {

    fun build(req: SearchGlobalMessagesReq): JSONObject {
        val body = JSONObject()
        if (!req.keyword.isNullOrEmpty()) {
            body["keyword"] = req.keyword
        }
        req.filters.takeUnless { it.isEmpty() }?.let { body["filters"] = filtersToJson(it) }
        body["sort"] = req.sort
        body["page_size"] = req.pageSize
        if (!req.cursor.isNullOrEmpty()) body["cursor"] = req.cursor
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
