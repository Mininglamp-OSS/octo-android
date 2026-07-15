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

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import com.alibaba.fastjson.JSONObject
import com.chat.base.base.WKBaseModel
import com.chat.base.msg.model.WKVectorStickerContent
import com.chat.base.net.IRequestResultListener
import com.chat.base.net.entity.CommonResponse
import com.chat.base.utils.WKToastUtils
import com.chat.base.R as BaseR
import com.xinbida.wukongim.entity.WKMsg

/**
 * 用户收藏贴纸的内存缓存 + API 网关。全局单例。
 *
 * 数据流：
 *   App 启动 / 登录成功 → [load] 从服务端拉取当前用户全部收藏
 *   收到贴图消息长按 "添加到我的表情" → [collect] POST /sticker/user/collect
 *   编辑态点 × → [delete] DELETE /sticker/user/{id}
 *   编辑态拖拽结束 → [reorder] 本地顺序持久化
 *   上传自己贴纸 → [WKStickerUploader]（走 POST /sticker/user）
 *   表情面板 "我的贴图" tab → 观察 [stickersLiveData] 展示 grid
 *
 * 幂等：服务端 collect 用 SHA256(source_path) 唯一键去重，重复收藏返回已存在
 * 记录，不重复消耗配额（默认 100 张 / 用户），Android 侧不做前置去重，交给
 * 服务端兜底。
 *
 * 本地顺序：服务端 sort 字段由后端维护但无重排接口，Android 侧用
 * [StickerLocalOrderStore] 让用户拖拽调整的顺序本地稳定复现（对齐 iOS）。
 */
object WKStickerManager : WKBaseModel() {

    private val service by lazy { createService(StickerService::class.java) }

    /** 两次成功 [load] 之间的最小间隔。UI 层可以随手在 tab 切换 / 面板打开时调 load()，
     *  短时间内的重复调用会被 short-circuit（快速切 tab / 反复打开面板不会打网络）。 */
    private const val LOAD_MIN_INTERVAL_MS = 30_000L

    @Volatile
    private var cache: List<WKSticker> = emptyList()

    @Volatile
    private var lastLoadedAtMs: Long = 0L

    /** 主线程可观察的收藏列表（已按 [StickerLocalOrderStore] 排序）。UI 层通过 observe / value 读。 */
    val stickersLiveData: MutableLiveData<List<WKSticker>> = MutableLiveData(emptyList())

    /**
     * 拉取服务端最新收藏列表并刷新缓存。空集合返回 {"list":[]} 而非 404，视为成功。
     * 距上次成功 <30s 的调用会跳过网络；[force]=true 用于用户主动下拉刷新等场景绕过。
     *
     * 成功后：
     * 1. 按本地顺序表重排（登记过的前置，其他追加）
     * 2. 修剪本地顺序表中服务端已不存在的 id（防表越涨越大）
     */
    @JvmOverloads
    fun load(force: Boolean = false) {
        if (!force && SystemClock.uptimeMillis() - lastLoadedAtMs < LOAD_MIN_INTERVAL_MS) {
            return
        }
        request(service.getMyStickers(), object : IRequestResultListener<ListStickerResp> {
            override fun onSuccess(result: ListStickerResp?) {
                val serverList = result?.list ?: emptyList()
                StickerLocalOrderStore.prune(serverList.mapTo(mutableSetOf()) { it.sticker_id })
                val ordered = StickerLocalOrderStore.applyOrder(serverList)
                cache = ordered
                lastLoadedAtMs = SystemClock.uptimeMillis()
                stickersLiveData.value = ordered
            }

            override fun onFail(code: Int, msg: String?) {
                // 静默失败：面板 tab 仍能显示已缓存的（可能是空），下次 load 重试。
                // 失败不更新 lastLoadedAtMs，下次调用不会被节流吞掉。
            }
        })
    }

    /** 长按 chat 里的贴图消息 → 收藏到"我的表情"。 */
    fun collect(msg: WKMsg) {
        val content = msg.baseContentMsgModel as? WKVectorStickerContent ?: return
        val path = content.url
        if (path.isNullOrEmpty()) return

        val body = JSONObject()
        body["path"] = path
        // placeholder 服务端会自动补默认值 "[表情]"，Android 不做前置指定；
        // width/height/category/format/handle 服务端不接（会被忽略或校验失败）。

        request(service.collectSticker(body), object : IRequestResultListener<WKSticker> {
            override fun onSuccess(result: WKSticker?) {
                if (result == null) return
                onStickerAdded(result)
                WKToastUtils.getInstance().showToastNormal(
                    getString(BaseR.string.str_sticker_add_success)
                )
            }

            override fun onFail(code: Int, msg: String?) {
                // 服务端 400 body 里可能含 `quota_exceeded` 等 code，简化处理直接用 msg
                val fallback = getString(BaseR.string.str_sticker_add_failed)
                WKToastUtils.getInstance().showToastNormal(
                    if (msg.isNullOrEmpty()) fallback else msg
                )
            }
        })
    }

