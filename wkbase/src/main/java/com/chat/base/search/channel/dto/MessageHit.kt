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

import android.text.TextUtils

/**
 * 单条文本/转发消息搜索命中。`/_search` 与 `/_search_all` 中 result_type=message 时使用。
 */
class MessageHit {
    lateinit var message_id: String
    var message_seq: Long = 0L
    lateinit var message_kind: String          // "text" | "forward"
    var snippet: String = ""                   // 已带 <mark>…</mark> 高亮的片段，可能为空
    lateinit var sender_id: String
    var sender_name: String? = null
    var sender_avatar_url: String? = null
    lateinit var sent_at: String               // RFC3339
    var outer_preview: OuterPreview? = null
    var inner_messages: List<InnerMessage>? = null
    var channel_id: String = ""
    // 全局搜索 preview 场景由服务端回填（`_search_global_groups`/`_search_global_messages`）。
    // 频道内 `_search`/`_search_all` 响应中不携带，保留默认 0。
    var channel_type: Byte = 0

    /** 服务端 snippet 内 <mark> 标签替换为客户端紫色 font，与 GlobalMessage.getHtmlText 同色规则。 */
    fun getHighlightedHtml(): String {
        if (TextUtils.isEmpty(snippet)) return ""
        return snippet.replace("<mark>", "<font color=#7761F4>")
            .replace("</mark>", "</font>")
    }

    fun isForward(): Boolean = message_kind == "forward"
}

class OuterPreview {
    var child_count: Int = 0
}

class InnerMessage {
    lateinit var message_id: String
    var type: Int = 0
    var search_text: String? = null
    var sender_id: String? = null
    var sender_name: String? = null
    var sent_at: String? = null
}
