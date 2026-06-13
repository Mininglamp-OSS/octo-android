/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.list

import com.chat.base.summary.model.SummaryFilter
import com.chat.base.summary.model.SummaryListItem

/**
 * 列表页 UI 状态. 1:1 对齐 iOS [OctoSummaryListVC] 的 ivars 集合:
 *   items / loading / page / hasMore / filter / keyword + 顶部暂态信息 (toast / error).
 *
 * 跟 iOS 不同的是, summaryPreview 仍然挂在 [SummaryListItem] 上 (val class with var),
 * 这里不重复保存。
 */
data class SummaryListUiState(
    val items: List<SummaryListItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val filter: SummaryFilter = SummaryFilter.All,
    val keyword: String = "",
    /** 仅一次性消费的事件 (toast/网络错误/操作结果), 由 ViewModel 在派发后置 null. */
    val transientMessage: TransientMessage? = null,
) {
    val isInitialEmpty: Boolean get() = items.isEmpty() && !loading && keyword.isEmpty()
    val isSearchEmpty: Boolean get() = items.isEmpty() && !loading && keyword.isNotEmpty()
}

/**
 * 一次性消息 (toast). 与 [SummaryListUiState.transientMessage] 配套, Activity 显示后通过
 * [SmartSummaryListViewModel.consumeTransient] 清空。
 */
sealed interface TransientMessage {
    data class Text(val message: String) : TransientMessage
    data class StringRes(val resId: Int) : TransientMessage
}
