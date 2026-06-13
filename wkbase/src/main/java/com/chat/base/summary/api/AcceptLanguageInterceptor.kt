/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.api

import com.chat.base.utils.language.WKLanguageType
import com.chat.base.utils.language.WKMultiLanguageUtil
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

/**
 * 仅 summary Retrofit 实例使用,不污染全局 OkHttpClient。
 *
 * 与 iOS [OctoSummaryAPI applyCommonHeaders] 的 Accept-Language 映射保持一致:
 *   zh-Hans → "zh-CN,zh;q=0.9,en;q=0.8"
 *   zh-Hant → "zh-TW,zh;q=0.9,en;q=0.8"
 *   其它   → "en-US,en;q=0.9"
 *
 * 优先读用户在设置里选的语言类型 [WKMultiLanguageUtil.getLanguageType],跟随系统时
 * 回退到 sysLocale。
 *
 * Token 与 X-Space-Id 由 [com.chat.base.net.CommonRequestParamInterceptor] 在
 * 共享 OkHttpClient 上自动注入,这里不重复处理。
 */
class AcceptLanguageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        builder.removeHeader(HEADER)
        builder.addHeader(HEADER, languageHeaderValue())
        return chain.proceed(builder.build())
    }

    private fun languageHeaderValue(): String {
        val type = runCatching { WKMultiLanguageUtil.getInstance().languageType }.getOrDefault(
            WKLanguageType.LANGUAGE_FOLLOW_SYSTEM
        )
        return when (type) {
            WKLanguageType.LANGUAGE_EN -> EN
            WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED -> ZH_CN
            WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL -> ZH_TW
            else -> systemLocaleHeader()
        }
    }

    private fun systemLocaleHeader(): String {
        val locale: Locale = runCatching { WKMultiLanguageUtil.getInstance().sysLocale }
            .getOrNull() ?: Locale.SIMPLIFIED_CHINESE
        val lang = locale.language?.lowercase().orEmpty()
        val country = locale.country?.uppercase().orEmpty()
        return when {
            lang == "zh" && (country == "TW" || country == "HK" || country == "MO") -> ZH_TW
            lang == "zh" -> ZH_CN
            else -> EN
        }
    }

    companion object {
        const val HEADER = "Accept-Language"
        private const val ZH_CN = "zh-CN,zh;q=0.9,en;q=0.8"
        private const val ZH_TW = "zh-TW,zh;q=0.9,en;q=0.8"
        private const val EN = "en-US,en;q=0.9"
    }
}
