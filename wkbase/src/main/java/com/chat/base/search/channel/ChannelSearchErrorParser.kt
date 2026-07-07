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

import com.alibaba.fastjson.JSON
import com.chat.base.search.channel.dto.SearchErrorBody

/**
 * 解析 octo-server `modules/messages_search` 的错误信封：
 * `{ "error": { "code": "err.server.messages_search.xxx", "message": "...", "details": {...}, "http_status": N } }`
 *
 * 旧的 `{ "status": N, "msg": "..." }` 形态由 [com.chat.base.net.ResponseExceptionHandle] 处理，
 * 不在此覆盖。
 */
internal object ChannelSearchErrorParser {

    data class ParsedError(
        val code: String,
        val message: String,
        val retryAfterSec: Int,
    )

    fun parse(errJson: String?): ParsedError? {
        if (errJson.isNullOrBlank()) return null
        return runCatching {
            val body = JSON.parseObject(errJson, SearchErrorBody::class.java) ?: return null
            val err = body.error ?: return null
            if (err.code.isBlank()) return null
            ParsedError(
                code = err.code,
                message = err.message,
                retryAfterSec = (err.details?.get("retry_after") as? Number)?.toInt() ?: 0,
            )
        }.getOrNull()
    }
}
