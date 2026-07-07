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

/** 频道内文件命中。`/_search_files` 与 `/_search_all` 中 result_type=file 时使用。 */
class FileHit {
    lateinit var message_id: String
    var message_seq: Long = 0L
    lateinit var file_name: String
    var file_size_bytes: Long = 0L
    var file_ext: String = ""                  // 无前导点
    var download_url: String = ""
    var preview_url: String? = null            // 当前服务端始终 null
    lateinit var sender_id: String
    var sender_name: String? = null
    var sender_avatar_url: String? = null
    lateinit var sent_at: String               // RFC3339
}
