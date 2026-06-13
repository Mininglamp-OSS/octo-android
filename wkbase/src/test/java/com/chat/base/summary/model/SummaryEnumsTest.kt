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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * iOS OctoSummaryModels.h 枚举数值是 API 契约 — 任何乱序都会让后端 status=2 在
 * Android 上被映射成 "Pending" 而不是 "Processing", 列表整页显示乱套。这套测试是
 * 防止未来误改的契约锁。
 */
class SummaryEnumsTest {

    @Test
    fun `task status raw values match iOS contract`() {
        assertEquals(0, TaskStatus.Pending.raw)
        assertEquals(1, TaskStatus.WaitingConfirm.raw)
        assertEquals(2, TaskStatus.Processing.raw)
        assertEquals(3, TaskStatus.Completed.raw)
        assertEquals(4, TaskStatus.Failed.raw)
        assertEquals(5, TaskStatus.Cancelled.raw)
    }

    @Test
    fun `task status of unknown raw falls back to pending`() {
        assertEquals(TaskStatus.Pending, TaskStatus.of(99))
    }

    @Test
    fun `summary mode raw values match iOS contract`() {
        assertEquals(1, SummaryMode.ByGroup.raw)
        assertEquals(2, SummaryMode.ByPerson.raw)
    }

    @Test
    fun `source type raw values match iOS contract`() {
        assertEquals(1, SourceType.GroupChat.raw)
        assertEquals(2, SourceType.Thread.raw)
        assertEquals(3, SourceType.DirectMessage.raw)
    }

    @Test
    fun `participant status raw values match iOS contract`() {
        assertEquals(0, ParticipantStatus.Pending.raw)
        assertEquals(1, ParticipantStatus.Confirmed.raw)
        assertEquals(2, ParticipantStatus.Declined.raw)
    }

    @Test
    fun `trigger type raw values match iOS contract`() {
        assertEquals(1, TriggerType.Manual.raw)
        assertEquals(2, TriggerType.Scheduled.raw)
    }

    @Test
    fun `summary filter All maps to null api status`() {
        assertNull(SummaryFilter.All.toApiStatus())
    }

    @Test
    fun `summary filter mapping matches iOS taskStatusForFilter`() {
        assertEquals(TaskStatus.Pending.raw, SummaryFilter.Pending.toApiStatus())
        assertEquals(TaskStatus.WaitingConfirm.raw, SummaryFilter.WaitingConfirm.toApiStatus())
        assertEquals(TaskStatus.Processing.raw, SummaryFilter.Processing.toApiStatus())
        assertEquals(TaskStatus.Completed.raw, SummaryFilter.Completed.toApiStatus())
        assertEquals(TaskStatus.Failed.raw, SummaryFilter.Failed.toApiStatus())
    }
}
