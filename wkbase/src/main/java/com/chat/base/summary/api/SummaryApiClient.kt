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

import com.chat.base.config.WKApiConfig
import com.chat.base.net.FastJsonConverterFactory
import com.chat.base.net.OkHttpUtils
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * 单独一份 Retrofit 实例:
 *   baseUrl = `<apiURL>/summary/api/v1/` (从 [WKApiConfig.baseUrl] 派生,
 *   去掉末尾 "/v1/" 重新拼)
 *   client  = 共享 OkHttp + 在 builder 上叠 [AcceptLanguageInterceptor]
 *   converter = FastJson (项目自家)
 *
 * 不修改 [WKApiConfig] / 共享 OkHttpClient, summary 模块完全自闭包。
 *
 * baseUrl 切换 (登录前后, IncludeIP 路径) 后通过 [reset] 重建。
 */
object SummaryApiClient {

    @Volatile
    private var service: SummaryApiService? = null

    @Volatile
    private var lastBaseUrl: String = ""

    fun get(): SummaryApiService {
        val resolved = resolveBaseUrl()
        val cached = service
        if (cached != null && resolved == lastBaseUrl) return cached
        synchronized(this) {
            val cur = service
            if (cur != null && resolved == lastBaseUrl) return cur
            val built = build(resolved)
            service = built
            lastBaseUrl = resolved
            return built
        }
    }

    fun reset() {
        synchronized(this) {
            service = null
            lastBaseUrl = ""
        }
    }

    private fun resolveBaseUrl(): String {
        val base = WKApiConfig.baseUrl.orEmpty()
        if (base.isEmpty()) return ""
        // 把末尾 "/v1/" 替换为 "/summary/api/v1/"
        val trimmed = base.removeSuffix("/v1/").removeSuffix("/v1")
        return if (trimmed.endsWith("/")) "${trimmed}summary/api/v1/" else "$trimmed/summary/api/v1/"
    }

    private fun build(baseUrl: String): SummaryApiService {
        val shared: OkHttpClient = OkHttpUtils.getInstance().okHttpClient
        val client = shared.newBuilder()
            .addInterceptor(AcceptLanguageInterceptor())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(FastJsonConverterFactory.create())
            .build()
        return retrofit.create(SummaryApiService::class.java)
    }
}
