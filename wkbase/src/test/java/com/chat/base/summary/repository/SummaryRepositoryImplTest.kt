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
import com.chat.base.summary.api.CreateSummaryRequest
import com.chat.base.summary.api.EditSummaryRequest
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.SummaryFilter
import com.chat.base.summary.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository 行为锁: envelope 解开 / 错误状态映射 / 入参拼装 / 列表分页结构。
 * 不依赖 Retrofit baseUrl 或 OkHttp,完全靠 [FakeSummaryApi]。
 */
class SummaryRepositoryImplTest {

    private val api = FakeSummaryApi()
    private val repo: SummaryRepository = SummaryRepositoryImpl(apiProvider = { api })

    @Test
    fun `createSummary serialises sources to snake_case and returns task_id`() = runTest {
        api.stubSuccess("POST summaries", JSONObject().apply { put("task_id", 12345L) })

        val sources = listOf(
            SourceItem(SourceType.GroupChat, "g1", "组群"),
            SourceItem(SourceType.DirectMessage, "u9", "小红"),
        )
        val res = repo.createSummary(topic = "Demo", sources = sources)

        assertTrue(res.isSuccess)
        assertEquals(12345L, res.getOrThrow())
        val sent = api.lastRequest("POST summaries") as CreateSummaryRequest
        assertEquals("Demo", sent.topic)
        assertEquals(2, sent.sources.size)
        assertEquals(SourceType.GroupChat.raw, sent.sources[0].sourceType)
        assertEquals("g1", sent.sources[0].sourceId)
    }

    @Test
    fun `listSummaries passes status query param when filter is not All`() = runTest {
        api.stubSuccess(
            "GET summaries",
            JSONObject().apply {
                put("total", 1)
                put("items", JSONArray().apply {
                    add(
                        JSONObject().apply {
                            put("task_id", 99)
                            put("title", "X")
                            put("summary_mode", 1)
                            put("status", 3)
                            put("trigger_type", 1)
                        }
                    )
                })
            },
        )

        val res = repo.listSummaries(page = 2, pageSize = 20, filter = SummaryFilter.Completed)

        assertTrue(res.isSuccess)
        val page = res.getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals(99L, page.items[0].taskId)
        assertEquals(TaskStatus.Completed, page.items[0].status)

        @Suppress("UNCHECKED_CAST")
        val params = api.lastRequest("GET summaries") as Map<String, String>
        assertEquals("2", params["page"])
        assertEquals("20", params["page_size"])
        assertEquals("3", params["status"])
    }

    @Test
    fun `listSummaries omits status when filter is All`() = runTest {
        api.stubSuccess(
            "GET summaries",
            JSONObject().apply {
                put("items", JSONArray())
                put("total", 0)
            },
        )

        repo.listSummaries(page = 1, filter = SummaryFilter.All)

        @Suppress("UNCHECKED_CAST")
        val params = api.lastRequest("GET summaries") as Map<String, String>
        assertFalse("All filter must not send status param", params.containsKey("status"))
    }

    @Test
    fun `non-2xx http maps to SummaryException with backend message`() = runTest {
        api.stubFailure("POST summaries/7/cancel", status = 500, code = 1001, message = "已是终态")

        val res = repo.cancelSummary(taskId = 7)

        assertTrue(res.isFailure)
        val err = res.exceptionOrNull() as SummaryException
        assertEquals(500, err.httpStatus)
        assertEquals("已是终态", err.message)
    }

    /**
     * HTTP 200 但 envelope.code != 0: 这是后端业务级失败 (例如 createSummary 返回
     * `{"code":4001,"message":"模板已下线","data":null}`). 旧实现透传 data → createSummary
     * 拿到 task_id=0 假装成功; 修复后必须走 SummaryException 路径, apiCode 透传给 UI。
     */
    @Test
    fun `http 200 with non-zero envelope code maps to SummaryException`() = runTest {
        api.stubFailure(
            "POST summaries", status = 200, code = 4001, message = "模板已下线",
        )

        val res = repo.createSummary(
            topic = "Demo",
            sources = listOf(SourceItem(SourceType.GroupChat, "g1", "组群")),
        )

        assertTrue(res.isFailure)
        val err = res.exceptionOrNull() as SummaryException
        assertEquals(200, err.httpStatus)
        assertEquals(4001, err.apiCode)
        assertEquals("模板已下线", err.message)
    }

