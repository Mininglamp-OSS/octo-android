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
import com.chat.base.utils.WKLogUtils
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
    ): Result<T> = try {
        // 不能用 runCatching: 它捕 Throwable 包括 CancellationException, 调用方 (poller /
        // VM) 取消请求时这里会把 cancellation 折叠成 Result.failure 让调用链继续走失败分支
        // (e.g. 详情页 silent=false 时多 emit 一次 LoadFailed toast), 违反结构化并发约定。
        // CancellationException 必须重抛让父协程的 cancel 信号正确传递。
        Result.success(callEnvelopeInner(block, extract))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private suspend fun <T> callEnvelopeInner(
        block: suspend (SummaryApiService) -> Response<JSONObject>,
        extract: (Any?) -> T,
    ): T {
        val resp = try {
            block(api)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 仅在 debug 包打日志, 不输出 body 内容; 异常本身会被外层 try 捕获后包成
            // Result.failure 抛给上层, 这里只做线索留存。
            WKLogUtils.e(LOG_TAG, "call threw: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
        val httpStatus = resp.code()
        val body: JSONObject? = resp.body()
        val urlForLog = resp.raw().request.url.toString()
        if (!resp.isSuccessful) {
            // parseErrorMessage 会调 errorBody().string() 把 buffer 消费掉,
            // 一次性拿到 message, 同时打日志, 不要在这里再读 errorBody。
            val msg = parseErrorMessage(resp)
            WKLogUtils.w(LOG_TAG, "<- $httpStatus $urlForLog message=$msg")
            throw SummaryException(httpStatus = httpStatus, apiCode = -1, message = msg)
        }
        if (body == null) {
            WKLogUtils.w(LOG_TAG, "<- $httpStatus $urlForLog body=null")
            throw SummaryException(httpStatus = httpStatus, apiCode = -1, message = null)
        }
        // 校验 envelope.code: HTTP 200 + code != 0 是后端业务失败 (与 [SummaryRepository]
        // 注释 + [ApiEnvelope] 文档一致 — code == 0 才视为成功)。把 envelope.message
        // 透出去给 UI 层, 方便用户看到具体错误原因。
        // body 里没有 "code" 字段时 FastJson getIntValue 返回 0, 视为成功 (兼容裸 data 响应)。
        val apiCode = body.getIntValue("code")
        if (apiCode != 0) {
            val msg = body.getString("message") ?: body.getString("msg")
            WKLogUtils.w(LOG_TAG, "<- $httpStatus $urlForLog apiCode=$apiCode")
            throw SummaryException(httpStatus = httpStatus, apiCode = apiCode, message = msg)
        }
        // 仅日志 url + status, 永不打 body —— body 含总结正文 / citation /
        // 参与者上下文等私密 chat-derived 数据, 即使 debug 也不输出避免 logcat 抓取时泄露。
        WKLogUtils.d(LOG_TAG, "<- $httpStatus $urlForLog ok")
        // data 字段缺失时把整个 body 作为 data 透下去 (兼容部分 endpoint 直接把字段
        // 平铺在顶层, 例如 listSummaries 的 items/total)。
        val rawData = body["data"]
        val data = rawData ?: body
        return extract(data)
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

    private companion object {
        const val LOG_TAG = "SummaryApi"
    }

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
        // 调试: 序列化整个 body 让排查私聊"总结不到内容"时能直接对照 iOS 抓包看 source/origin 是不是正确。
        // 走 fastjson 同款序列化 (与实际网络出口同口径), 而不是 toString —— 否则看到的字段名是 camelCase
        // 而非 snake_case, 会误以为客户端发的就是错的。release 包关掉避免 topic / sourceName 等
        // 用户内容进 logcat (默认 Log.i 在 release 也会输出).
        if (com.chat.base.BuildConfig.DEBUG) {
            runCatching {
                val json = com.alibaba.fastjson.JSON.toJSONString(body)
                android.util.Log.i("SummaryDebug", "createSummary req: $json")
            }
        }
        return callEnvelope({ it.createSummary(body) }) { data ->
            // task_id 缺失 / 为 0 视为后端契约破裂, 抛 SummaryException 让上层弹 创建失败 toast,
            // 而不是 success(0L) 让 SmartSummaryCreateActivity 关闭后跳进 detail(taskId=0) 拉空详情。
            val taskId = asObject(data)?.getLongValue("task_id") ?: 0L
            if (com.chat.base.BuildConfig.DEBUG) {
                android.util.Log.i("SummaryDebug", "createSummary resp: task_id=$taskId raw=$data")
            }
            if (taskId <= 0L) {
                throw SummaryException(
                    httpStatus = 200, apiCode = 0,
                    message = "createSummary returned no task_id",
                )
            }
            taskId
        }
    }

    override suspend fun listSummaries(
        page: Int,
        pageSize: Int,
        filter: SummaryFilter,
        keyword: String?,
    ): Result<SummaryListPage> {
        val params = buildMap {
            put("page", page.toString())
            put("page_size", pageSize.toString())
            filter.toApiStatus()?.let { put("status", it.toString()) }
            keyword?.takeIf { it.isNotEmpty() }?.let { put("keyword", it) }
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
            // 与 createSummary 同口径: task_id 缺失 / 0 视为契约破裂, 不返回假成功。
            val newId = asObject(data)?.getLongValue("task_id") ?: 0L
            if (newId <= 0L) {
                throw SummaryException(
                    httpStatus = 200, apiCode = 0,
                    message = "regenerateSummary returned no task_id",
                )
            }
            newId
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
