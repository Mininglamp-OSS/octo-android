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

/** 频道内图片/视频命中。`/_search_media` 返回。 */
class MediaHit {
    lateinit var message_id: String
    var message_seq: Long = 0L
    lateinit var media_kind: String            // "image" | "video"
    var thumb_url: String = ""
    var width: Int = 0
    var height: Int = 0
    var duration_ms: Long = 0L                 // video 才有
    lateinit var sender_id: String
    var sender_name: String? = null
    lateinit var sent_at: String               // RFC3339
    lateinit var month_bucket: String          // YYYY-MM，UI 用于 sticky header 分组

    fun isVideo(): Boolean = media_kind == "video"
}
