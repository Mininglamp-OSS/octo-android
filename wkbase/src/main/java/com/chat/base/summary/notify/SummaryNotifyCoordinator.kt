/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.notify

import com.chat.base.config.WKConfig
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.chat.base.summary.repository.SummaryRepository
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 群总结完成后往来源群发一条系统提示, 补上 "App 发起的总结群里没有任何提示" 这个缺口。
 *
 * ## 为什么是客户端发, 以及它的代价
 *
 * 这条提示在 octo-web 里是**客户端发**的 (SummaryDetailPage.notifyGroupsOnCompletion →
 * WKSDK.chatManager.send), 服务端不参与。Web 只在总结详情页挂载着、且亲眼看到
 * processing→completed 状态沿时才发 —— 所以 App 发起的总结, 只要用户没在 Web 上打开
 * 那条总结的详情页, 群里就一条提示都没有。本类补的就是这个缺口。
 *
 * **这与项目其它同类场景的做法不一致**: 子区创建 (type=1100)、群成员变更 (1002/1003)
 * 都是客户端调 REST、由服务端往频道发系统消息, 去重责任在服务端。本类是这个代码库里
 * 第一例 "多端可能同时产生同一条消息, 靠客户端自行协调"。之所以这么做, 是因为当前既
 * 改不了 Web 也排不上后端。**后端一旦支持在任务完成时直接下发这条提示, 本类应整个删除。**
 *
 * ## 不变量: 谁创建, 谁负责发
 *
 * [SummaryNotifyStore] 只登记**本机发起**的任务 (创建 / 重新生成), 加上 [handleCompleted]
 * 里的 `creatorId == 自己` 双保险。绝对不要改成 "启动时把自己创建的所有在途任务都跟上" ——
 * 那样多台设备会同时跟同一条任务, 必然重复。
 *
 * ## 与 Web 的重复: 当前不做防护
 *
 * 完成即发, 不等待、不检查频道内是否已有 Web 发出的同款提示。
 *
 * 代价: 手机发起总结 + Web 同时开着这条总结的详情页停留到完成, 两端都会各发一条,
 * 群里出现两条一样的提示。这是已知且当前接受的取舍 —— 曾经实现过"等 8s 宽限期 +
 * 查本地库确认 Web 没抢发"的门禁 (2721d832), 但去重判断天生不精确 (提示消息体不带
 * task_id, 只能按频道+发送人+时间窗匹配), 真机复现过它把"自己上一次总结的提示"
 * 误判成"Web 已经发过", 平白吞掉一条本该发的提示。两害相权, 选择了更简单可预测的
 * "完成即发", 接受偶发的跨端重复。
 */
object SummaryNotifyCoordinator {

    /** 轮询在途任务的间隔。 */
    private const val POLL_INTERVAL_MILLIS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: SummaryRepository get() = SummaryDeps.repository

    private var loopJob: Job? = null

    /**
     * [track] 在写完 SP 之后置位, [pollLoop] 在读 SP 之前清位。
     *
     * 这个顺序保证了 "loop 正要因为 pending 空而退出" 与 "track 刚登记了新任务" 撞车时,
     * loop 一定能看到置位从而不退出 —— 否则新登记的任务会永远没人轮询。
     */
    private val pendingChanged = AtomicBoolean(false)

    /** 正在处理完成事件的 taskId, 防止同一任务被并发处理两次。 */
    private val handling: MutableSet<Long> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * 应用启动时调一次, 从 SP 恢复上次没跟完的任务 (进程被杀过)。幂等。
     *
     * 未登录时 [pollLoop] 会立刻退出, 登录后由 [track] 再拉起, 所以不需要监听登录态。
     */
    @JvmStatic
    fun start() {
        ensureLoopRunning()
    }

    /** 本机发起了一次总结 (创建 / 重新生成) 时登记。见类注释里的"谁创建谁发"不变量。 */
    @JvmStatic
    fun track(taskId: Long) {
        if (taskId <= 0L) return
        SummaryNotifyStore.addPending(taskId, System.currentTimeMillis())
        pendingChanged.set(true)
        ensureLoopRunning()
    }

