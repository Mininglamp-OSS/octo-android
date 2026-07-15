/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import android.content.Context
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import org.json.JSONArray

/**
 * 自定义贴纸的本地顺序表。
 *
 * 服务端 stickerResp.sort 字段由后端维护，但服务端不暴露"批量重排"接口
 * （iOS 用的 /sticker/user/front 服务端未实现，见 memory 记录）。为了让用户
 * 拖拽调整的顺序在本地稳定复现（对齐 iOS 行为），Android 侧把重排结果落在
 * 本地 SharedPreferences 里。
 *
 * <h3>数据格式</h3>
 * JSON 数组 `["stickerId1", "stickerId2", ...]`，先出现的排在前面。列表加载后：
 * - 在本表登记过的 id 按此顺序前置
 * - 未登记的（如后台其他端新增）按服务端返回顺序追加尾部
 *
 * <h3>多用户</h3>
 * SP 文件按 uid 命名：`sticker_order_{uid}`，切换账号自动隔离。
 * 未登录 (uid == null) 时读写空操作。
 *
 * <h3>并发</h3>
 * 单例、SP 操作走系统内置线程安全；本类不额外加锁。所有 mutator 走 apply()
 * 异步落盘（同 [com.chat.base.config.WKSharedPreferencesUtil] 的选型理由）。
 */
object StickerLocalOrderStore {

    private const val PREF_PREFIX = "sticker_order_"
    private const val KEY_ORDER = "order"

    private fun prefsOrNull(): android.content.SharedPreferences? {
        val uid = WKConfig.getInstance().uid
        if (uid.isNullOrEmpty()) return null
        val ctx = WKBaseApplication.getInstance().context ?: return null
        return ctx.getSharedPreferences(PREF_PREFIX + uid, Context.MODE_PRIVATE)
    }

    /** 读取当前用户的本地顺序。列表为空 = 从未登记过 = 完全按服务端顺序。 */
    fun read(): List<String> {
        val raw = prefsOrNull()?.getString(KEY_ORDER, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrElse { emptyList() }
    }

    /** 全量覆盖当前用户的本地顺序（拖拽结束时调）。 */
    fun write(ids: List<String>) {
        val prefs = prefsOrNull() ?: return
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString(KEY_ORDER, arr.toString()).apply()
    }

    /** 追加一个 id 到本地顺序末尾（收藏成功时调）。已存在则不动。 */
    fun append(id: String) {
        if (id.isEmpty()) return
        val current = read()
        if (current.contains(id)) return
        write(current + id)
    }

    /** 从本地顺序移除一个 id（删除成功时调）。 */
    fun remove(id: String) {
        if (id.isEmpty()) return
        val current = read()
        if (!current.contains(id)) return
        write(current.filterNot { it == id })
    }

    /** 与服务端返回的 id 集合求交集，剔除本地存在但服务端已删的 id。
     *  在 [WKStickerManager.load] 成功后调用，防止本地表越涨越大。 */
    fun prune(validIds: Set<String>) {
        val current = read()
        val pruned = current.filter { it in validIds }
        if (pruned.size != current.size) write(pruned)
    }

    /**
     * 按本地顺序对服务端返回列表排序：登记过的 id 按本地顺序前置，
     * 未登记的按 [serverList] 原顺序追加尾部（这是"其他端刚加的"或"从未拖过的"）。
     */
    fun applyOrder(serverList: List<WKSticker>): List<WKSticker> {
        return applyOrder(read(), serverList)
    }

    /** 纯函数版本（测试用）：给定顺序表和服务端列表，返回排序后列表。 */
    @JvmStatic
    fun applyOrder(orderedIds: List<String>, serverList: List<WKSticker>): List<WKSticker> {
        if (orderedIds.isEmpty()) return serverList

        val byId = serverList.associateBy { it.sticker_id }
        val registered = orderedIds.mapNotNull { byId[it] }
        val registeredIds = registered.mapTo(mutableSetOf()) { it.sticker_id }
        val tail = serverList.filterNot { it.sticker_id in registeredIds }
        return registered + tail
    }

    /** 纯函数版本（测试用）：给定原顺序表与合法 id 集，返回修剪后顺序表。 */
    @JvmStatic
    fun pruneOrder(orderedIds: List<String>, validIds: Set<String>): List<String> =
        orderedIds.filter { it in validIds }
}
