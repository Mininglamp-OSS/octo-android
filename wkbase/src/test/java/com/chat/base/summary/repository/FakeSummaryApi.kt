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

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.chat.base.summary.api.BatchStatusRequest
import com.chat.base.summary.api.ConfirmRequest
import com.chat.base.summary.api.CreateSummaryRequest
import com.chat.base.summary.api.EditSummaryRequest
import com.chat.base.summary.api.RegenerateRequest
import com.chat.base.summary.api.RespondRequest
import com.chat.base.summary.api.SummaryApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * 测试用 Fake. 每个 endpoint 录制最近一次调用的入参 (供 assert 入参), 返回值由
 * 调用方塞预设。
 */
class FakeSummaryApi : SummaryApiService {

    /** 路径 → 预录响应 (按 FIFO 弹出, 一个路径多次调用可塞多个). */
    private val queued = HashMap<String, ArrayDeque<Response<JSONObject>>>()

    /** 路径 → 最近一次入参 (供测试 assert). */
    private val lastRequests = HashMap<String, Any?>()

    fun stub(path: String, status: Int = 200, envelope: JSONObject) {
        val resp = if (status in 200..299) {
            Response.success(envelope)
        } else {
            Response.error(
                status,
                JSON.toJSONString(envelope).toResponseBody("application/json".toMediaType()),
            )
        }
        queued.getOrPut(path) { ArrayDeque() }.addLast(resp)
    }

    fun stubSuccess(path: String, data: Any?) {
        val env = JSONObject().apply {
            put("code", 0)
            put("message", "ok")
            if (data != null) put("data", data)
        }
        stub(path, 200, env)
    }

    fun stubFailure(path: String, status: Int = 200, code: Int = -1, message: String = "boom") {
        val env = JSONObject().apply {
            put("code", code)
            put("message", message)
        }
        stub(path, status, env)
    }

    fun lastRequest(path: String): Any? = lastRequests[path]

    private fun consume(path: String): Response<JSONObject> {
        val q = queued[path]
            ?: error("FakeSummaryApi: no stub for $path (queued: ${queued.keys})")
        if (q.isEmpty()) error("FakeSummaryApi: stubs exhausted for $path")
        return q.removeFirst()
    }

    private fun record(path: String, body: Any?) {
        lastRequests[path] = body
    }

    override suspend fun createSummary(body: CreateSummaryRequest): Response<JSONObject> {
        record("POST summaries", body); return consume("POST summaries")
    }

    override suspend fun listSummaries(params: Map<String, String>): Response<JSONObject> {
        record("GET summaries", params); return consume("GET summaries")
    }

    override suspend fun getSummaryDetail(taskId: Long): Response<JSONObject> {
        record("GET summaries/$taskId", taskId); return consume("GET summaries/$taskId")
    }

    override suspend fun getSummaryDetailByNo(taskNo: String): Response<JSONObject> {
        record("GET summaries/$taskNo", taskNo); return consume("GET summaries/$taskNo")
    }

    override suspend fun deleteSummary(taskId: Long): Response<JSONObject> {
        record("DELETE summaries/$taskId", taskId); return consume("DELETE summaries/$taskId")
    }

    override suspend fun regenerateSummary(taskId: Long, body: RegenerateRequest): Response<JSONObject> {
        record("POST summaries/$taskId/regenerate", body); return consume("POST summaries/$taskId/regenerate")
    }

    override suspend fun editSummary(taskId: Long, body: EditSummaryRequest): Response<JSONObject> {
        record("PUT summaries/$taskId/edit", body); return consume("PUT summaries/$taskId/edit")
    }

    override suspend fun batchStatus(body: BatchStatusRequest): Response<JSONObject> {
        record("POST summaries/batch-status", body); return consume("POST summaries/batch-status")
    }

    override suspend fun cancelSummary(taskId: Long): Response<JSONObject> {
        record("POST summaries/$taskId/cancel", taskId); return consume("POST summaries/$taskId/cancel")
    }

    override suspend fun confirmParticipation(taskId: Long, body: ConfirmRequest): Response<JSONObject> {
        record("POST summaries/$taskId/confirm", body); return consume("POST summaries/$taskId/confirm")
    }

    override suspend fun declineParticipation(taskId: Long): Response<JSONObject> {
        record("POST summaries/$taskId/decline", taskId); return consume("POST summaries/$taskId/decline")
    }

    override suspend fun acceptInvitation(taskId: Long): Response<JSONObject> {
        record("POST summaries/$taskId/accept", taskId); return consume("POST summaries/$taskId/accept")
    }

    override suspend fun respondToTask(taskId: Long, body: RespondRequest): Response<JSONObject> {
        record("POST summaries/$taskId/respond", body); return consume("POST summaries/$taskId/respond")
    }

    override suspend fun getPersonalResult(taskId: Long): Response<JSONObject> {
        record("GET summaries/$taskId/personal", taskId); return consume("GET summaries/$taskId/personal")
    }

    override suspend fun submitPersonalResult(taskId: Long): Response<JSONObject> {
        record("POST summaries/$taskId/submit", taskId); return consume("POST summaries/$taskId/submit")
    }

    override suspend fun getMembers(taskId: Long): Response<JSONObject> {
        record("GET summaries/$taskId/members", taskId); return consume("GET summaries/$taskId/members")
    }

    override suspend fun getParticipants(taskId: Long): Response<JSONObject> {
        record("GET summaries/$taskId/participants", taskId); return consume("GET summaries/$taskId/participants")
    }

    override suspend fun getTopicTemplates(): Response<JSONObject> {
        record("GET summary-templates", Unit); return consume("GET summary-templates")
    }

    override suspend fun inferScope(topic: String): Response<JSONObject> {
        record("GET summary-infer", topic); return consume("GET summary-infer")
    }

    override suspend fun getChatCandidates(params: Map<String, String>): Response<JSONObject> {
        record("GET summary-chat-candidates", params); return consume("GET summary-chat-candidates")
    }

    override suspend fun getMemberCandidates(params: Map<String, String>): Response<JSONObject> {
        record("GET summary-member-candidates", params); return consume("GET summary-member-candidates")
    }

    @Suppress("unused")
    private val unusedRequest: Request? = null
}
