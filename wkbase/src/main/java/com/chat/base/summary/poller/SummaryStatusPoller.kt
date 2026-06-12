/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.poller

import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.BatchStatusItem
import com.chat.base.summary.model.TaskStatus
import com.chat.base.summary.repository.SummaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 列表 / 详情页用的 5s 轮询器, 1:1 对齐 iOS [OctoSummaryStatusPoller]。
 *
 * 跟一个 [CoroutineScope] (调用方传 lifecycleScope), [setTaskIds] 后立刻轮询,
 * status 变化通过 [onUpdate] 回调 (主线程)。
 *
 *   - VC viewWillAppear  → [resume] / [start]
 *   - VC viewWillDisappear → [pause]
 *   - VC dealloc         → scope 取消即停 (不显式 stop 也安全)
 *
 * 为对齐 iOS, 只在终态变化时回调:
 *   - 上一次 cache 没记录 → 回调 (首次注册)
 *   - 状态码变化 → 回调
 *   - 状态码不变 → 跳过
 */
class SummaryStatusPoller(
    private val scope: CoroutineScope,
    private val repository: SummaryRepository = SummaryDeps.repository,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) {

    private val lastStatus = HashMap<Long, TaskStatus>()

    @Volatile
    private var taskIds: List<Long> = emptyList()

    @Volatile
    private var paused: Boolean = false

    private var pollJob: Job? = null

    var onUpdate: (suspend (Map<Long, BatchStatusItem>) -> Unit)? = null

    fun setTaskIds(ids: List<Long>) {
        taskIds = ids.toList()
    }

    fun start() {
        stop()
        pollJob = scope.launch {
            while (isActive) {
                if (!paused) tick()
                delay(intervalMillis)
            }
        }
    }

    fun pause() { paused = true }

    fun resume() { paused = false }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun tick() {
        val ids = taskIds
        if (ids.isEmpty()) return
        val res = repository.batchStatus(ids)
        val items = res.getOrNull() ?: return
        val changes = HashMap<Long, BatchStatusItem>()
        for (it in items) {
            val prev = lastStatus[it.taskId]
            if (prev == null || prev != it.status) {
                changes[it.taskId] = it
                lastStatus[it.taskId] = it.status
            }
        }
        if (changes.isNotEmpty()) {
            withContext(Dispatchers.Main) { onUpdate?.invoke(changes) }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
