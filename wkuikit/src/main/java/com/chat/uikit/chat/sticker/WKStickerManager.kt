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
import com.chat.base.utils.WKToastUtils
import com.chat.base.R as BaseR
import com.xinbida.wukongim.entity.WKMsg

/**
 * 用户收藏贴纸的内存缓存 + API 网关。全局单例。
 *
 * 数据流：
 *   App 启动 / 登录成功 → [load] 从服务端拉取当前用户全部收藏
 *   收到贴图消息长按 "添加到我的表情" → [collect] POST /sticker/user/collect
 *   表情面板 "我的贴图" tab → 观察 [stickersLiveData] 展示 grid
 *   长按菜单展示前 → [isCollected] 判断是否已收藏（已收藏则不出菜单）
 *
 * 幂等：服务端 collect 用 SHA256(source_path) 唯一键去重，重复收藏返回已存在
 * 记录，不重复消耗配额（默认 100 张 / 用户），Android 侧不做前置去重，交给
 * 服务端兜底。
 *
 * 未实现：上传自己的贴纸（需要 /v1/file/upload?type=sticker 流程 + handle 签名，
 * 当前 Android 只做"从消息收藏"链路，如需上传后续再加）。
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

    /** 主线程可观察的收藏列表。UI 层通过 observe / value 读。 */
    val stickersLiveData: MutableLiveData<List<WKSticker>> = MutableLiveData(emptyList())

    /**
     * 拉取服务端最新收藏列表并刷新缓存。空集合返回 {"list":[]} 而非 404，视为成功。
     * 距上次成功 <30s 的调用会跳过网络；[force]=true 用于用户主动下拉刷新等场景绕过。
     */
    @JvmOverloads
    fun load(force: Boolean = false) {
        if (!force && SystemClock.uptimeMillis() - lastLoadedAtMs < LOAD_MIN_INTERVAL_MS) {
            return
        }
        request(service.getMyStickers(), object : IRequestResultListener<ListStickerResp> {
            override fun onSuccess(result: ListStickerResp?) {
                val list = result?.list ?: emptyList()
                cache = list
                lastLoadedAtMs = SystemClock.uptimeMillis()
                stickersLiveData.value = list
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
                // 幂等：如果服务端返回的是已存在记录，避免重复追加。
                val updated = cache.filterNot { it.sticker_id == result.sticker_id } + result
                cache = updated
                stickersLiveData.value = updated
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
