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

import com.chat.base.config.WKSharedPreferencesUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * 群总结完成提示的持久化去重账本。
 *
 * 两份数据:
 *   - [ELIGIBLE_KEY] 本机 [track] 过的 taskId 集合。**只登记本机发起的任务**
 *     (创建 / 重新生成), 这是"谁创建谁负责发"这条不变量唯一的判据 —— 不管观测到
 *     完成是靠状态跃变还是首次加载就是终态, 放行前都必须查这份账本命中。没有
 *     TTL: 同一个人在多端登录时, "任务处理超过 N 分钟" 不该变成"这台设备突然
 *     又发不出本该由它发的提示", 生命周期完全交给 [markEligible]/[clearEligible]
 *     显式管理, 不靠时间推断。
 *   - [SENT_KEY]     taskId → 已通知过的 channelId 集合。claim-before-send 的记账处。
 *
 * 全部走 [WKSharedPreferencesUtil.putSPWithUID] 按 uid 隔离, 多账号切换不串台。
 * App 实质单进程 (只有腾讯 X5 的 :dexopt service 是独立进程, 不碰 SP), MODE_PRIVATE 够用。
 *
 * 写入是 best-effort: SP 异常时宁可漏发也不重发 —— 群里刷两条一样的
 * 提示比偶尔少一条难受得多。
 */
internal object SummaryNotifyStore {

    // 所有读改写方法都 @Synchronized: 详情页 ViewModel 的 loadDetail 协程、通知助手卡片点击
    // 判定等入口都可能并发读改写同一份 SP JSON。[claimUnnotifiedChannels] 把 read + write
    // 合并到同一临界区, 避免两个调用者各自读到旧集合、各自覆盖写回导致的重复发送。

    private const val ELIGIBLE_KEY = "summary_tip_eligible_v2"
    private const val SENT_KEY = "summary_tip_sent_v1"

    /** eligible 账本保留的任务数上限, 防止 [clearEligible] 漏调时 SP 无限增长。 */
    private const val MAX_ELIGIBLE_TASKS = 100

    /** 已通知账本保留的任务数上限, 防止 SP 无限增长。 */
    private const val MAX_SENT_TASKS = 100

    // ===== eligible (本机发起过创建/重新生成的 taskId, "谁创建谁发" 的唯一判据) =====

    /** 本机发起 (创建 / 重新生成) 成功时登记。 */
    @Synchronized
    fun markEligible(taskId: Long) {
        if (taskId <= 0L) return
        val json = readObject(ELIGIBLE_KEY)
        json.put(taskId.toString(), true)
        writeObject(ELIGIBLE_KEY, trimToCap(json, MAX_ELIGIBLE_TASKS))
    }

    /**
     * 只读检查: taskId 是否是本机登记过的。跃变路径每一拍轮询都可能调用, 不消费——
     * 消费 (从账本移除) 是 [clearEligible] 的职责, 由调用方在真正不再需要这条登记时
     * (发送完成 / 任务进入非 Completed 终态) 显式调用。
     */
    @Synchronized
    fun isEligible(taskId: Long): Boolean {
        if (taskId <= 0L) return false
        return readObject(ELIGIBLE_KEY).has(taskId.toString())
    }

    /**
     * 这个 taskId 不再需要跟踪 (提示已经发出去, 或任务确认不会再产生提示需求) 时
     * 显式移除登记。不调用也不会错发 —— 只是账本多留一条, 靠 [MAX_ELIGIBLE_TASKS]
     * 兜底防止无限增长。
     */
    @Synchronized
    fun clearEligible(taskId: Long) {
        val json = readObject(ELIGIBLE_KEY)
        val key = taskId.toString()
        if (!json.has(key)) return
        json.remove(key)
        writeObject(ELIGIBLE_KEY, json)
    }

    // ===== sent =====

    @Synchronized
    fun notifiedChannels(taskId: Long): Set<String> {
        val arr = readObject(SENT_KEY).optJSONArray(taskId.toString()) ?: return emptySet()
        val set = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotEmpty() }?.let(set::add)
        }
        return set
    }

    /**
     * 原子版"读未通知 + 记账"：把 [notifiedChannels] 读取和记账写入合并到同一个
     * synchronized 临界区里, 避免调用方先 read 再逐个 write 时中间被并发调用者插入
     * 导致的 TOCTOU —— 两个协程 (例如详情页跃变判定 + 通知助手卡片点击几乎同时
     * 命中同一 taskId) 分别 read 到空集合、各自算出相同 targets、都发一遍, 就会在群里
     * 出现重复提示, 与"claim-before-send: 同 taskId 同群不重发"这条不变量矛盾。
     *
     * 返回值是这次真正抢到、且已经记账过的 channelId 子集 —— 调用方对这些 id 发送即可。
     * 无回滚: SDK 的 send 是 fire-and-forget, 拿不到发送结果, 与"宁可漏发也不重发"一致。
     */
    @Synchronized
    fun claimUnnotifiedChannels(taskId: Long, candidates: List<String>): List<String> {
        val already = notifiedChannels(taskId)
        val claimed = candidates.filterNot { already.contains(it) }
        if (claimed.isEmpty()) return claimed
        val json = readObject(SENT_KEY)
        val merged = (already + claimed).toMutableSet()
        json.put(taskId.toString(), JSONArray(merged.toList()))
        writeObject(SENT_KEY, trimToCap(json, MAX_SENT_TASKS))
        return claimed
    }

    // ===== io =====

    /**
     * 超出上限时按 taskId 从小到大丢弃 —— taskId 由后端自增, 数值小即更早创建,
     * 这等价于"丢最旧的", 且不需要额外存时间戳。两份账本 (eligible / sent) 共用。
     */
    private fun trimToCap(json: JSONObject, cap: Int): JSONObject {
        if (json.length() <= cap) return json
        val ordered = json.keys().asSequence().toList()
            .sortedByDescending { it.toLongOrNull() ?: 0L }
            .take(cap)
        val trimmed = JSONObject()
        for (key in ordered) trimmed.put(key, json.get(key))
        return trimmed
    }

    private fun readObject(key: String): JSONObject {
        val raw = WKSharedPreferencesUtil.getInstance().getSPWithUID(key)
        if (raw.isNullOrEmpty()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeObject(key: String, json: JSONObject) {
        try {
            WKSharedPreferencesUtil.getInstance().putSPWithUID(key, json.toString())
        } catch (_: Exception) {
            // best-effort: 写失败最坏结果是重复一条, 不影响主流程
        }
    }
}
