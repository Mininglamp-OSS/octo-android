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

package com.chat.base.msg

import com.xinbida.wukongim.msgmodel.WKTextContent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 扩展 WKTextContent，在 encodeMsg 时将 mention entities 写入 mention 对象。
 *
 * SDK 的 getSendPayload 在构建 mention JSON 前会检查 `json.has("mention")`，
 * 如果 encodeMsg 返回的 JSON 已包含 "mention"，SDK 不会覆盖。
 * 因此本类在 encodeMsg 中直接构建完整的 mention 对象（含 uids、all、entities）。
 */
class WKMentionTextContent(content: String) : WKTextContent(content) {

    override fun encodeMsg(): JSONObject {
        val json = super.encodeMsg()

        // 只有存在 mentionInfo 或 mentionAll 时才构建 mention 对象
        val hasUids = mentionInfo?.uids?.isNotEmpty() == true
        val hasMentionAll = mentionAll == 1
        if (!hasUids && !hasMentionAll) return json

        val mentionJson = JSONObject()

        if (hasMentionAll) {
            mentionJson.put("all", 1)
        }

        if (hasUids) {
            val uidsArr = JSONArray()
            for (uid in mentionInfo.uids) {
                uidsArr.put(uid)
            }
            mentionJson.put("uids", uidsArr)
        }

        // 将 type=mention 的 entity 写入 mention.entities
        val mentionEntitiesArr = MentionEntityHelper.buildMentionEntitiesJson(entities)
        if (mentionEntitiesArr != null) {
            mentionJson.put("entities", mentionEntitiesArr)
        }

        json.put("mention", mentionJson)
        return json
    }
}
