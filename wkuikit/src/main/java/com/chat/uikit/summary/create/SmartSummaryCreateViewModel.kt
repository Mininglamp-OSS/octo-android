/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.TopicTemplate
import com.chat.base.summary.repository.SummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreateEffect {
    data class ToastRes(val resId: Int) : CreateEffect
    /** 创建成功 → Activity 关闭 + 通知列表刷新. */
    object Done : CreateEffect
}

data class CreateUiState(
    val topic: String = "",
    val templates: List<TopicTemplate> = emptyList(),
    val activeTemplateId: String? = null,
    val sources: List<SourceItem> = emptyList(),
    val submitting: Boolean = false,
    val effect: CreateEffect? = null,
) {
    val canSubmit: Boolean get() = topic.isNotBlank() && sources.isNotEmpty() && !submitting
}

/**
 * 发起总结页 ViewModel:
 *   - loadTemplates: 先用本地 fallback 4 模板, 后端拉到再覆盖
 *   - performSubmit: createSummary, 成功 → 发 [CreateEffect.Done]
 */
class SmartSummaryCreateViewModel(
    private val repository: SummaryRepository = SummaryDeps.repository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state = _state.asStateFlow()

    init {
        // 启动: 先 emit 本地 fallback 模板 (Activity 自己装载, 见 fallbackTemplates)
        loadTemplates()
    }

    fun setTopic(topic: String) {
        _state.update { it.copy(topic = topic) }
    }

    fun applyTemplate(t: TopicTemplate, content: String) {
        _state.update { it.copy(activeTemplateId = t.templateId, topic = content) }
    }

    fun setSources(sources: List<SourceItem>) {
        _state.update { it.copy(sources = sources) }
    }

    fun removeSource(item: SourceItem) {
        _state.update { st ->
            st.copy(sources = st.sources.filterNot {
                it.sourceType == item.sourceType && it.sourceId == item.sourceId
            })
        }
    }

    fun consumeEffect() {
        if (_state.value.effect != null) _state.update { it.copy(effect = null) }
    }

    fun setLocalTemplates(templates: List<TopicTemplate>) {
        if (_state.value.templates.isNotEmpty()) return
        _state.update { it.copy(templates = templates) }
    }

    fun submit(originChannelId: String, originChannelType: Int) {
        val st = _state.value
        if (!st.canSubmit) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val res = repository.createSummary(
                topic = st.topic.trim(),
                sources = st.sources,
                originChannelId = originChannelId,
                originChannelType = originChannelType,
            )
            if (res.isFailure) {
                _state.update {
                    it.copy(
                        submitting = false,
                        effect = CreateEffect.ToastRes(com.chat.uikit.R.string.summary_create_failed),
                    )
                }
                return@launch
            }
            _state.update { it.copy(submitting = false, effect = CreateEffect.Done) }
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            val res = repository.getTopicTemplates()
            if (res.isFailure) return@launch
            val list = res.getOrThrow()
            if (list.isNotEmpty()) {
                _state.update { it.copy(templates = list) }
            }
        }
    }
}
