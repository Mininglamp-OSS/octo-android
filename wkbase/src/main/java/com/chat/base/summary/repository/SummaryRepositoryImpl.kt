/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.repository

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chat.base.summary.api.BatchStatusRequest
import com.chat.base.summary.api.ConfirmRequest
import com.chat.base.summary.api.CreateSummaryRequest
import com.chat.base.summary.api.EditSummaryRequest
import com.chat.base.summary.api.RegenerateRequest
import com.chat.base.summary.api.RespondRequest
import com.chat.base.summary.api.SourceSerializable
import com.chat.base.summary.api.SummaryApiClient
import com.chat.base.summary.api.SummaryApiService
import com.chat.base.summary.model.BatchStatusItem
import com.chat.base.summary.model.ChatCandidate
import com.chat.base.summary.model.MemberCandidate
import com.chat.base.summary.model.MemberStatus
import com.chat.base.summary.model.Participant
import com.chat.base.summary.model.PersonalResult
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.SummaryFilter
import com.chat.base.summary.model.SummaryListItem
import com.chat.base.summary.model.SummaryListPage
import com.chat.base.summary.model.TopicTemplate
import com.chat.base.summary.model.intOf
import com.chat.base.summary.model.mapModel
import retrofit2.Response

/**
 * 默认实现, suspend + Retrofit + FastJson。
 *
 * 每个 endpoint 的处理流程统一为 [callEnvelope]:
 *   1. 调用 ApiService 拿到 [Response]<JSONObject> 整包响应体
 *   2. 校验 HTTP status 与 envelope.code
 *   3. 把 envelope.data 透出给调用方 (data 可能是 JSONObject / JSONArray / 标量)
 *   4. 失败时抛 [SummaryException] 携带 HTTP status + 后端 message
 *
 * 调用方再用 model.fromJson 把 data 转 model,等价 iOS 的 transform: block。
 */
