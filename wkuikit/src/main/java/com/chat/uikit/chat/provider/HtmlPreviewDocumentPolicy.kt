/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.chat.uikit.chat.provider

import java.util.UUID

internal object HtmlPreviewDocumentPolicy {
    // 保留内联脚本/样式，同时限制外部资源、Fetch/XHR/WebSocket、worker 等能力。
    // connect-src 和 sandbox 不保证阻断 WebRTC/P2P；此策略不承诺附件完全离线。
    // 与 iOS 对齐的是 HTML 渲染和脚本交互需求，并非两端具有相同的网络策略。
    const val CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
        "img-src data:; font-src data:; media-src data:; connect-src 'none'; " +
        "worker-src 'none'; child-src 'none'; frame-src 'none'; object-src 'none'; " +
        "base-uri 'none'; form-action 'none'; sandbox allow-scripts"

    fun newDocumentUrl(): String = "https://${UUID.randomUUID()}.html-preview.invalid/index.html"

    fun isMainDocumentRequest(expectedUrl: String, url: String, method: String): Boolean =
        url == expectedUrl && method.equals("GET", ignoreCase = true)

    fun responseHeaders(): Map<String, String> = mapOf(
        "Content-Security-Policy" to CSP,
        "Cache-Control" to "no-store",
        "X-Content-Type-Options" to "nosniff"
    )
}
