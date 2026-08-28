/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.model

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject

/**
 * 1:1 对齐 iOS OctoSummaryModels.h —— snake_case JSON 字段 → camelCase 属性,
 * fromJson(JSONObject) 工厂等价 iOS 的 +modelFromDict:。
 *
 * 故意不上 @JSONField 注解 / 自动反序列化:
 *   - 字段命名归集到 fromJson 一处,不散落
 *   - 后端缺字段不抛异常,降级到默认值,避免列表加载因单条数据脏掉整页
 *   - 嵌套 OctoCitationItem.context_before/after 这种用注解很啰嗦
 */

internal fun JSONObject?.intOf(key: String, default: Int = 0): Int =
    this?.getIntValue(key) ?: default

internal fun JSONObject?.longOf(key: String, default: Long = 0L): Long =
    this?.getLongValue(key) ?: default

internal fun JSONObject?.boolOf(key: String, default: Boolean = false): Boolean =
    this?.getBooleanValue(key) ?: default

/** 空字符串友好: missing / null → "" */
internal fun JSONObject?.strOf(key: String): String =
    this?.getString(key).orEmpty()

/** 真实可空: missing / null / empty 都返回 null */
internal fun JSONObject?.nstrOf(key: String): String? =
    this?.getString(key).takeUnless { it.isNullOrEmpty() }

internal fun JSONObject?.arrOf(key: String): JSONArray? = this?.getJSONArray(key)

internal fun JSONObject?.objOf(key: String): JSONObject? = this?.getJSONObject(key)

internal inline fun <T> JSONArray?.mapModel(parse: (JSONObject) -> T?): List<T> {
    if (this == null || isEmpty()) return emptyList()
    val out = ArrayList<T>(size)
    for (i in 0 until size) {
        val item = getJSONObject(i) ?: continue
        parse(item)?.let(out::add)
    }
    return out
}

// ===== Source / Participant =====

data class SourceItem(
    val sourceType: SourceType,
    val sourceId: String,
    val sourceName: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("source_type", sourceType.raw)
        put("source_id", sourceId)
        if (!sourceName.isNullOrEmpty()) put("source_name", sourceName)
    }

    companion object {
        fun fromJson(json: JSONObject?): SourceItem? {
            json ?: return null
            return SourceItem(
                sourceType = SourceType.of(json.intOf("source_type")),
                sourceId = json.strOf("source_id"),
                sourceName = json.nstrOf("source_name"),
            )
        }
    }
}

data class Participant(
    val userId: String,
    val userName: String?,
    val status: ParticipantStatus,
    val confirmedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject?): Participant? {
            json ?: return null
            return Participant(
                userId = json.strOf("user_id"),
                userName = json.nstrOf("user_name"),
                status = ParticipantStatus.of(json.intOf("status")),
                confirmedAt = json.nstrOf("confirmed_at"),
            )
        }
    }
}

// ===== Citation =====

data class CitationContextMessage(
    val sender: String,
    val content: String,
    val sentAt: String,
    val messageSeq: Int,
) {
    companion object {
        fun fromJson(json: JSONObject?): CitationContextMessage? {
            json ?: return null
            return CitationContextMessage(
                sender = json.strOf("sender"),
                content = json.strOf("content"),
                sentAt = json.strOf("sent_at"),
                messageSeq = json.intOf("message_seq"),
            )
        }
    }
}

data class CitationItem(
    val index: Int,
    val sender: String,
    val content: String,
    val sentAt: String,
    val source: String?,
    val channelId: String?,
    val messageSeq: Int,
    val channelType: Int,
    val contextBefore: List<CitationContextMessage>,
    val contextAfter: List<CitationContextMessage>,
) {
    companion object {
        fun fromJson(json: JSONObject?): CitationItem? {
            json ?: return null
            return CitationItem(
                index = json.intOf("index"),
                sender = json.strOf("sender"),
                content = json.strOf("content"),
                sentAt = json.strOf("sent_at"),
                source = json.nstrOf("source"),
                channelId = json.nstrOf("channel_id"),
                messageSeq = json.intOf("message_seq"),
                channelType = json.intOf("channel_type"),
                contextBefore = json.arrOf("context_before").mapModel(CitationContextMessage::fromJson),
                contextAfter = json.arrOf("context_after").mapModel(CitationContextMessage::fromJson),
            )
        }
    }
}

// ===== Result =====

data class SummaryResult(
    val content: String,
    val totalMsgCount: Int,
    val totalTokenUsed: Int,
    val modelVersion: String?,
    val version: Int,
    val generatedAt: String?,
    val citations: List<CitationItem>,
) {
    companion object {
        fun fromJson(json: JSONObject?): SummaryResult? {
            json ?: return null
            return SummaryResult(
                content = json.strOf("content"),
                totalMsgCount = json.intOf("total_msg_count"),
                totalTokenUsed = json.intOf("total_token_used"),
                modelVersion = json.nstrOf("model_version"),
                version = json.intOf("version"),
                generatedAt = json.nstrOf("generated_at"),
                citations = json.arrOf("citations").mapModel(CitationItem::fromJson),
            )
        }
    }
}