class SummaryRepositoryImpl(
    private val apiProvider: () -> SummaryApiService = { SummaryApiClient.get() },
) : SummaryRepository {

    private val api: SummaryApiService get() = apiProvider()

    // region call helper

    private suspend fun <T> callEnvelope(
        block: suspend (SummaryApiService) -> Response<JSONObject>,
        extract: (Any?) -> T,
    ): Result<T> = runCatching {
        val resp = block(api)
        val httpStatus = resp.code()
        val body: JSONObject? = resp.body()
        if (!resp.isSuccessful) {
            // 用 errorBody 解 envelope 拿后端 message;失败再回退到默认 message
            val msg = parseErrorMessage(resp)
            throw SummaryException(httpStatus = httpStatus, apiCode = -1, message = msg)
        }
        if (body == null) {
            throw SummaryException(httpStatus = httpStatus, apiCode = -1, message = null)
        }
        val code = body.intOf("code")
        if (code != 0) {
            val msg = body.getString("message") ?: body.getString("msg")
            throw SummaryException(httpStatus = httpStatus, apiCode = code, message = msg)
        }
        extract(body["data"])
    }

    private fun parseErrorMessage(resp: Response<JSONObject>): String? {
        val errBody = resp.errorBody() ?: return null
        return runCatching {
            val raw = errBody.string()
            if (raw.isBlank()) return null
            val obj = com.alibaba.fastjson.JSON.parseObject(raw)
            obj?.getString("message") ?: obj?.getString("msg")
        }.getOrNull()
    }

    private fun asObject(data: Any?): JSONObject? = data as? JSONObject

    private fun asArray(data: Any?): JSONArray? = data as? JSONArray

    // endregion

    override suspend fun createSummary(
        topic: String,
        sources: List<SourceItem>,
        originChannelId: String,
        originChannelType: Int,
    ): Result<Long> {
        val body = CreateSummaryRequest(
            topic = topic,
            sources = sources.map(SourceSerializable::from),
            originChannelId = originChannelId,
            originChannelType = originChannelType,
        )
        return callEnvelope({ it.createSummary(body) }) { data ->
            asObject(data)?.getLongValue("task_id") ?: 0L
        }
    }

    override suspend fun listSummaries(
        page: Int,
        pageSize: Int,
        filter: SummaryFilter,
    ): Result<SummaryListPage> {
        val params = buildMap {
            put("page", page.toString())
            put("page_size", pageSize.toString())
            filter.toApiStatus()?.let { put("status", it.toString()) }
        }
        return callEnvelope({ it.listSummaries(params) }) { data ->
            val obj = asObject(data)
            val items = obj?.getJSONArray("items")
                .mapModel(SummaryListItem::fromJson)
            val total = obj?.getIntValue("total") ?: items.size
            SummaryListPage(items = items, total = total)
        }
    }

    override suspend fun getSummaryDetail(taskId: Long): Result<SummaryDetail> =
        callEnvelope({ it.getSummaryDetail(taskId) }) { data ->
            SummaryDetail.fromJson(asObject(data))
                ?: throw SummaryException(httpStatus = 200, apiCode = 0, message = "empty detail")
        }

    override suspend fun deleteSummary(taskId: Long): Result<Unit> =
        callEnvelope({ it.deleteSummary(taskId) }) { }

    override suspend fun regenerateSummary(taskId: Long, topic: String?): Result<Long> {
        val body = RegenerateRequest(topic = topic?.takeIf { it.isNotEmpty() })
        return callEnvelope({ it.regenerateSummary(taskId, body) }) { data ->
            asObject(data)?.getLongValue("task_id") ?: 0L
        }
    }

    override suspend fun editSummary(taskId: Long, content: String, baseResultId: Long): Result<Unit> {
        val body = EditSummaryRequest(content = content, baseResultId = baseResultId)
        return callEnvelope({ it.editSummary(taskId, body) }) { }
    }

    override suspend fun batchStatus(taskIds: List<Long>): Result<List<BatchStatusItem>> =
        callEnvelope({ it.batchStatus(BatchStatusRequest(taskIds = taskIds)) }) { data ->
            asObject(data)?.getJSONArray("tasks").mapModel(BatchStatusItem::fromJson)
        }

    override suspend fun cancelSummary(taskId: Long): Result<Unit> =
        callEnvelope({ it.cancelSummary(taskId) }) { }

    override suspend fun confirmParticipation(taskId: Long, sources: List<SourceItem>): Result<Unit> {
        val body = ConfirmRequest(sources = sources.map(SourceSerializable::from))
        return callEnvelope({ it.confirmParticipation(taskId, body) }) { }
    }

    override suspend fun declineParticipation(taskId: Long): Result<Unit> =
        callEnvelope({ it.declineParticipation(taskId) }) { }

    override suspend fun acceptInvitation(taskId: Long): Result<Unit> =
        callEnvelope({ it.acceptInvitation(taskId) }) { }

    override suspend fun respondToTask(taskId: Long, action: String): Result<Unit> =
        callEnvelope({ it.respondToTask(taskId, RespondRequest(action = action)) }) { }

    override suspend fun getPersonalResult(taskId: Long): Result<PersonalResult> =
        callEnvelope({ it.getPersonalResult(taskId) }) { data ->
            PersonalResult.fromJson(asObject(data))
                ?: throw SummaryException(httpStatus = 200, apiCode = 0, message = "empty personal")
        }

    override suspend fun submitPersonalResult(taskId: Long): Result<Unit> =
        callEnvelope({ it.submitPersonalResult(taskId) }) { }

    override suspend fun getMembers(taskId: Long): Result<List<MemberStatus>> =
        callEnvelope({ it.getMembers(taskId) }) { data ->
            asObject(data)?.getJSONArray("members").mapModel(MemberStatus::fromJson)
        }

    override suspend fun getParticipants(taskId: Long): Result<List<Participant>> =
        callEnvelope({ it.getParticipants(taskId) }) { data ->
            asObject(data)?.getJSONArray("participants").mapModel(Participant::fromJson)
        }

    override suspend fun getTopicTemplates(): Result<List<TopicTemplate>> =
        callEnvelope({ it.getTopicTemplates() }) { data ->
            asObject(data)?.getJSONArray("templates").mapModel(TopicTemplate::fromJson)
        }

    override suspend fun inferScope(topic: String): Result<Map<String, Any?>> =
        callEnvelope({ it.inferScope(topic) }) { data ->
            (asObject(data) ?: JSONObject()).innerMap.toMap()
        }

    override suspend fun getChatCandidates(params: Map<String, String>): Result<List<ChatCandidate>> =
        callEnvelope({ it.getChatCandidates(params) }) { data ->
            asArray(data).mapModel(ChatCandidate::fromJson)
        }

    override suspend fun getMemberCandidates(params: Map<String, String>): Result<List<MemberCandidate>> =
        callEnvelope({ it.getMemberCandidates(params) }) { data ->
            asArray(data).mapModel(MemberCandidate::fromJson)
        }
}
