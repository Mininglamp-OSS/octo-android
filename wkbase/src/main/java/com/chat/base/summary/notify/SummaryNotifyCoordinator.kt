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

import android.net.Uri
import com.chat.base.config.WKConfig
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 群总结完成后往来源群发一条系统提示, 补上 "App 发起的总结群里没有任何提示" 这个缺口。
 *
 * ## 触发方式: 挂在详情页自身的轮询节奏上, 不用独立常驻协程
 *
 * 由 [com.chat.uikit.summary.detail.SmartSummaryDetailViewModel] 自身已有的 8s 轮询
 * (`loadDetail`/`schedulePollIfNeeded`) 在观测到 "非完成 → Completed" 状态跃变时调
 * [notifyIfNeeded]。通知助手卡片的 "/s/STxxx?sp=..." 深链点击也会在打开 WebView 前
 * 做一次同样的判定。
 *
 * ## 不变量: 谁创建, 谁负责发 —— 且判定权只归 eligible 账本
 *
 * [SummaryNotifyStore] 的 eligible 集合只在**本机发起**任务 (创建 / 重新生成) 时登记,
 * [notifyIfNeeded] 放行的唯一依据就是 taskId 命中这份账本, 不受"是否观测到状态跃变"
 * 影响 —— 同一个人可能在 Web 创建总结、又在手机上打开原生详情页停留到完成, 这台
 * 手机确实会亲眼看到 "Processing → Completed" 的跃变, 但因为它从未 `track()` 过这个
 * taskId, 依然不会发。`creatorId == 自己` 只是双保险, 不是唯一防线 —— 同一账号跨端
 * 登录时 creatorId 恒等于自己, 单靠它挡不住这类跨端重复。
 *
 * ## 与 Web 的重复: 当前不做防护
 *
 * 完成即发, 不等待、不检查频道内是否已有 Web 发出的同款提示。
 * 代价: 手机发起总结 + Web 同时开着这条总结的详情页停留到完成, 两端都会各发一条,
 * 群里出现两条一样的提示。这是已知且当前接受的取舍。
 */
object SummaryNotifyCoordinator {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: com.chat.base.summary.repository.SummaryRepository
        get() = com.chat.base.summary.SummaryDeps.repository

    /** 本机发起了一次总结 (创建 / 重新生成) 时登记, 是"谁创建谁发"的唯一判据。 */
    @JvmStatic
    fun track(taskId: Long) {
        SummaryNotifyStore.markEligible(taskId)
    }

