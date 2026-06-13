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

import com.alibaba.fastjson.annotation.JSONField
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SummaryMode

/**
 * POST/PUT 请求体. 字段名严格对齐 iOS createSummaryWithParams: 等。
 *
 * 用 @JSONField 把 camelCase 属性序列化为 snake_case 出口字段, FastJson 1.2.83
 * 自带支持。
 */

class CreateSummaryRequest(
    @JvmField val topic: String,
    @field:JSONField(name = "summary_mode")
    @JvmField val summaryMode: Int = SummaryMode.ByGroup.raw,
    @JvmField val sources: List<SourceSerializable>,
    @field:JSONField(name = "origin_channel_id")
    @JvmField val originChannelId: String = "",
    @field:JSONField(name = "origin_channel_type")
    @JvmField val originChannelType: Int = 0,
)

class RegenerateRequest(
    @JvmField val topic: String? = null,
)

class EditSummaryRequest(
    @JvmField val content: String,
    @field:JSONField(name = "base_result_id")
    @JvmField val baseResultId: Long,
)

class BatchStatusRequest(
    @field:JSONField(name = "task_ids")
    @JvmField val taskIds: List<Long>,
)

class ConfirmRequest(
    @JvmField val sources: List<SourceSerializable>,
)

class RespondRequest(
    /** "accept" / "reject" */
    @JvmField val action: String,
)

/**
 * 出口用的 SourceItem 投影 — 把 camelCase 的 sourceType/sourceId 映回 snake_case。
 * SourceItem 自身只承担入口解析,不挂出口注解,避免双向注解打架。
 */
class SourceSerializable(
    @field:JSONField(name = "source_type")
    @JvmField val sourceType: Int,
    @field:JSONField(name = "source_id")
    @JvmField val sourceId: String,
    @field:JSONField(name = "source_name")
    @JvmField val sourceName: String? = null,
) {
    companion object {
        fun from(item: SourceItem): SourceSerializable = SourceSerializable(
            sourceType = item.sourceType.raw,
            sourceId = item.sourceId,
            sourceName = item.sourceName,
        )
    }
}
