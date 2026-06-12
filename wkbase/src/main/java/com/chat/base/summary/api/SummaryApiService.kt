/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.api

import com.alibaba.fastjson.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 22 个 endpoint, 1:1 对齐 iOS [OctoSummaryAPI]。
 *
 * Path 前缀 `/summary/api/v1` 已在 baseUrl 里, 这里只填相对路径。
 *
 * 返回 [Response]<JSONObject>: 让 Repository 拿到 HTTP status (区分 409 编辑冲突)
 * 与原始 envelope ({code,message,data})。Retrofit 用 FastJson converter 把整个响应
 * 体解析为 [JSONObject], data 字段保留为嵌套 JSONObject/JSONArray, 由 Repository
 * 调用 model `fromJson(...)` 工厂转 model。
 *
 * 不在这里手动加 @Header("token") / @Header("X-Space-Id") —— 共享 OkHttpClient 的
 * [com.chat.base.net.CommonRequestParamInterceptor] 已自动注入。Accept-Language 由
 * 仅挂在 summary Retrofit 的 [AcceptLanguageInterceptor] 注入。
 */
interface SummaryApiService {

    // ===== Core CRUD =====

    @POST("summaries")
    suspend fun createSummary(@Body body: CreateSummaryRequest): Response<JSONObject>

    @GET("summaries")
    suspend fun listSummaries(@QueryMap params: Map<String, String>): Response<JSONObject>

    @GET("summaries/{id}")
    suspend fun getSummaryDetail(@Path("id") taskId: Long): Response<JSONObject>

    @DELETE("summaries/{id}")
    suspend fun deleteSummary(@Path("id") taskId: Long): Response<JSONObject>

    @POST("summaries/{id}/regenerate")
    suspend fun regenerateSummary(
        @Path("id") taskId: Long,
        @Body body: RegenerateRequest,
    ): Response<JSONObject>

    @PUT("summaries/{id}/edit")
    suspend fun editSummary(
        @Path("id") taskId: Long,
        @Body body: EditSummaryRequest,
    ): Response<JSONObject>

    // ===== Status / Participation =====

    @POST("summaries/batch-status")
    suspend fun batchStatus(@Body body: BatchStatusRequest): Response<JSONObject>

    @POST("summaries/{id}/cancel")
    suspend fun cancelSummary(@Path("id") taskId: Long): Response<JSONObject>

    @POST("summaries/{id}/confirm")
    suspend fun confirmParticipation(
        @Path("id") taskId: Long,
        @Body body: ConfirmRequest,
    ): Response<JSONObject>

    @POST("summaries/{id}/decline")
    suspend fun declineParticipation(@Path("id") taskId: Long): Response<JSONObject>

    @POST("summaries/{id}/accept")
    suspend fun acceptInvitation(@Path("id") taskId: Long): Response<JSONObject>

    @POST("summaries/{id}/respond")
    suspend fun respondToTask(
        @Path("id") taskId: Long,
        @Body body: RespondRequest,
    ): Response<JSONObject>

    // ===== Personal (BY_PERSON) =====

    @GET("summaries/{id}/personal")
    suspend fun getPersonalResult(@Path("id") taskId: Long): Response<JSONObject>

    @POST("summaries/{id}/submit")
    suspend fun submitPersonalResult(@Path("id") taskId: Long): Response<JSONObject>

    @GET("summaries/{id}/members")
    suspend fun getMembers(@Path("id") taskId: Long): Response<JSONObject>

    // ===== Participants & ancillary =====

    @GET("summaries/{id}/participants")
    suspend fun getParticipants(@Path("id") taskId: Long): Response<JSONObject>

    @GET("summary-templates")
    suspend fun getTopicTemplates(): Response<JSONObject>

    @GET("summary-infer")
    suspend fun inferScope(@Query("topic") topic: String): Response<JSONObject>

    // ===== Candidates =====

    @GET("summary-chat-candidates")
    suspend fun getChatCandidates(@QueryMap params: Map<String, String>): Response<JSONObject>

    @GET("summary-member-candidates")
    suspend fun getMemberCandidates(@QueryMap params: Map<String, String>): Response<JSONObject>
}
