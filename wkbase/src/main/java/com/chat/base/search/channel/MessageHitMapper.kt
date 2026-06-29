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

import com.chat.base.entity.GlobalChannel
import com.chat.base.entity.GlobalMessage
import com.chat.base.msgitem.WKContentType
import com.chat.base.search.channel.dto.MessageHit

/**
 * 把服务端 [MessageHit] 适配成现有 `SearchMessageAdapter` 消费的 [GlobalMessage]。
 * snippet 已带 `<mark>…</mark>` 高亮，adapter 内部识别 HTML 后渲染紫色。
 *
 * 由于服务端只回 sender_id（不带 sender_name / channel_name 等聊天框需要的元信息），
 * 调用方负责传入本地解析后的 channelName。
 */
fun MessageHit.toGlobalMessage(
    channelID: String,
    channelType: Byte,
    channelName: String,
): GlobalMessage {
    val gm = GlobalMessage()
    gm.message_seq = message_seq
    gm.from_uid = sender_id
    gm.timestamp = Rfc3339.toEpochSeconds(sent_at)
    val payload = HashMap<String, Any>()
    payload["type"] = WKContentType.WK_TEXT
    payload["content"] = snippet
    gm.payload = payload
    gm.channel = GlobalChannel().apply {
        channel_id = channelID
        channel_type = channelType
        channel_name = channelName
    }
    return gm
}
