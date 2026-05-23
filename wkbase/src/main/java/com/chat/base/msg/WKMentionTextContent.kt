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

        // 三态 mention：mention.humans=1 / mention.ais=1 与 legacy mention.all=1、mention.uids 并存
        val hasUids = mentionInfo?.uids?.isNotEmpty() == true
        val hasMentionAll = mentionAll == 1
        val hasHumans = mentionHumans == 1 || mentionInfo?.humans == true
        val hasAis = mentionAis == 1 || mentionInfo?.ais == true
        if (!hasUids && !hasMentionAll && !hasHumans && !hasAis) return json

        val mentionJson = JSONObject()

        if (hasMentionAll) {
            mentionJson.put("all", 1)
        }
        // 三态 mention：humans=1（@所有人 新协议）/ ais=1（@所有AI），可与 all / uids 并存。
        // 单一来源原则：mentionInfo BOOL 与 WKMessageContent int 任一被置位即写出，
        // 对齐 iOS PR#128 round 3 教训（避免上层只设 enum 而忘了 sync 标志位时丢失）。
        if (hasHumans) {
            mentionJson.put("humans", 1)
        }
        if (hasAis) {
            mentionJson.put("ais", 1)
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
