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
        // 1:1 对齐 iOS [OctoSummaryAPI init]: 取 scheme://host[:port] 部分,
        // 丢掉 baseUrl 上的任何 path (例如 /api/v1/), 再拼 /summary/api/v1/。
        // 项目实际 baseUrl 是 "https://im.deepminer.com.cn/api/v1/" 这种带 /api 中段
        // 的形式, 直接 removeSuffix("/v1/") 会保留 /api 段, 拼出 /api/summary/api/v1/
        // 命中后端 404。这里用 java.net.URL 解析 host, 与 iOS NSURL 取法一致。
        val origin = runCatching {
            val u = java.net.URL(base)
            val portPart = if (u.port > 0) ":${u.port}" else ""
            "${u.protocol}://${u.host}$portPart"
        }.getOrNull() ?: base.removeSuffix("/v1/").removeSuffix("/v1")
        val resolved = "$origin/summary/api/v1/"
        android.util.Log.i("SummaryApi", "baseUrl resolved: source=$base -> resolved=$resolved")
        return resolved
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
