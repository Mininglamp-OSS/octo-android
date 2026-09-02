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
import java.text.MessageFormat

/**
 * 群总结完成提示的发送侧消息体, 1:1 对齐 octo-web [SummaryTipContent]。
 *
 * 走 WK_TIP(2000) 系统号段而不是自定义类型: 收端 (Web / iOS / Android) 都用各自
 * SDK 的系统提示占位替换路径渲染, 不需要为这条提示单独适配。Android 侧由
 * [com.chat.base.msgitem.WKSystemProvider] 特化处理 (不走
 * [com.chat.base.utils.StringUtils.getShowContent] 的通用"uid==自己显示你"逻辑 ——
 * 总结提示不管是不是本人发的都要显示发起人姓名本身, 见 [resolveDisplayContent])。
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

        /**
         * 收端渲染: 直接取 `extra[0].name` 填进 [TEMPLATE], **不做**
         * [com.chat.base.utils.StringUtils.getShowContent] 那种"uid==当前登录用户就
         * 显示'你'"的替换 —— 总结提示无论是不是本机自己发起的, 都要显示发起人的姓名本身
         * (对齐产品诉求: 自己在手机上发起总结, 群里看到的也应该是"张三总结了群聊内容"
         * 而不是"你总结了群聊内容")。
         *
         * 解析失败 (JSON 非法 / extra 缺失) 返回 null, 调用方自行决定兜底文案。
         */
        @JvmStatic
        fun resolveDisplayContent(contentJson: String?): String? {
            if (contentJson.isNullOrEmpty()) return null
            return try {
                val obj = JSONObject(contentJson)
                val template = obj.optString("content").ifEmpty { TEMPLATE }
                val sender = obj.optJSONArray("extra")?.optJSONObject(0) ?: return null
                val name = sender.optString("name")
                MessageFormat.format(template, name)
            } catch (_: Exception) {
                null
            }
        }
    }
}
