/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 总结模块跨页面事件总线. 1:1 对齐 iOS [NSNotificationCenter] 在 OctoSummary
 * 链路上的两个广播:
 *   - "OctoSummaryDidCreateNotification" (创建总结成功)
 *   - 详情页 cancel/regenerate/delete 完成 (iOS 走 [self loadDetail] 不主动通知,
 *     但 Android 用户报"详情页重新生成后返回列表看不到正在总结的状态刷新", 我们
 *     补一条事件让 list 在重新可见时能同步)
 *
 * 不引 EventBus / RxBus 第三方; 项目里 Summary 模块已经全栈用 Flow / coroutine,
 * 一个 [MutableSharedFlow] + [tryEmit] 完全够用. extraBufferCapacity=1 +
 * DROP_OLDEST 让 emit 永远不阻塞, 没订阅者时也不堆积过期事件 — 多次连续操作只
 * 保留最近一条 (列表 reload 是幂等的, 收到 N 次和 1 次效果一样).
 */
object SummaryEvents {

    /**
     * 列表需要刷新的信号. 触发时机:
     *   - 创建总结成功 ([com.chat.uikit.summary.create])
     *   - 详情页 cancel / regenerate / delete 成功 ([com.chat.uikit.summary.detail])
     *
     * payload [Boolean] 表示订阅方是否应该顺手"跳顶":
     *   - true  → regenerate 之类会产生新 task_id 的操作, 服务端按 created_at desc
     *             排序, 新条目落在第 0 行, 用户预期看到它浮顶
     *   - false → cancel / delete 只翻状态/删条目, 强行跳顶反而打断用户当前位置
     *
     * 列表 Activity 用 lifecycleScope 直接 collect (不套 repeatOnLifecycle(STARTED)):
     * 用户进 detail 页期间 list 处于 STOPPED, 此时 detail 这边 regenerate 触发 emit 必须
     * 能被 list 端收到才不丢; 如果绑死在 STARTED 上, emit 当下没人订阅 (SharedFlow replay=0)
     * 就丢掉了, 用户返回 list 看不到刷新. reload 只动 ViewModel state, 不碰 UI, STOPPED
     * 期间也安全 — uiState 那条 collect 仍然由 repeatOnLifecycle(STARTED) 守门, 用户回到
     * STARTED 时自然 render 最新数据. 与 iOS NSNotification 全程在线接收同效果.
     *
     * true 调 reloadAndScrollToTop, false 调 reload. 与 iOS NSNotification post object:nil
     * 同语义, 多了一个跳顶提示位.
     */
    private val _listShouldRefresh = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val listShouldRefresh: SharedFlow<Boolean> = _listShouldRefresh.asSharedFlow()

    /** 触发列表刷新. [scrollToTop]=true 时订阅方应顺手跳顶 (regenerate / 创建场景). */
    fun postListShouldRefresh(scrollToTop: Boolean = false) {
        _listShouldRefresh.tryEmit(scrollToTop)
    }
}
