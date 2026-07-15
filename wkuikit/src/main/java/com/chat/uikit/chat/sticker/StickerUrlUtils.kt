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

/**
 * 贴图 URL 后缀识别 —— 决定 provider / 面板 adapter 用哪种加载策略。
 *
 * 服务端 sticker 表的 format 白名单只含静态图（gif/png/jpg/jpeg/webp），
 * 但历史消息 / iOS 侧 lottie 贴图 URL 是 .lim（Lottie JSON），Android 未引入
 * lottie 播放能力，`.lim / .json` 归为 lottie 走占位图降级。
 */
object StickerUrlUtils {

    private val STATIC_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif")
    private val LOTTIE_EXTS = setOf("lim", "json")

    /** 是否是 Glide 能直接渲染的静态/动图（含 gif）。 */
    fun isStaticImage(url: String?): Boolean {
        val ext = extractExt(url) ?: return false
        return ext in STATIC_EXTS
    }

    /** 是否是 lottie 格式（Android 侧走占位）。 */
    fun isLottieFormat(url: String?, format: String?): Boolean {
        if (!format.isNullOrEmpty() && format.equals("lim", ignoreCase = true)) return true
        val ext = extractExt(url) ?: return false
        return ext in LOTTIE_EXTS
    }

    /** 从 URL 抽取小写扩展名（不含点），去掉 query/fragment。null 表示无法识别。 */
    fun extractExt(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val cleanEnd = url.indexOfAny(charArrayOf('?', '#')).let {
            if (it < 0) url.length else it
        }
        val path = url.substring(0, cleanEnd)
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        if (dot <= slash || dot == path.length - 1) return null
        return path.substring(dot + 1).lowercase()
    }

    /**
     * 归一化到"可对比"的 URL 形式：剥离 scheme+host+query/fragment，只留路径部分。
     *
     * 服务端可能返回绝对 `https://cdn/xxx/abc.png` 或相对 `/xxx/abc.png`
     * （见 [com.chat.base.config.WKApiConfig.getShowUrl] 兼容逻辑）。消息 wire
     * 和 `GET /v1/sticker/user` 缓存返回的 URL 格式并不保证一致，直接 `==` 比会漏判：
     * 已收藏的贴图仍误显示"添加到我的表情"菜单，点后服务端 SHA256 去重返回已存在记录，
     * 用户看到成功提示但列表没变化——很怪。用归一化的路径部分比较即可稳。
     *
     * 空串 / null 返回 null。
     */
    fun normalizePath(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val cleanEnd = url.indexOfAny(charArrayOf('?', '#')).let {
            if (it < 0) url.length else it
        }
        val cleaned = url.substring(0, cleanEnd)
        val lower = cleaned.lowercase()
        val schemeIdx = lower.indexOf("://")
        if (schemeIdx < 0) return cleaned.ifEmpty { null }
        val afterScheme = cleaned.substring(schemeIdx + 3)
        val slashIdx = afterScheme.indexOf('/')
        return if (slashIdx < 0) "" else afterScheme.substring(slashIdx)
    }
}
