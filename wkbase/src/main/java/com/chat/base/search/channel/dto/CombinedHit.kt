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
 * `/_search_all` 单条结果。message 与 file 互斥（按 result_type 判断），永远只填其中一个。
 */
class CombinedHit {
    lateinit var result_type: String           // "message" | "file"
    lateinit var sorted_at: String             // RFC3339；与所选 sort 排序键一致，用于游标
    var message: MessageHit? = null
    var file: FileHit? = null

    fun isMessage(): Boolean = result_type == TYPE_MESSAGE
    fun isFile(): Boolean = result_type == TYPE_FILE

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_FILE = "file"
    }
}
