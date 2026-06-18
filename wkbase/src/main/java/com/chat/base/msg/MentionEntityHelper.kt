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
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
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
     * 跨端兜底：用 `mentionInfo.uids` + `plain` 文本权威重建 mention entities。
     *
     * 背景：iOS 发送 RichText 时把 `mention.entities[i].offset` 写成 **caption-relative**
     * （`entity.range.location` 是在 caption 内 match 出来的），而 Android 接收侧
     * `WKRichTextProvider` 按 **plain-relative**（含 `[图片]` 占位符）过滤 entity 落到哪个
     * text block。两边参考系不一致 → iOS 发的 entities offset 全部落在 Android 侧 block 范围
     * 之外 → @ 不高亮。
     *
     * iOS 接收侧自己绕开了这个问题（`WKRichTextCell.appendTextBlock:` 直接走
     * `WKMentionService.parseMention(text, mentionInfo)` re-parse @ 模式，根本不读 wire entities）。
     * 这里把 Android 接收侧也对齐这个口径：**有 mentionInfo.uids 就权威重建 mention entities**，
     * 不再信 wire 上的 offset/length（它可能是 caption-relative 的）。
     *
     * 同时也覆盖 wire 上没写 entities 的旧消息（只有 uids 没 entities 的情况）。
     *
     * 算法（与发送侧 `scanPlainTextMentions` 镜像）：
     *  - 跳过哨兵 uid（"-1" / "-2"）, 它们由渲染侧 `applyBroadcastHighlight` 单独处理
     *  - 子区（COMMUNITY_TOPIC）按父群成员表查询
     *  - 对每个 real uid: 拿 channelMember (memberName/Remark) 与 PERSONAL channel
     *    (channelName/Remark) 的 4 个候选名, 在 plain 里逐个 indexOf 找 `@<name>` 首个
     *    未被占用的位置, 命中即追加 entity (offset/length plain-relative)
     *  - 命中失败（人退群 / 改名）→ skip, 不构造伪造 entity
     *
     * 安全性：
     *  - mentionInfo 来自后端权威，绝不凭空伪造高亮
     *  - 替换原有 type=mention entities, 但保留其它类型（link 等）不动
     *  - 同一 uid 在 uids 里出现 N 次, 在 plain 里按出现顺序消费 N 个 `@<name>` 位置
     */
    @JvmStatic
    fun reconstructMentionEntitiesFromPlain(msg: WKMsg) {
        val model = msg.baseContentMsgModel ?: return
        val mInfo = model.mentionInfo ?: return
        val uidsRaw = mInfo.uids ?: return
        if (uidsRaw.isEmpty()) return

        // 过滤哨兵, 只对真实用户 uid 重建
        val realUids = uidsRaw.filter { uid -> uid != "-1" && uid != "-2" }
        if (realUids.isEmpty()) return

        val plain = model.displayContent ?: return
        if (plain.isEmpty()) return

        // 子区 → 查父群成员; 否则用消息所在 channel
        var lookupChannelId = msg.channelID
        var lookupChannelType = msg.channelType
        if (lookupChannelType == WKChannelType.COMMUNITY_TOPIC) {
            val parentGroupNo = WKIM.getInstance().channelManager
                .getChannel(msg.channelID, msg.channelType)
                ?.remoteExtraMap?.get("parentGroupNo") as? String
            if (!parentGroupNo.isNullOrEmpty()) {
                lookupChannelId = parentGroupNo
                lookupChannelType = WKChannelType.GROUP
            }
        }

        // 占用游标: 同一 plain 中可能出现多次 @<name>, 按 uids 顺序消费, 防同一处理两次
        val claimed = BooleanArray(plain.length)
        val rebuilt = ArrayList<WKMsgEntity>(realUids.size)

        for (uid in realUids) {
            val candidates = collectMentionNameCandidates(uid, lookupChannelId, lookupChannelType)
            if (candidates.isEmpty()) continue

            // 按候选名长度降序找 (避免 "@张" 先于 "@张三" 命中)
            val ordered = candidates.distinct().sortedByDescending { it.length }
            for (name in ordered) {
                val needle = "@$name"
                val matchOffset = findUnclaimedIndex(plain, needle, claimed)
                if (matchOffset < 0) continue

                val length = needle.length
                for (i in matchOffset until matchOffset + length) claimed[i] = true

                val entity = WKMsgEntity()
                entity.type = ChatContentSpanType.mention
                entity.offset = matchOffset
                entity.length = length
                entity.value = uid
                rebuilt.add(entity)
                break  // 每个 uid 只认一个 name candidate, 命中即停
            }
        }

        // 用重建结果替换原 type=mention entities, 其它类型 (link 等) 保留
        val nonMention = (model.entities ?: emptyList())
            .filter { it.type != ChatContentSpanType.mention }
        if (rebuilt.isEmpty() && nonMention.isEmpty()) return  // 没新增也没原有非 mention, 不动
        model.entities = ArrayList<WKMsgEntity>(nonMention.size + rebuilt.size).apply {
            addAll(nonMention)
            addAll(rebuilt)
        }
    }

    /**
     * 拿一个 uid 在指定群里所有可能的"显示名": memberName / memberRemark
     * + 该用户的 PERSONAL channel.channelName / channelRemark, 用于在 plain 文本里反查
     * `@<name>` 位置。返回的列表保留候选顺序, 调用方可去重 + 长度排序后扫。
     */
    private fun collectMentionNameCandidates(
        uid: String,
        channelId: String,
        channelType: Byte,
    ): List<String> {
        val out = ArrayList<String>(4)
        val member = runCatching {
            WKIM.getInstance().channelMembersManager.getMember(channelId, channelType, uid)
        }.getOrNull()
        member?.memberRemark?.takeIf { it.isNotEmpty() }?.let(out::add)
        member?.memberName?.takeIf { it.isNotEmpty() }?.let(out::add)

        val personal = runCatching {
            WKIM.getInstance().channelManager.getChannel(uid, WKChannelType.PERSONAL)
        }.getOrNull()
        personal?.channelRemark?.takeIf { it.isNotEmpty() }?.let(out::add)
        personal?.channelName?.takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    /**
     * 在 [haystack] 里找 [needle] 第一个不与 [claimed] 区间重叠的位置, 没有则返回 -1。
     * 用于多个 uid 共享同一段 plain 时按出现顺序分配位置, 避免同一 `@xxx` 被两次认领。
     */
    private fun findUnclaimedIndex(haystack: String, needle: String, claimed: BooleanArray): Int {
        if (needle.isEmpty() || haystack.length < needle.length) return -1
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return -1
            // 任一字节已被前面 uid 占用 → 跳过, 找下一个
            var conflict = false
            for (i in idx until idx + needle.length) {
                if (i < claimed.size && claimed[i]) { conflict = true; break }
            }
            if (!conflict) return idx
            from = idx + 1
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