    /**
     * createSummary 返回 envelope code=0 但 data 里没有 task_id (后端契约破裂):
     * 旧实现 fallback 到 0L 假装成功 → SmartSummaryCreateActivity 关闭后跳进 detail(taskId=0)
     * 拉空详情, 用户看到无效页. 修复后视为契约破裂抛 SummaryException, 让 UI 弹"创建失败"。
     */
    @Test
    fun `createSummary missing task_id maps to SummaryException`() = runTest {
        api.stubSuccess("POST summaries", JSONObject())  // 空 data, 没有 task_id

        val res = repo.createSummary(
            topic = "Demo",
            sources = listOf(SourceItem(SourceType.GroupChat, "g1", "组群")),
        )

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is SummaryException)
    }

    @Test
    fun `editSummary 409 surfaces as conflict exception`() = runTest {
        api.stubFailure("PUT summaries/3/edit", status = 409, code = -1, message = "stale")

        val res = repo.editSummary(taskId = 3, content = "x", baseResultId = 12)

        assertTrue(res.isFailure)
        val err = res.exceptionOrNull() as SummaryException
        assertTrue(err.isConflict)
        val sent = api.lastRequest("PUT summaries/3/edit") as EditSummaryRequest
        assertEquals(12L, sent.baseResultId)
        assertEquals("x", sent.content)
    }

    @Test
    fun `regenerateSummary returns new task_id and forwards optional topic`() = runTest {
        api.stubSuccess("POST summaries/1/regenerate", JSONObject().apply { put("task_id", 88L) })

        val res = repo.regenerateSummary(taskId = 1, topic = "新主题")

        assertEquals(88L, res.getOrThrow())
    }

    @Test
    fun `batchStatus reads tasks array and parses BatchStatusItem with id key`() = runTest {
        api.stubSuccess(
            "POST summaries/batch-status",
            JSONObject().apply {
                put("tasks", JSONArray().apply {
                    add(JSONObject().apply { put("id", 100L); put("status", 2) })
                    add(JSONObject().apply { put("id", 101L); put("status", 3); put("progress", 100) })
                })
            },
        )

        val res = repo.batchStatus(listOf(100, 101))
        assertTrue(res.isSuccess)
        val items = res.getOrThrow()
        assertEquals(2, items.size)
        assertEquals(100L, items[0].taskId)
        assertEquals(TaskStatus.Processing, items[0].status)
        assertEquals(TaskStatus.Completed, items[1].status)
    }

    @Test
    fun `getSummaryDetail parses nested result and returns model`() = runTest {
        api.stubSuccess(
            "GET summaries/42",
            JSONObject().apply {
                put("task_id", 42L)
                put("title", "T")
                put("summary_mode", 1)
                put("status", 3)
                put("trigger_type", 1)
                put("result", JSONObject().apply {
                    put("content", "hello [1]")
                    put("citations", JSONArray())
                })
                put("permissions", JSONObject().apply { put("can_edit", true) })
            },
        )

        val res = repo.getSummaryDetail(taskId = 42)
        assertTrue(res.isSuccess)
        val d = res.getOrThrow()
        assertEquals(42L, d.taskId)
        assertNotNull(d.result)
        assertEquals("hello [1]", d.result?.content)
        assertEquals(true, d.permissions?.canEdit)
    }

    @Test
    fun `getTopicTemplates reads templates field`() = runTest {
        api.stubSuccess(
            "GET summary-templates",
            JSONObject().apply {
                put("templates", JSONArray().apply {
                    add(
                        JSONObject().apply {
                            put("id", "weekly_report")
                            put("label", "Team weekly")
                            put("type", "fixed")
                            put("pattern", "Summarize this week")
                        }
                    )
                })
            },
        )

        val res = repo.getTopicTemplates()
        val ts = res.getOrThrow()
        assertEquals(1, ts.size)
        assertEquals("weekly_report", ts[0].templateId)
        assertEquals("fixed", ts[0].type)
    }

    @Test
    fun `getChatCandidates reads top-level array data`() = runTest {
        api.stubSuccess(
            "GET summary-chat-candidates",
            JSONArray().apply {
                add(JSONObject().apply { put("chat_id", "c1"); put("chat_type", "group"); put("name", "G") })
                add(JSONObject().apply { put("chat_id", "c2"); put("chat_type", "direct"); put("name", "D") })
            },
        )

        val res = repo.getChatCandidates()
        assertTrue(res.isSuccess)
        val items = res.getOrThrow()
        assertEquals(2, items.size)
        assertEquals("c1", items[0].chatId)
        assertEquals("group", items[0].chatType)
    }
}
