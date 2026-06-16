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
    /**
     * 下次 render 时列表跳到顶部的标志 — 由 [SmartSummaryListViewModel.reloadAndScrollToTop]
     * 在新数据落 state 后置 true, Activity 在 submitList commit callback 里滚到 0 后
     * 调 [SmartSummaryListViewModel.consumePendingScrollToTop] 清掉.
     *
     * 烧在 state 里而不是单独走 SharedFlow 的原因: SharedFlow(replay=0) 的 emit 在 list
     * 处于 STOPPED 状态 (用户进 detail 页) 时会被丢, 等 list 回到 STARTED 重新 collect 拿
     * 不到这条事件; 而 StateFlow 始终把最新 value replay 给新订阅者, 跨 STOPPED/STARTED
     * 切换天然不丢. (review 链路: 用户报"详情页重新生成后返回列表刷新了但没浮顶")
     */
    val pendingScrollToTop: Boolean = false,
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
