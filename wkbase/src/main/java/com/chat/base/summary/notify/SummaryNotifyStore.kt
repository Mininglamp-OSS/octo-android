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
 * 群总结完成提示的持久化去重账本, 对齐 octo-web groupSummaryNotify.ts 的 localStorage 部分。
 *
 * 两份数据:
 *   - [PENDING_KEY]  taskId → 登记时间戳。**只登记本机发起的任务** (创建 / 重新生成),
 *     这是"谁创建谁负责发"这条不变量的载体 —— 一旦改成"把自己创建的所有在途任务都跟上",
 *     多台设备会同时跟同一条任务, 立刻退化成必然重复。
 *   - [SENT_KEY]     taskId → 已通知过的 channelId 集合。claim-before-send 的记账处。
 *
 * 全部走 [WKSharedPreferencesUtil.putSPWithUID] 按 uid 隔离, 多账号切换不串台。
 * App 实质单进程 (只有腾讯 X5 的 :dexopt service 是独立进程, 不碰 SP), MODE_PRIVATE 够用。
 *
 * 写入是 best-effort: SP 异常时宁可漏发也不重发 —— 与 Web 同口径, 群里刷两条一样的
 * 提示比偶尔少一条难受得多。
 */
internal object SummaryNotifyStore {

    // 所有读改写方法都 @Synchronized: 轮询循环与多个 handleCompleted 协程跑在
    // Dispatchers.IO 的不同线程上, 会并发读改写同一份 SP JSON。不加锁时两个任务同时
    // markNotified 会丢更新 (各自读到旧集合、各自覆盖写回), 结果就是重复发送。

    private const val PENDING_KEY = "summary_tip_pending_v1"
    private const val SENT_KEY = "summary_tip_sent_v1"

    /**
     * 登记后超过这个时长仍未观测到完成, 直接丢弃不再发。
     *
     * 覆盖 "App 被杀 → 几小时后重启 → 翻出一条早已完成的旧任务" 这种场景: 那时再往群里
     * 补一条提示只会让人莫名其妙。Web 的同类标记是 10 分钟, 这里放宽到 30 分钟, 因为
     * Android 端更容易被切后台 / 回收, 需要更长的续跟窗口。
     */
    private const val PENDING_TTL_MILLIS = 30 * 60 * 1000L

    /** 已通知账本保留的任务数上限, 防止 SP 无限增长。 */
    private const val MAX_SENT_TASKS = 100

    // ===== pending =====

    /** 返回仍在 TTL 内的 pending taskId; 顺手把过期项清掉。 */
    @Synchronized
    fun activePendingTaskIds(nowMillis: Long): List<Long> {
        val json = readObject(PENDING_KEY)
        val alive = JSONObject()
        val ids = ArrayList<Long>()
        for (key in json.keys()) {
            val trackedAt = json.optLong(key, 0L)
            val taskId = key.toLongOrNull() ?: continue
            if (trackedAt <= 0L || nowMillis - trackedAt > PENDING_TTL_MILLIS) continue
            alive.put(key, trackedAt)
            ids.add(taskId)
        }
        if (alive.length() != json.length()) writeObject(PENDING_KEY, alive)
        return ids
    }

    @Synchronized
    fun addPending(taskId: Long, nowMillis: Long) {
        if (taskId <= 0L) return
        val json = readObject(PENDING_KEY)
        json.put(taskId.toString(), nowMillis)
        writeObject(PENDING_KEY, json)
    }

    @Synchronized
    fun removePending(taskId: Long) {
        val json = readObject(PENDING_KEY)
        if (!json.has(taskId.toString())) return
        json.remove(taskId.toString())
        writeObject(PENDING_KEY, json)
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

    /** claim: 发送**之前**先记账。失败再调 [unmarkNotified] 回滚。 */
    @Synchronized
    fun markNotified(taskId: Long, channelId: String) {
        val json = readObject(SENT_KEY)
        val channels = notifiedChannels(taskId).toMutableSet()
        if (!channels.add(channelId)) return
        json.put(taskId.toString(), JSONArray(channels.toList()))
        writeObject(SENT_KEY, trimToCap(json))
    }

    @Synchronized
    fun unmarkNotified(taskId: Long, channelId: String) {
        val json = readObject(SENT_KEY)
        val channels = notifiedChannels(taskId).toMutableSet()
        if (!channels.remove(channelId)) return
        if (channels.isEmpty()) json.remove(taskId.toString())
        else json.put(taskId.toString(), JSONArray(channels.toList()))
        writeObject(SENT_KEY, json)
    }

    // ===== io =====

    /**
     * 超出上限时按 taskId 从小到大丢弃 —— taskId 由后端自增, 数值小即更早创建,
     * 这等价于"丢最旧的", 且不需要额外存时间戳。
     */
    private fun trimToCap(json: JSONObject): JSONObject {
        if (json.length() <= MAX_SENT_TASKS) return json
        val ordered = json.keys().asSequence().toList()
            .sortedByDescending { it.toLongOrNull() ?: 0L }
            .take(MAX_SENT_TASKS)
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
