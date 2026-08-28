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
import com.chat.base.msgitem.WKContentType
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.chat.base.summary.repository.SummaryRepository
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.utils.DateUtils
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
 * ## 与 Web 的重复怎么防
 *
 * 去重账本存在各端本地 (Web 在 localStorage, 这里在 SP), 互相看不见, 所以没法靠账本
 * 协调。但**提示本身就是一条落库的群消息, 两端都看得见** —— 用频道当共享状态:
 * 观测到完成后先等 [PEER_TIP_GRACE_MILLIS], 再查本地库看这个群里是不是已经有本人发出的
 * 总结提示了; 有就不发。
 *
 * 延时取 8s 的依据 (Web 从任务完成到消息落到本机的耗时):
 *   - SSE 推送路径 (按人总结): 亚秒级
 *   - 列表页 2s 轮询广播: ≤2s
 *   - 详情页兜底轮询: 5s 延迟启动 + 15s 间隔, 最坏 ~17s  ← **不覆盖**
 * 再叠加本类 [POLL_INTERVAL_MILLIS]=10s 的轮询本身就比 Web 晚 0~10s 发现完成, 实际
 * 判定点落在完成后 8~18s, 前两条主路径留了足够余量。第三条长尾 (只开详情页 + 非按人
 * 模式 + 同一总结手机也发起了) 概率极低, 接受偶发重复。
 *
 * 这是概率性防护不是锁: 两端的"检查"和"发送"之间没有原子性。Android 单方面做不到消除,
 * 消除需要 Web 也遵守某种协议 (例如只发本浏览器创建的任务) 或干脆由服务端下发。
 */
object SummaryNotifyCoordinator {

    // TODO(排查完成后删除): 临时诊断日志, 定位"停在群聊页面也不发提示"这个问题
    // 具体卡在 handleCompleted 的哪一步。
    private const val TAG = "SummaryNotify"

    /** 轮询在途任务的间隔。比 Web 慢一拍是刻意的, 见类注释里的延时依据。 */
    private const val POLL_INTERVAL_MILLIS = 10_000L

    /** 观测到完成后, 留给 Web 的提示送达本机的宽限期。 */
    private const val PEER_TIP_GRACE_MILLIS = 8_000L

