/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.msgmodel

import com.chat.base.msgitem.WKContentType
import com.xinbida.wukongim.msgmodel.WKMessageContent
import org.json.JSONObject

/**
 * 交互式卡片消息（type=17）—— Microsoft AdaptiveCards / octo InteractiveCard。
 *
 * 服务端信封契约（对齐 web 端 InteractiveCardPayload）：
 * ```json
 * {
 *   "type": 17,
 *   "card": { "type":"AdaptiveCard", "version":"1.5", "body":[...], "actions":[...] },
 *   "plain": "服务端权威纯文本，兜底与预览用",
 *   "card_version": "1.5",
 *   "profile": "octo/v1" | "octo/v2",
 *   "space_id": "space-1"
 * }
 * ```
 *
 * P1 最小可跑：只解析 [cardJson] + [plain]，供 Provider 交给 SDK 渲染 / 失败降级。
 * 白名单校验、profile 协商、digest 兜底文案后续 M1 里再补。
 */
class WKInteractiveCardContent : WKMessageContent() {

    init {
        type = WKContentType.interactiveCard
    }

    /** 原始 AdaptiveCard JSON 对象，直接透传给 SDK renderer。 */
    var cardJson: JSONObject? = null

    /** 服务端权威纯文本（会话列表预览、渲染失败兜底）。 */
    var plain: String = ""

    /** AdaptiveCards schema 版本，SDK 反序列化时用。 */
    var cardVersion: String = "1.5"

    /** 展示协商 profile：octo/v1（纯展示）/ octo/v2（可交互）。 */
    var profile: String = ""

    /** Space 隔离标识。 */
    var cardSpaceId: String = ""

    override fun encodeMsg(): JSONObject {
        val json = JSONObject()
        cardJson?.let { json.put("card", it) }
        json.put("plain", plain)
        json.put("card_version", cardVersion)
        json.put("profile", profile)
        json.put("space_id", cardSpaceId)
        return json
    }

    override fun decodeMsg(jsonObject: JSONObject?): WKMessageContent {
        val json = jsonObject ?: return this
        cardJson = json.optJSONObject("card")
        plain = json.optString("plain", "")
        cardVersion = json.optString("card_version", "1.5")
        profile = json.optString("profile", "")
        cardSpaceId = json.optString("space_id", "")
        return this
    }

    override fun getDisplayContent(): String = plain.ifBlank { "[卡片]" }

    override fun getSearchableWord(): String = plain
}
