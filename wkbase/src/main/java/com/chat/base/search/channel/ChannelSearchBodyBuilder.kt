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

package com.chat.base.search.channel

import com.alibaba.fastjson.JSONObject
import com.chat.base.search.channel.dto.AroundRequest
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.dto.SearchFilters

/**
 * 把强类型请求结构序列化为 FastJSON [JSONObject]，对齐 octo-server `modules/messages_search/validate.go`。
 *
 * - keyword、cursor 为空时**不写入** body，避免被服务端 `keywordOrFilterRequired` 校验误判（[ChannelSearchReq]
 *   允许 keyword 为空 + filters 非空的"纯过滤"模式）。
 * - SearchFilters 完全为空时整个 filters 字段省略。
 * - 客户端不进行二次校验（如 keyword 长度），交由服务端 400 反馈，避免与服务端规则漂移。
 */
internal object ChannelSearchBodyBuilder {

    fun build(req: ChannelSearchReq, allowKeyword: Boolean = true): JSONObject {
        val body = JSONObject()
        body["channel_type"] = req.channelType.toInt()
        body["channel_id"] = req.channelId
        if (allowKeyword && !req.keyword.isNullOrEmpty()) {
            body["keyword"] = req.keyword
        }
        req.filters?.takeUnless { it.isEmpty() }?.let { body["filters"] = filtersToJson(it) }
        body["sort"] = req.sort
        body["page_size"] = req.pageSize
        if (!req.cursor.isNullOrEmpty()) body["cursor"] = req.cursor
        return body
    }

    fun buildAround(req: AroundRequest): JSONObject {
        val body = JSONObject()
        body["channel_type"] = req.channelType.toInt()
        body["channel_id"] = req.channelId
        body["anchor_message_id"] = req.anchorMessageId
        req.filters?.takeUnless { it.isEmpty() }?.let { body["filters"] = filtersToJson(it) }
        body["page_size"] = req.pageSize
        return body
    }

    private fun filtersToJson(f: SearchFilters): JSONObject {
        val out = JSONObject()
        f.senderIds?.takeIf { it.isNotEmpty() }?.let { out["sender_ids"] = it }
        f.sentAtFrom?.takeIf { it.isNotEmpty() }?.let { out["sent_at_from"] = it }
        f.sentAtTo?.takeIf { it.isNotEmpty() }?.let { out["sent_at_to"] = it }
        return out
    }
}