    /**
     * 收藏 / 上传成功后往缓存里追加一条。已存在的按 sticker_id 覆盖（更新元数据），
     * 新增的追加到本地顺序末尾。呼叫方无须自己维护 LiveData。
     */
    fun onStickerAdded(sticker: WKSticker) {
        val existed = cache.any { it.sticker_id == sticker.sticker_id }
        val updated = cache.filterNot { it.sticker_id == sticker.sticker_id } + sticker
        cache = updated
        if (!existed) StickerLocalOrderStore.append(sticker.sticker_id)
        stickersLiveData.value = StickerLocalOrderStore.applyOrder(updated)
    }

    /**
     * 删除一条已收藏的贴纸。成功后从缓存 + 本地顺序表 + LiveData 移除。
     * 失败：吐 [onFail] 里的服务端 msg（或默认 "删除失败"），不改缓存。
     */
    fun delete(stickerId: String, onDone: ((Boolean) -> Unit)? = null) {
        if (stickerId.isEmpty()) {
            onDone?.invoke(false)
            return
        }
        request(service.deleteSticker(stickerId), object : IRequestResultListener<CommonResponse> {
            override fun onSuccess(result: CommonResponse?) {
                val updated = cache.filterNot { it.sticker_id == stickerId }
                cache = updated
                StickerLocalOrderStore.remove(stickerId)
                stickersLiveData.value = updated
                WKToastUtils.getInstance().showToastNormal(
                    getString(BaseR.string.str_sticker_delete_success)
                )
                onDone?.invoke(true)
            }

            override fun onFail(code: Int, msg: String?) {
                val fallback = getString(BaseR.string.str_sticker_delete_failed)
                WKToastUtils.getInstance().showToastNormal(
                    if (msg.isNullOrEmpty()) fallback else msg
                )
                onDone?.invoke(false)
            }
        })
    }

    /**
     * 拖拽结束后写入新顺序。传入应为当前面板可视顺序的 sticker_id 列表。
     * 不打网络（服务端无重排接口），只落本地 SP + LiveData 发新值。
     *
     * ⚠️ [StickerLocalOrderStore.write] 走 `apply()` 异步落盘，同一 tick 立刻 `read()`
     * 可能拿到旧值 → applyOrder 用旧顺序重排 → LiveData 发出旧顺序 →
     * 观察者 submitList(旧序) → notifyDataSetChanged → 视觉反弹回旧序。
     * 所以这里必须用参数 [orderedIds] 直接排序，不走 SP 读回路径——契约测试见
     * `WKStickerManagerReorderTest`。
     */
    fun reorder(orderedIds: List<String>) {
        StickerLocalOrderStore.write(orderedIds)
        cache = reorderCache(orderedIds, cache)
        stickersLiveData.value = cache
    }

    /**
     * 纯函数版本：拿参数 [orderedIds] 直接排 [current]，不读任何持久化状态。
     *
     * 抽出成 `internal` 可见的原因：`reorder()` 会写 SP、发 LiveData——JVM 单测
     * 测不到；但"结果只由参数决定，不从 SP 回读"这个 race-free 契约必须锁死，
     * 否则回退成 `applyOrder(read(), cache)` 会让所有现有 [StickerLocalOrderStoreTest]
     * 依然通过。见 `WKStickerManagerReorderTest.reorderCache_uses_only_params_no_persisted_read`。
     */
    @JvmStatic
    internal fun reorderCache(orderedIds: List<String>, current: List<WKSticker>): List<WKSticker> =
        StickerLocalOrderStore.applyOrder(orderedIds, current)

    /** 长按菜单判断：已收藏则不出 "添加到我的表情"（对齐 iOS 的行为）。
     *
     * 使用 [StickerUrlUtils.normalizePath] 归一化再比：服务端可能对同一贴图返回
     * 绝对/相对 URL 混用，直接 `==` 会漏判为"未收藏"，菜单误显示 → 用户点后拿到
     * 服务端"已存在"记录，列表不更新看起来像"没生效"。
     */
    fun isCollected(url: String?): Boolean {
        val target = StickerUrlUtils.normalizePath(url) ?: return false
        return cache.any { StickerUrlUtils.normalizePath(it.path) == target }
    }

    private fun getString(resId: Int): String {
        return com.chat.base.WKBaseApplication.getInstance().context.getString(resId)
    }
}