    /** 扫描频道内提示消息的条数上限。提示很稀疏, 20 条足够覆盖回溯窗口。 */
    private const val PEER_TIP_SCAN_LIMIT = 20

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
        android.util.Log.i(TAG, "track taskId=$taskId")
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
        android.util.Log.i(TAG, "pollLoop started")
        while (currentCoroutineContext().isActive) {
            pendingChanged.set(false)

            // 未登录: 停掉循环, 登录后由 track 拉起。SP 键按 uid 隔离, 不会串账。
            if (WKConfig.getInstance().uid.isNullOrEmpty()) {
                android.util.Log.i(TAG, "pollLoop: not logged in, idling")
                if (finishLoopIfIdle()) return
                delay(POLL_INTERVAL_MILLIS)
                continue
            }

            val pending = SummaryNotifyStore.activePendingTaskIds(System.currentTimeMillis())
            android.util.Log.i(TAG, "pollLoop tick pending=$pending")
            if (pending.isEmpty()) {
                if (finishLoopIfIdle()) return
                delay(POLL_INTERVAL_MILLIS)
                continue
            }

            val batch = repository.batchStatus(pending)
            android.util.Log.i(TAG, "batchStatus result=$batch")
            batch.getOrNull()?.forEach { item ->
                when (item.status) {
                    TaskStatus.Completed -> {
                        android.util.Log.i(TAG, "taskId=${item.taskId} completed, handling")
                        // 先摘出 pending 再异步处理: 处理链路里有 8s 等待, 不能占着轮询。
                        SummaryNotifyStore.removePending(item.taskId)
                        launchCompletionHandling(item.taskId)
                    }
                    TaskStatus.Failed, TaskStatus.Cancelled -> {
                        android.util.Log.i(TAG, "taskId=${item.taskId} terminal status=${item.status}, dropping")
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
            android.util.Log.i(TAG, "handleCompleted taskId=$taskId: no uid, abort")
            return
        }

        val detailResult = repository.getSummaryDetail(taskId)
        android.util.Log.i(TAG, "handleCompleted taskId=$taskId: detail=$detailResult")
        val detail = detailResult.getOrNull() ?: return
        android.util.Log.i(
            TAG,
            "handleCompleted taskId=$taskId: creatorId=${detail.creatorId} uid=$uid " +
                "sources=${detail.sources} originChannelId=${detail.originChannelId} " +
                "originChannelType=${detail.originChannelType}",
        )
        // 双保险: SP 只登记本机发起的任务, 这里再按服务端权威字段确认一次创建者身份。
        // creatorId 缺失时按"不是我"处理 —— 宁可漏发。
        if (detail.creatorId.isNullOrEmpty() || detail.creatorId != uid) {
            android.util.Log.i(TAG, "handleCompleted taskId=$taskId: creatorId mismatch, abort")
            return
        }

        val channels = groupSourceIds(detail)
        android.util.Log.i(TAG, "handleCompleted taskId=$taskId: groupChannels=$channels")
        if (channels.isEmpty()) return

        val notified = SummaryNotifyStore.notifiedChannels(taskId)
        val targets = channels.filterNot { notified.contains(it) }
        android.util.Log.i(TAG, "handleCompleted taskId=$taskId: notified=$notified targets=$targets")
        if (targets.isEmpty()) return

        // 宽限期只等一次, 不是每个群各等一次 —— 多群来源时不该把提示拖成 N × 8s。
        // "开始等待"这一刻的时间戳是 peerTipExists 的判定基准, 见该函数注释里的踩坑记录。
        val waitStartedAtSeconds = DateUtils.getInstance().currentSeconds
        delay(PEER_TIP_GRACE_MILLIS)

        val name = selfDisplayName(uid)
        for (channelId in targets) {
            if (peerTipExists(channelId, uid, waitStartedAtSeconds)) {
                android.util.Log.i(TAG, "handleCompleted taskId=$taskId channel=$channelId: peer tip exists, skip")
                // Web 已经发过了。同样记账, 免得下次任务重试时又白等一轮宽限期。
                SummaryNotifyStore.markNotified(taskId, channelId)
                continue
            }
            android.util.Log.i(TAG, "handleCompleted taskId=$taskId channel=$channelId: sending, name=$name")
            // claim-before-send: 先记账再发。SDK 的 send 是 fire-and-forget (void, 无回调),
            // 拿不到发送结果, 所以没有回滚 —— 与"宁可漏发也不重发"的取舍一致。
            SummaryNotifyStore.markNotified(taskId, channelId)
            WKIM.getInstance().msgManager.send(
                SummaryTipContent(uid, name),
                WKChannel(channelId, WKChannelType.GROUP),
            )
        }
    }

    /**
     * 这个群里是不是已经有本人发出的总结提示了 —— 只看**宽限期等待开始之后**才出现的。
     *
     * 同时匹配 [WKContentType.summaryTip] (2000, 新) 与 [WKContentType.summaryNotify]
     * (21, Web 旧版本), 因为线上 Web 可能还没升到 2000。
     *
     * ## 踩过的坑: 曾经用固定回溯窗口 (10 分钟), 把"自己上一次总结"当成了"Web 抢发"
     *
     * 提示消息体里**不带 task_id**, 只能按 (频道 + 发送人 + 类型 + 时间) 匹配, 天生无法
     * 区分"这条提示属于哪次总结"。固定回溯窗口的问题: 连续对同一个群做两次总结, 只要
     * 间隔小于窗口, 第二次的检查会把第一次自己发的提示误判成"Web 已经发过了", 直接跳过
     * 发送 —— 真机复现: 两次总结间隔 94 秒, 10 分钟窗口下第二条被吞。
     *
     * 改成以 [waitStartedAtSeconds] (进入宽限期那一刻的时间戳) 为下界: 宽限期开始之前
     * 就存在的消息 (不管是自己上次发的还是 Web 更早发的) 一律不算数, 只有等待期间
     * **新出现**的才可能是 Web 这次抢发的。
     *
     * 仍然无法区分"宽限期内新出现的这条是不是恰好也是自己另一台设备刚发的" —— 但那本来
     * 就是 [SummaryNotifyStore] 记账要防的范畴, 不是本函数的职责; 本函数只负责"和 Web
     * 撞车"这一种情况。
     */
    private fun peerTipExists(channelId: String, uid: String, waitStartedAtSeconds: Long): Boolean {
        val msgs = WKIM.getInstance().msgManager.searchMsgWithChannelAndContentTypes(
            channelId,
            WKChannelType.GROUP,
            0,
            PEER_TIP_SCAN_LIMIT,
            intArrayOf(WKContentType.summaryTip, WKContentType.summaryNotify),
        ) ?: return false
        return msgs.any { it != null && it.fromUID == uid && it.timestamp >= waitStartedAtSeconds }
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
