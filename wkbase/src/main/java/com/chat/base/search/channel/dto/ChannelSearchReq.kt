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

package com.chat.base.search.channel.dto

/**
 * 通用请求体，覆盖 _search / _search_all / _search_media / _search_files 四个端点。
 * _search_around 走 [AroundRequest]。
 *
 * 服务端约束：
 *  - keyword ≤ 64 UTF-8 码点；_search_media 必须为空。
 *  - sender_ids ≤ 50 个，会自动剔除空串。
 *  - sent_at_* 支持 YYYY-MM-DD 或 RFC3339；from ≤ to。
 *  - page_size ∈ [1, 100]，默认 20。
 *  - cursor 为服务端 HMAC 签名的不透明字符串，客户端禁止解析或拼接。
 */
class ChannelSearchReq(
    val channelType: Byte,
    val channelId: String,
    val keyword: String? = null,
    val filters: SearchFilters? = null,
    val sort: String = SORT_TIME_DESC,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_KEYWORD_RUNES = 64
        const val MAX_SENDER_IDS = 50

        const val SORT_TIME_DESC = "time_desc"
        const val SORT_TIME_ASC = "time_asc"
        const val SORT_RELEVANCE = "relevance"
    }
}