    /** 数字 taskId 入口 (原生详情页跃变路径不会走这里; 保留给其他 fire-and-forget 入口)。 */
    @JvmStatic
    fun notifyByTaskIdIfEligible(taskId: Long) {
        if (taskId <= 0L) return
        appScope.launch {
            try {
                val detail = repository.getSummaryDetail(taskId).getOrNull() ?: return@launch
                notifyIfNeeded(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 提示是尽力而为: 异常不影响主流程
            }
        }
    }

    /**
     * 通过 task_no (ST 开头字符串, 通知助手卡片深链 "/s/STxxx?sp=..." 的格式) 触发判定。
     * 后端详情接口 GET /summaries/{id} 同时接受数字 taskId 和字符串 taskNo 作为路径参数
     * (resolveSummaryTaskParam 会在 ParseInt 失败时按 task_no 反查)。
     */
    @JvmStatic
    fun notifyByTaskNoIfEligible(taskNo: String) {
        val key = taskNo.trim()
        if (key.isEmpty()) return
        appScope.launch {
            try {
                val detail = repository.getSummaryDetailByNo(key).getOrNull() ?: return@launch
                notifyIfNeeded(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 提示是尽力而为: 异常不影响主流程
            }
        }
    }

    /**
     * 识别"总结详情页"URL, 返回用于查找详情的 key 类型和值。支持两种格式:
     *   - "/summary/detail?taskId=<数字>" (octo-web 内部路由)
     *   - "/s/<segment>" (通知助手卡片深链, 单段路径, 纯数字按 taskId 否则按 taskNo)
     *
     * 匹配失败返回 null。
     */
    @JvmStatic
    fun extractSummaryLookup(url: String): SummaryLookup? {
        if (url.isBlank()) return null
        return try {
            val uri = Uri.parse(url)
            val rawPath = (uri.path ?: return null).trimEnd('/')
            val path = if (rawPath.contains('#')) {
                rawPath.substringAfterLast('#').trimEnd('/')
            } else {
                rawPath
            }
            when {
                path.endsWith("/summary/detail") -> {
                    val tid = uri.getQueryParameter("taskId")?.trim()
                    val id = tid?.toLongOrNull()?.takeIf { it > 0L } ?: return null
                    SummaryLookup.ById(id)
                }
                path.matches(Regex("^/s/[^/]+$")) -> {
                    val segment = path.removePrefix("/s/")
                    val id = segment.toLongOrNull()
                    if (id != null && id > 0L) SummaryLookup.ById(id)
                    else SummaryLookup.ByNo(segment)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 标识一次卡片/URL 点击对应的查找 key 类型。 */
    sealed class SummaryLookup {
        data class ById(val taskId: Long) : SummaryLookup()
        data class ByNo(val taskNo: String) : SummaryLookup()
    }

    /**
     * 详情页 `loadDetail` 拿到 [detail] 后调用, 通知助手卡片点击也走这里。
     *
     * 放行条件唯一: taskId 命中 [SummaryNotifyStore.isEligible] (本机 [track] 过)。
     * 状态跃变 / 首次加载即终态都只是"什么时候来检查"的触发时机, 不参与放行判断本身——
     * 这样跨端同账号登录时, 另一端创建的任务不会因为这台设备恰好观测到了完成跃变就被放行。
     */
    @JvmStatic
    suspend fun notifyIfNeeded(detail: SummaryDetail) {
        val taskId = detail.taskId
        if (detail.status != TaskStatus.Completed) return
        if (!SummaryNotifyStore.isEligible(taskId)) return
        handleCompleted(detail)
    }

    private suspend fun handleCompleted(detail: SummaryDetail) {
        val taskId = detail.taskId
        val uid = WKConfig.getInstance().uid.orEmpty()
        if (uid.isEmpty()) return

        // 双保险: eligible 账本只登记本机发起的任务, 这里再按服务端权威字段确认一次创建者
        // 身份。creatorId 缺失时按"不是我"处理 —— 宁可漏发。
        if (detail.creatorId.isNullOrEmpty() || detail.creatorId != uid) return

        val channels = groupSourceIds(detail)
        if (channels.isEmpty()) return

        // 原子 claim: read-未通知 + 记账合并到一次 synchronized 调用, 避免两个协程
        // (详情页跃变判定 / 通知助手卡片点击) 几乎同时命中同一 taskId 时, 分别读到
        // 空集合、各自把同一批 channelId 判定为"未通知"而重复发送。
        val targets = SummaryNotifyStore.claimUnnotifiedChannels(taskId, channels)
        if (targets.isEmpty()) return

        val name = selfDisplayName(uid)
        for (channelId in targets) {
            // SDK 的 send 是 fire-and-forget (void, 无回调), 拿不到发送结果, 所以没有回滚
            // —— 与"宁可漏发也不重发"的取舍一致 (记账已经在 claimUnnotifiedChannels 里完成)。
            WKIM.getInstance().msgManager.send(
                SummaryTipContent(uid, name),
                WKChannel(channelId, WKChannelType.GROUP),
            )
        }
        // 这次该发的都发完了, 这个 taskId 不会再产生提示需求 (状态已经是终态 Completed),
        // 从 eligible 账本移除, 避免长期占位。
        SummaryNotifyStore.clearEligible(taskId)
    }

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

    private fun selfDisplayName(uid: String): String {
        val fromUserInfo = WKConfig.getInstance().userInfo?.name?.trim()
        if (!fromUserInfo.isNullOrEmpty()) return fromUserInfo
        val fromConfig = WKConfig.getInstance().userName?.trim()
        if (!fromConfig.isNullOrEmpty()) return fromConfig
        return uid
    }
}
