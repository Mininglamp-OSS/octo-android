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

import com.chat.base.msgitem.WKContentType
import com.xinbida.wukongim.msgmodel.WKMessageContent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 群总结完成提示的发送侧消息体, 1:1 对齐 octo-web [SummaryTipContent]。
 *
 * 走 WK_TIP(2000) 系统号段而不是自定义类型: 收端 (Web / iOS / Android) 都用各自
 * SDK 的系统提示占位替换路径渲染, 不需要为这条提示单独适配。Android 侧由
 * [com.chat.base.msgitem.WKSystemProvider] +
 * [com.chat.base.utils.StringUtils.getShowContent] 处理。
 *
 * [TEMPLATE] 刻意不做多语言 —— 它是**跨端线格式**, 由收端 SDK 做 `{0}` 占位替换,
 * 按语言给不同变体会让各端认不出同一条协议 (octo-web d31a10c3 已把这条决策记录在
 * .i18n/scan-config.json)。因此英文环境下这条提示也显示中文, 与 Web 表现一致。
 *
 * `type` 字段不在 [encodeMsg] 里写: SDK 发送时由
 * [com.xinbida.wukongim.message.WKProto.getSendPayload] 统一注入。
 */
class SummaryTipContent(
    private val uid: String = "",
    private val name: String = "",
) : WKMessageContent() {

    init {
        type = WKContentType.summaryTip
    }

    override fun encodeMsg(): JSONObject {
        val sender = JSONObject().apply {
            put("uid", uid.trim())
            put("name", name.trim())
        }
        return JSONObject().apply {
            put("content", TEMPLATE)
            put("extra", JSONArray().put(sender))
        }
    }

    companion object {
        /** 与 octo-web SUMMARY_TIP_TEMPLATE 必须逐字一致, 改动即破坏跨端识别。 */
        const val TEMPLATE = "{0}总结了群聊内容"
    }
}
