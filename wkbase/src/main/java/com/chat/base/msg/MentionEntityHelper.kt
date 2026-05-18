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

import com.chat.base.utils.WKReader
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.msgmodel.WKMsgEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 处理 mention.entities 的解析与构建。
 *
 * Bot 消息的 payload 中，mention 对象可能包含 entities 数组：
 * ```json
 * "mention": {
 *   "uids": ["uid_chen", "uid_bob"],
 *   "entities": [
 *     {"uid": "uid_chen", "offset": 3, "length": 4},
 *     {"uid": "uid_bob", "offset": 10, "length": 4}
 *   ]
 * }
 * ```
 * SDK 只解析 uids，不解析 entities，因此需要在应用层补充处理。
 */
object MentionEntityHelper {

    /**
     * 从 msg.content 原始 JSON 中提取 mention.entities，
     * 转换为 WKMsgEntity 并合并到 msg.baseContentMsgModel.entities。
     */
    @JvmStatic
    fun mergeMentionEntities(msg: WKMsg) {
        if (msg.content.isNullOrEmpty() || msg.baseContentMsgModel == null) return
        try {
            val json = JSONObject(msg.content)
            val mentionObj = json.optJSONObject("mention") ?: return
            val entitiesArr = mentionObj.optJSONArray("entities") ?: return
            if (entitiesArr.length() == 0) return

            val existing: MutableList<WKMsgEntity> =
                if (WKReader.isNotEmpty(msg.baseContentMsgModel.entities))
                    ArrayList(msg.baseContentMsgModel.entities)
                else
                    ArrayList()

            for (i in 0 until entitiesArr.length()) {
                val obj = entitiesArr.getJSONObject(i)
                val uid = obj.optString("uid", "")
                val offset = obj.optInt("offset", -1)
                val length = obj.optInt("length", 0)
                if (uid.isEmpty() || offset < 0 || length <= 0) continue

                // 避免重复：已有相同 offset+length 的 mention entity 则跳过
                val duplicate = existing.any {
                    it.type == ChatContentSpanType.mention &&
                            it.offset == offset && it.length == length
                }
                if (duplicate) continue

                val entity = WKMsgEntity()
                entity.type = ChatContentSpanType.mention
                entity.offset = offset
                entity.length = length
                entity.value = uid
                existing.add(entity)
            }

            msg.baseContentMsgModel.entities = existing
        } catch (_: Exception) {
            // JSON 解析失败时忽略，降级到原有逻辑
        }
    }

    /**
     * 从 entities 列表中提取 type=mention 的项，构建 mention.entities JSONArray。
     * 返回 null 表示没有 mention entity。
     */
    @JvmStatic
    fun buildMentionEntitiesJson(entities: List<WKMsgEntity>?): JSONArray? {
        if (entities.isNullOrEmpty()) return null
        val arr = JSONArray()
        for (entity in entities) {
            if (entity.type != ChatContentSpanType.mention) continue
            val obj = JSONObject()
            obj.put("uid", entity.value)
            obj.put("offset", entity.offset)
            obj.put("length", entity.length)
            arr.put(obj)
        }
        return if (arr.length() > 0) arr else null
    }
}