    @Synchronized
    private fun ensureLoopRunning() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { pollLoop() }
    }

    /**
     * 尝试结束轮询循环。返回 true 表示调用方可以安全退出。
     *
     * "判定该退出"和"清掉 loopJob"必须在同一个临界区里完成, 否则有这条竞态:
     * 循环判定 pending 为空决定退出 → [track] 抢在 loopJob 被清掉之前调
     * [ensureLoopRunning], 看到 job 还 active 于是 no-op → 循环退出并清掉 job →
     * 新登记的任务永远没人轮询。把 [pendingChanged] 的检查也挪进锁内就没有这个窗口了。
     *
     * 不需要 finally 兜底清 loopJob: [ensureLoopRunning] 判的是 `isActive`, 循环若因
     * 异常终止, 那个 job 已经 inactive, 下次 track 会正常重启。
     */
    @Synchronized
    private fun finishLoopIfIdle(): Boolean {
        if (pendingChanged.get()) return false
        loopJob = null
        return true
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            pendingChanged.set(false)

            // 未登录: 停掉循环, 登录后由 track 拉起。SP 键按 uid 隔离, 不会串账。
            if (WKConfig.getInstance().uid.isNullOrEmpty()) {
                if (finishLoopIfIdle()) return
                delay(POLL_INTERVAL_MILLIS)
                continue
            }

            val pending = SummaryNotifyStore.activePendingTaskIds(System.currentTimeMillis())
            if (pending.isEmpty()) {
                if (finishLoopIfIdle()) return
                delay(POLL_INTERVAL_MILLIS)
                continue
            }

            val batch = repository.batchStatus(pending)
            batch.getOrNull()?.forEach { item ->
                when (item.status) {
                    TaskStatus.Completed -> {
                        // 先摘出 pending 再异步处理: 结果未知前不占着轮询继续跑。
                        SummaryNotifyStore.removePending(item.taskId)
                        launchCompletionHandling(item.taskId)
                    }
                    TaskStatus.Failed, TaskStatus.Cancelled -> {
                        SummaryNotifyStore.removePending(item.taskId)
                    }
                    else -> Unit
                }
            }

            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun launchCompletionHandling(taskId: Long) {
        if (!handling.add(taskId)) return
        scope.launch {
            try {
                handleCompleted(taskId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 结构化并发: cancel 信号必须往上传, 不能当成"一条提示发失败"吞掉。
                throw e
            } catch (_: Throwable) {
                // 提示是尽力而为: 其余异常都只损失一条提示, 不能影响轮询循环。
            } finally {
                handling.remove(taskId)
            }
        }
    }

    private suspend fun handleCompleted(taskId: Long) {
        val uid = WKConfig.getInstance().uid.orEmpty()
        if (uid.isEmpty()) {
            return
        }

        val detailResult = repository.getSummaryDetail(taskId)
        val detail = detailResult.getOrNull() ?: return
        // 双保险: SP 只登记本机发起的任务, 这里再按服务端权威字段确认一次创建者身份。
        // creatorId 缺失时按"不是我"处理 —— 宁可漏发。
        if (detail.creatorId.isNullOrEmpty() || detail.creatorId != uid) {
            return
        }

        val channels = groupSourceIds(detail)
        if (channels.isEmpty()) return

        val notified = SummaryNotifyStore.notifiedChannels(taskId)
        val targets = channels.filterNot { notified.contains(it) }
        if (targets.isEmpty()) return

        val name = selfDisplayName(uid)
        for (channelId in targets) {
            // claim-before-send: 先记账再发。SDK 的 send 是 fire-and-forget (void, 无回调),
            // 拿不到发送结果, 所以没有回滚 —— 与"宁可漏发也不重发"的取舍一致。
            SummaryNotifyStore.markNotified(taskId, channelId)
            WKIM.getInstance().msgManager.send(
                SummaryTipContent(uid, name),
                WKChannel(channelId, WKChannelType.GROUP),
            )
        }
    }

    /** 对齐 octo-web collectGroupSourceIds(): 群聊来源; sources 为空时退回 origin channel。 */
    private fun groupSourceIds(detail: SummaryDetail): List<String> {
        val ids = LinkedHashSet<String>()
        for (source in detail.sources) {
            if (source.sourceType != SourceType.GroupChat) continue
            source.sourceId.trim().takeIf { it.isNotEmpty() }?.let(ids::add)
        }
        if (detail.sources.isEmpty() && detail.originChannelType == SourceType.GroupChat.raw) {
            detail.originChannelId?.trim()?.takeIf { it.isNotEmpty() }?.let(ids::add)
        }
        return ids.toList()
    }

    /**
     * 写进提示消息体的显示名, 对齐 Web 的 selfDisplayName() || name || uid。
     *
     * 收端优先用本地资料渲染 (见 WKSummaryNotifyProvider), 这里的名字只是本地资料
     * 缺失时的兜底, 所以不必追求精确。
     */
    private fun selfDisplayName(uid: String): String {
        val fromUserInfo = WKConfig.getInstance().userInfo?.name?.trim()
        if (!fromUserInfo.isNullOrEmpty()) return fromUserInfo
        val fromConfig = WKConfig.getInstance().userName?.trim()
        if (!fromConfig.isNullOrEmpty()) return fromConfig
        return uid
    }
}
