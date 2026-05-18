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

package com.chat.base.entity


import android.text.TextUtils
import com.chat.base.R
import com.chat.base.WKBaseApplication
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.msgmodel.WKMessageContent
import org.json.JSONObject


class GlobalMessage {
    var setting: Int = 0
    lateinit var message_idstr: String
    var message_seq: Long = 0
    lateinit var client_msg_no: String
    lateinit var from_uid: String
    var timestamp: Long = 0L
    var is_deleted: Int = 0
    lateinit var channel: GlobalChannel
    lateinit var from_channel: GlobalChannel
    lateinit var payload: Map<String, Any>
    private var messageContent: WKMessageContent? = null

    fun getMessageModel(): WKMessageContent? {
        if (messageContent == null) {
            val jsonObject = JSONObject(payload)
            messageContent = WKIM.getInstance().msgManager.getMsgContentModel(jsonObject)
        }
        return messageContent
    }

    fun getContentType(): Int {
        val type = payload["type"]
        if (type is Int) {
            return type
        }
        return 0
    }


    fun getHtmlText(): String {
        val content = getMessageModel()?.content
        if (!TextUtils.isEmpty(content)) {
            return content!!.replace("<mark>", "<font color=#7761F4>")
                .replace("</mark>", "</font>")
        }
        return ""
    }

    fun getHtmlWithField(field: String): String {
        val content = payload[field]
        if (content is String && !TextUtils.isEmpty(content)) {
            return WKBaseApplication.getInstance().application.getString(R.string.last_message_file) + " " + content.replace(
                "<mark>",
                "<font color=#7761F4>"
            )
                .replace("</mark>", "</font>")
        }
        return ""
    }
}