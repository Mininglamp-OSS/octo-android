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

import com.chat.base.summary.model.BatchStatusItem
import com.chat.base.summary.model.ChatCandidate
import com.chat.base.summary.model.MemberCandidate
import com.chat.base.summary.model.MemberStatus
import com.chat.base.summary.model.Participant
import com.chat.base.summary.model.PersonalResult
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.SummaryFilter
import com.chat.base.summary.model.SummaryListPage
import com.chat.base.summary.model.TopicTemplate

/**
 * 上下文/智能总结业务仓库。
 *
 * 与 iOS [OctoSummaryAPI] 一一对应,所有方法返回 [Result]:
 *   - 成功 → Result.success(...)
 *   - 网络/解析/HTTP 非 2xx / envelope.code != 0 → Result.failure([SummaryException])
 *
 * 业务方处理 409/401 等具体 HTTP status 走 [SummaryException.httpStatus]。
 */
interface SummaryRepository {

    suspend fun createSummary(
        topic: String,
        sources: List<SourceItem>,
        originChannelId: String = "",
        originChannelType: Int = 0,
    ): Result<Long>

    suspend fun listSummaries(
        page: Int,
        pageSize: Int = 20,
        filter: SummaryFilter = SummaryFilter.All,
        keyword: String? = null,
    ): Result<SummaryListPage>

    suspend fun getSummaryDetail(taskId: Long): Result<SummaryDetail>

    /** 通过 task_no (ST 开头字符串) 查详情, 后端详情接口支持字符串 id 反查。 */
    suspend fun getSummaryDetailByNo(taskNo: String): Result<SummaryDetail>

    suspend fun deleteSummary(taskId: Long): Result<Unit>

    /** 返回新生成的 task_id (后端给新 id, 旧 id 终态). */
    suspend fun regenerateSummary(taskId: Long, topic: String? = null): Result<Long>

    suspend fun editSummary(taskId: Long, content: String, baseResultId: Long): Result<Unit>

    suspend fun batchStatus(taskIds: List<Long>): Result<List<BatchStatusItem>>

    suspend fun cancelSummary(taskId: Long): Result<Unit>

    suspend fun confirmParticipation(taskId: Long, sources: List<SourceItem>): Result<Unit>

    suspend fun declineParticipation(taskId: Long): Result<Unit>

    suspend fun acceptInvitation(taskId: Long): Result<Unit>

    suspend fun respondToTask(taskId: Long, action: String): Result<Unit>

    suspend fun getPersonalResult(taskId: Long): Result<PersonalResult>

    suspend fun submitPersonalResult(taskId: Long): Result<Unit>

    suspend fun getMembers(taskId: Long): Result<List<MemberStatus>>

    suspend fun getParticipants(taskId: Long): Result<List<Participant>>

    suspend fun getTopicTemplates(): Result<List<TopicTemplate>>

    suspend fun inferScope(topic: String): Result<Map<String, Any?>>

    suspend fun getChatCandidates(params: Map<String, String> = emptyMap()): Result<List<ChatCandidate>>

    suspend fun getMemberCandidates(params: Map<String, String> = emptyMap()): Result<List<MemberCandidate>>
}
