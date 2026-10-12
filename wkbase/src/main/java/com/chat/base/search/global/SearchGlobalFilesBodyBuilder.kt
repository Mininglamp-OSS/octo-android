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
import com.chat.base.search.global.dto.SearchGlobalFilesReq

/**
 * 序列化文件搜索请求体为 FastJSON [JSONObject]。
 *
 * 关键规则（对齐 iOS `WKGlobalSearchV2API +searchGlobalFilesWithKeyword:filter:cursor:`）：
 *  - keyword 为空**不写入** body（浏览模式：仅按 filters 过滤）；不发 `"keyword": ""`。
 *  - page_size / cursor / filters **恒写入**：cursor 首页为 `""`，filters 全空为 `{}`。
 *    这与 L1 [SearchGlobalGroupsBodyBuilder]「filters 全空则省略」的规则不同——文件端点
 *    iOS 侧无条件下发这两个字段，保持一致以免服务端两套解析分支行为漂移。
 *  - filters 内部各字段为空/空集合时省略该字段（避免空数组的语义歧义）。
 *  - file_exts 小写、无前导点，由 [GlobalSearchFilters.fileExts] 保证。
 *  - 无 sort 字段：文件端点 iOS 不发送排序参数。
 *  - 客户端不做长度/枚举校验，交由服务端反馈以避免规则漂移。
 */
internal object SearchGlobalFilesBodyBuilder {

    fun build(req: SearchGlobalFilesReq): JSONObject {
        val body = JSONObject()
        // keyword 为空时整个 key 省略（浏览态），不能发 "keyword": ""
        // ——对齐 iOS WKGlobalSearchV2API.searchGlobalFilesWithKeyword 的 `if (kw.length > 0)`。
        if (!req.keyword.isNullOrEmpty()) {
            body["keyword"] = req.keyword
        }
        body["page_size"] = req.pageSize
        // cursor 恒写入：首页为 ""（不是省略、也不是 null），对齐 iOS
        // `body[@"cursor"] = cursor.length > 0 ? cursor : @""`。
        body["cursor"] = req.cursor.orEmpty()
        // filters 恒写入：全空时为 {}（不是省略），对齐 iOS
        // `body[@"filters"] = filters`（filter 为 nil 时取 @{}）。
        body["filters"] = filtersToJson(req.filters)
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
        f.fileExts?.takeIf { it.isNotEmpty() }?.let { out["file_exts"] = it }
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
