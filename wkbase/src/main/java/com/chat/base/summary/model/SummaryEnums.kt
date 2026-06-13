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

/**
 * 与 iOS OctoSummaryModels.h 的枚举严格对齐 — 数值编码就是 API 契约,
 * 不能调整顺序、不能补 0 占位。后端 SummaryListItem.status / SourceItem.source_type
 * 等字段直接发整数。
 */
enum class TaskStatus(val raw: Int) {
    Pending(0),
    WaitingConfirm(1),
    Processing(2),
    Completed(3),
    Failed(4),
    Cancelled(5);

    companion object {
        fun of(raw: Int): TaskStatus = entries.firstOrNull { it.raw == raw } ?: Pending
    }
}

enum class SummaryMode(val raw: Int) {
    ByGroup(1),
    ByPerson(2);

    companion object {
        fun of(raw: Int): SummaryMode = entries.firstOrNull { it.raw == raw } ?: ByGroup
    }
}

enum class TriggerType(val raw: Int) {
    Manual(1),
    Scheduled(2);

    companion object {
        fun of(raw: Int): TriggerType = entries.firstOrNull { it.raw == raw } ?: Manual
    }
}

enum class SourceType(val raw: Int) {
    GroupChat(1),
    Thread(2),
    DirectMessage(3);

    companion object {
        fun of(raw: Int): SourceType = entries.firstOrNull { it.raw == raw } ?: GroupChat
    }
}

enum class ParticipantStatus(val raw: Int) {
    Pending(0),
    Confirmed(1),
    Declined(2);

    companion object {
        fun of(raw: Int): ParticipantStatus = entries.firstOrNull { it.raw == raw } ?: Pending
    }
}

/**
 * 列表页 6 个筛选 tab → API status 参数。All → null 表示不传。
 * 顺序严格对齐 iOS OctoSummaryFilterTabsView。
 */
enum class SummaryFilter {
    All,
    Pending,
    WaitingConfirm,
    Processing,
    Completed,
    Failed;

    fun toApiStatus(): Int? = when (this) {
        All -> null
        Pending -> TaskStatus.Pending.raw
        WaitingConfirm -> TaskStatus.WaitingConfirm.raw
        Processing -> TaskStatus.Processing.raw
        Completed -> TaskStatus.Completed.raw
        Failed -> TaskStatus.Failed.raw
    }
}