/** BY_PERSON 个人结果. */
data class PersonalResult(
    val workerStatus: Int,
    val content: String?,
    val citations: List<CitationItem>,
    val submittedAt: String?,
    val generatedAt: String?,
    val msgCount: Int,
) {
    companion object {
        fun fromJson(json: JSONObject?): PersonalResult? {
            json ?: return null
            return PersonalResult(
                workerStatus = json.intOf("worker_status"),
                content = json.nstrOf("content"),
                citations = json.arrOf("citations").mapModel(CitationItem::fromJson),
                submittedAt = json.nstrOf("submitted_at"),
                generatedAt = json.nstrOf("generated_at"),
                msgCount = json.intOf("msg_count"),
            )
        }
    }
}

data class MemberStatus(
    val userId: String,
    val userName: String,
    /** "pending" / "processing" / "completed" / "submitted" / "accepted" / "declined" — 后端纯字符串 */
    val status: String,
    val submittedAt: String?,
    val content: String?,
    val citations: List<CitationItem>,
) {
    companion object {
        fun fromJson(json: JSONObject?): MemberStatus? {
            json ?: return null
            return MemberStatus(
                userId = json.strOf("user_id"),
                userName = json.strOf("user_name"),
                status = json.strOf("status"),
                submittedAt = json.nstrOf("submitted_at"),
                content = json.nstrOf("content"),
                citations = json.arrOf("citations").mapModel(CitationItem::fromJson),
            )
        }
    }
}

// ===== List & Detail =====

private fun parseSources(json: JSONObject?): List<SourceItem> =
    json.arrOf("sources").mapModel(SourceItem::fromJson)

private fun parseParticipants(json: JSONObject?): List<Participant> =
    json.arrOf("participants").mapModel(Participant::fromJson)

/**
 * 注意 summaryPreview / status / completedAt 几个 var: 列表 API 不带正文,详情页 hydrate
 * 后客户端回填; 5s poller 轮询变更也会改 status; 乐观更新 (取消/重新生成) 也修。
 *
 * 保持 data class 是为了 ViewModel 用 .copy() 触发 RecyclerView DiffUtil 的内容差异
 * 检测 — 直接 mutate 相同对象引用会让 DiffUtil 误判 "未变化"。
 */
data class SummaryListItem(
    val taskId: Long,
    val taskNo: String?,
    val title: String,
    val summaryMode: SummaryMode,
    var status: TaskStatus,
    val triggerType: TriggerType,
    val scheduleId: Long?,
    val timeRangeStart: String?,
    val timeRangeEnd: String?,
    val sources: List<SourceItem>,
    val participants: List<Participant>,
    val totalMsgCount: Int,
    val creatorName: String?,
    val originChannelId: String?,
    val originChannelType: Int,
    val createdAt: String?,
    var completedAt: String?,
    /** 客户端懒拉详情后回填,API 不返回. */
    var summaryPreview: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject?): SummaryListItem? {
            json ?: return null
            val schedRaw = json["schedule_id"]
            return SummaryListItem(
                taskId = json.longOf("task_id"),
                taskNo = json.nstrOf("task_no"),
                title = json.strOf("title"),
                summaryMode = SummaryMode.of(json.intOf("summary_mode")),
                status = TaskStatus.of(json.intOf("status")),
                triggerType = TriggerType.of(json.intOf("trigger_type")),
                scheduleId = (schedRaw as? Number)?.toLong(),
                timeRangeStart = json.nstrOf("time_range_start"),
                timeRangeEnd = json.nstrOf("time_range_end"),
                sources = parseSources(json),
                participants = parseParticipants(json),
                totalMsgCount = json.intOf("total_msg_count"),
                creatorName = json.nstrOf("creator_name"),
                originChannelId = json.nstrOf("origin_channel_id"),
                originChannelType = json.intOf("origin_channel_type"),
                createdAt = json.nstrOf("created_at"),
                completedAt = json.nstrOf("completed_at"),
            )
        }
    }
}

data class SummaryListPage(val items: List<SummaryListItem>, val total: Int)

data class SummaryPermissions(val canEdit: Boolean) {
    companion object {
        fun fromJson(json: JSONObject?): SummaryPermissions? {
            json ?: return null
            return SummaryPermissions(canEdit = json.boolOf("can_edit"))
        }
    }
}

