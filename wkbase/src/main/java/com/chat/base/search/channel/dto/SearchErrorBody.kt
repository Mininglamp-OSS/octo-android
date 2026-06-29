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
 * 服务端错误信封：
 * `{ "error": { "code": "...", "message": "...", "details": {...}, "hint": "...", "http_status": N } }`
 *
 * 由 [com.chat.base.search.channel.ChannelSearchErrorParser] 解析。
 */
class SearchErrorBody {
    var error: SearchErrorDetail? = null
}

class SearchErrorDetail {
    var code: String = ""
    var message: String = ""
    var details: Map<String, Any>? = null
    var hint: String? = null
    var http_status: Int = 0
}

/** 服务端固定错误码（与 octo-server/pkg/errcode/messages_search.go 对齐）。 */
object SearchErrorCode {
    const val VALIDATION_FAILED = "err.server.messages_search.validation_failed"
    const val UPSTREAM_UNAVAILABLE = "err.server.messages_search.upstream_unavailable"
    const val INTERNAL = "err.server.messages_search.internal"
    const val RATE_LIMITED = "err.server.messages_search.rate_limited"
    const val NOT_FOUND = "err.server.messages_search.not_found"
    const val DISABLED = "err.server.messages_search.disabled"
    const val DEPTH_EXCEEDED = "err.server.messages_search.depth_exceeded"
}
