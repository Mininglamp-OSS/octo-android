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

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字段级契约锁: snake_case key → Kotlin 属性的映射, 与 iOS 严格一致。
 * 任何 fromJson 漏字段, 这里会立刻挂掉。
 */
class SummaryModelsTest {

    private fun obj(json: String): JSONObject = JSON.parseObject(json)

    @Test
    fun `SourceItem fromJson maps fields and toJson round trips`() {
        val src = SourceItem.fromJson(obj("""{"source_type":2,"source_id":"abc","source_name":"#general"}"""))
        assertNotNull(src)
        assertEquals(SourceType.Thread, src!!.sourceType)
        assertEquals("abc", src.sourceId)
        assertEquals("#general", src.sourceName)

        val out = src.toJson()
        assertEquals(2, out.getIntValue("source_type"))
        assertEquals("abc", out.getString("source_id"))
        assertEquals("#general", out.getString("source_name"))
    }

    @Test
    fun `SourceItem fromJson handles missing source_name as null`() {
        val src = SourceItem.fromJson(obj("""{"source_type":1,"source_id":"g1"}"""))
        assertNotNull(src)
        assertNull(src!!.sourceName)
    }

    @Test
    fun `Participant fromJson maps user fields and status`() {
        val p = Participant.fromJson(obj("""{"user_id":"u1","user_name":"小明","status":1,"confirmed_at":"2026-06-12T10:00:00Z"}"""))
        assertNotNull(p)
        assertEquals("u1", p!!.userId)
        assertEquals("小明", p.userName)
        assertEquals(ParticipantStatus.Confirmed, p.status)
        assertEquals("2026-06-12T10:00:00Z", p.confirmedAt)
    }

    @Test
    fun `CitationItem fromJson parses nested context arrays`() {
        val json = """
        {
          "index": 1,
          "sender": "alice",
          "content": "hi",
          "sent_at": "2026-06-12T10:00:00Z",
          "source": "#dev",
          "channel_id": "ch1",
          "message_seq": 99,
          "channel_type": 2,
          "context_before": [{"sender":"bob","content":"earlier","sent_at":"2026-06-12T09:59:00Z","message_seq":98}],
          "context_after":  [{"sender":"carol","content":"later","sent_at":"2026-06-12T10:01:00Z","message_seq":100}]
        }
        """.trimIndent()
        val c = CitationItem.fromJson(obj(json))
        assertNotNull(c)
        assertEquals(1, c!!.index)
        assertEquals(1, c.contextBefore.size)
        assertEquals("bob", c.contextBefore[0].sender)
        assertEquals(98, c.contextBefore[0].messageSeq)
        assertEquals(1, c.contextAfter.size)
        assertEquals(100, c.contextAfter[0].messageSeq)
    }

    @Test
    fun `CitationItem fromJson tolerates missing context arrays`() {
        val c = CitationItem.fromJson(obj("""{"index":1,"sender":"a","content":"x","sent_at":"t"}"""))
        assertNotNull(c)
        assertTrue(c!!.contextBefore.isEmpty())
        assertTrue(c.contextAfter.isEmpty())
    }

    @Test
    fun `BatchStatusItem reads task id from key id not task_id`() {
        // iOS 自家 quirk: batch-status item 里面 key 是 "id", 不是 "task_id"
        val item = BatchStatusItem.fromJson(obj("""{"id":12345,"status":3,"progress":100}"""))
        assertNotNull(item)
        assertEquals(12345L, item!!.taskId)
        assertEquals(TaskStatus.Completed, item.status)
        assertEquals(100, item.progress)
    }

    @Test
    fun `SummaryListItem fromJson preserves nullable schedule_id and parses sources`() {
        val json = """
        {
          "task_id": 100,
          "title": "Demo",
          "summary_mode": 1,
          "status": 2,
          "trigger_type": 1,
          "schedule_id": null,
          "sources": [
            {"source_type":1,"source_id":"g1","source_name":"组群"},
            {"source_type":3,"source_id":"u9","source_name":"小红"}
          ],
          "participants": [],
          "creator_name": "张三",
          "created_at": "2026-06-12T10:00:00Z"
        }
        """.trimIndent()
        val item = SummaryListItem.fromJson(obj(json))
        assertNotNull(item)
        assertEquals(100L, item!!.taskId)
        assertEquals("Demo", item.title)
        assertEquals(SummaryMode.ByGroup, item.summaryMode)
        assertEquals(TaskStatus.Processing, item.status)
        assertNull(item.scheduleId)
        assertEquals(2, item.sources.size)
        assertEquals(SourceType.GroupChat, item.sources[0].sourceType)
        assertEquals(SourceType.DirectMessage, item.sources[1].sourceType)
        assertEquals("张三", item.creatorName)
    }

    @Test
    fun `SummaryDetail fromJson parses nested result and permissions`() {
        val json = """
        {
          "task_id": 200,
          "title": "Detail",
          "summary_mode": 2,
          "status": 3,
          "trigger_type": 1,
          "sources": [],
          "participants": [],
          "result": {"content":"hello","total_msg_count":42,"version":3,"citations":[]},
          "permissions": {"can_edit": true},
          "result_id": 5050,
          "result_is_edited": false
        }
        """.trimIndent()
        val d = SummaryDetail.fromJson(obj(json))
        assertNotNull(d)
        assertEquals(200L, d!!.taskId)
        assertEquals(SummaryMode.ByPerson, d.summaryMode)
        assertEquals(TaskStatus.Completed, d.status)
        assertEquals("hello", d.result?.content)
        assertEquals(42, d.result?.totalMsgCount)
        assertEquals(true, d.permissions?.canEdit)
        assertEquals(5050L, d.resultId)
    }

    @Test
    fun `TopicTemplate fromJson reads id from id not template_id`() {
        val json = """
        {
          "id": "weekly_report",
          "label": "团队周报",
          "type": "fixed",
          "pattern": "总结本周",
          "placeholders": []
        }
        """.trimIndent()
        val t = TopicTemplate.fromJson(obj(json))
        assertNotNull(t)
        assertEquals("weekly_report", t!!.templateId)
        assertEquals("团队周报", t.label)
        assertEquals("fixed", t.type)
        assertEquals("总结本周", t.pattern)
    }
}