data class SummaryDetail(
    val taskId: Long,
    val taskNo: String?,
    val title: String,
    val summaryMode: SummaryMode,
    val status: TaskStatus,
    val triggerType: TriggerType,
    val timeRangeStart: String?,
    val timeRangeEnd: String?,
    val sources: List<SourceItem>,
    val participants: List<Participant>,
    val result: SummaryResult?,
    val errorMessage: String?,
    val scheduleId: Long?,
    /** 创建者 uid. 群总结完成提示只由创建者所在端发出, 见 SummaryNotifyCoordinator. */
    val creatorId: String?,
    val originChannelId: String?,
    val originChannelType: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val resultId: Long?,
    val resultEditedAt: String?,
    val resultIsEdited: Boolean,
    val permissions: SummaryPermissions?,
) {
    companion object {
        fun fromJson(json: JSONObject?): SummaryDetail? {
            json ?: return null
            val schedRaw = json["schedule_id"]
            val ridRaw = json["result_id"]
            return SummaryDetail(
                taskId = json.longOf("task_id"),
                taskNo = json.nstrOf("task_no"),
                title = json.strOf("title"),
                summaryMode = SummaryMode.of(json.intOf("summary_mode")),
                status = TaskStatus.of(json.intOf("status")),
                triggerType = TriggerType.of(json.intOf("trigger_type")),
                timeRangeStart = json.nstrOf("time_range_start"),
                timeRangeEnd = json.nstrOf("time_range_end"),
                sources = parseSources(json),
                participants = parseParticipants(json),
                result = SummaryResult.fromJson(json.objOf("result")),
                errorMessage = json.nstrOf("error_message"),
                scheduleId = (schedRaw as? Number)?.toLong(),
                // creator_id 在后端契约里是可选的 (对齐 octo-web types/summary.ts 的
                // `creator_id?: string`)。fastjson 的 getString 对缺失 key 返回 null 而非
                // 抛异常, 所以 nstrOf 天然降级成 null —— 缺字段只会让"谁创建谁发提示"
                // 退化成不发, 不影响详情页加载。
                creatorId = json.nstrOf("creator_id"),
                originChannelId = json.nstrOf("origin_channel_id"),
                originChannelType = json.intOf("origin_channel_type"),
                createdAt = json.nstrOf("created_at"),
                updatedAt = json.nstrOf("updated_at"),
                resultId = (ridRaw as? Number)?.toLong(),
                resultEditedAt = json.nstrOf("result_edited_at"),
                resultIsEdited = json.boolOf("result_is_edited"),
                permissions = SummaryPermissions.fromJson(json.objOf("permissions")),
            )
        }
    }
}

/**
 * batch-status 返回项,注意 key 是 "id" 不是 "task_id"(后端这里偷懒了, iOS 也单独处理)。
 */
data class BatchStatusItem(
    val taskId: Long,
    val status: TaskStatus,
    val progress: Int,
    val updatedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject?): BatchStatusItem? {
            json ?: return null
            return BatchStatusItem(
                taskId = json.longOf("id"),
                status = TaskStatus.of(json.intOf("status")),
                progress = json.intOf("progress"),
                updatedAt = json.nstrOf("updated_at"),
            )
        }
    }
}

// ===== Candidates / Templates =====

data class ChatCandidate(
    val chatId: String,
    val chatType: String,
    val name: String,
    val memberCount: Int?,
    val parentGroupNo: String?,
    val isBot: Boolean,
    val isArchived: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject?): ChatCandidate? {
            json ?: return null
            val mc = json["member_count"]
            return ChatCandidate(
                chatId = json.strOf("chat_id"),
                chatType = json.strOf("chat_type"),
                name = json.strOf("name"),
                memberCount = (mc as? Number)?.toInt(),
                parentGroupNo = json.nstrOf("parent_group_no"),
                isBot = json.boolOf("is_bot"),
                isArchived = json.boolOf("is_archived"),
            )
        }
    }
}

data class MemberCandidate(
    val userId: String,
    val name: String,
    val avatar: String?,
    val department: String?,
) {
    companion object {
        fun fromJson(json: JSONObject?): MemberCandidate? {
            json ?: return null
            return MemberCandidate(
                userId = json.strOf("user_id"),
                name = json.strOf("name"),
                avatar = json.nstrOf("avatar"),
                department = json.nstrOf("department"),
            )
        }
    }
}

data class TopicTemplatePlaceholder(
    val key: String,
    val label: String,
    /** 后端可选返回 [start, end],缺省时 null */
    val position: List<Int>?,
) {
    companion object {
        fun fromJson(json: JSONObject?): TopicTemplatePlaceholder? {
            json ?: return null
            val arr = json.arrOf("position")
            val pos = if (arr != null && arr.size == 2) listOf(arr.getIntValue(0), arr.getIntValue(1)) else null
            return TopicTemplatePlaceholder(
                key = json.strOf("key"),
                label = json.strOf("label"),
                position = pos,
            )
        }
    }
}

data class TopicTemplate(
    val templateId: String,
    val label: String,
    val icon: String?,
    val description: String?,
    /** "fixed" / "parameterized" */
    val type: String,
    val pattern: String,
    val placeholders: List<TopicTemplatePlaceholder>,
) {
    companion object {
        fun fromJson(json: JSONObject?): TopicTemplate? {
            json ?: return null
            return TopicTemplate(
                templateId = json.strOf("id"),
                label = json.strOf("label"),
                icon = json.nstrOf("icon"),
                description = json.nstrOf("description"),
                type = json.strOf("type"),
                pattern = json.strOf("pattern"),
                placeholders = json.arrOf("placeholders").mapModel(TopicTemplatePlaceholder::fromJson),
            )
        }
    }
}
