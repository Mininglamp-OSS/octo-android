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

import org.json.JSONArray
import org.json.JSONObject

/**
 * 渲染前 payload sanitizer（对齐 iOS `WKACardRenderer.wk_sanitizeNode`）。
 *
 * 服务端下发的 payload 有两类结构与 AC SDK 严格 schema 不一致：
 *
 * 1. **Input.Toggle 缺 `title`**。AC 官方 schema 里 title 是 required，Teams SDK 的 C++ ObjectModel
 *    对空串一律判缺失 → `IOException: Property is required but was found empty: title`
 *    → 整卡反序列化失败 → 走 [WKInteractiveCardProvider] 的 catch 降级 plain。
 *    web 的 JS SDK 宽松所以能显示；iOS/Android 一起栽。服务端 `validate.go:184-191` 明确
 *    不校验此字段（"bot 须在其他文本元素提供标签"），所以只能客户端 sanitize。
 *
 * 2. **Input.ChoiceSet 默认 `compact` 下拉**。AC SDK 的 compact 样式在 Teams 版实现里是
 *    把选项列表 addView 到 window，样式差且不随会话页 VC 生命周期消失（关页面残留浮层）。
 *    统一改成 `expanded` 内联单选，视觉一致 + 无浮层残留。
 *
 * 处理原则：**不改原字典**，返回 sanitize 过的深拷贝。递归遍历所有可能的容器字段
 * （body/items/columns/rows/cells/facts/choices/actions/selectAction/inlineAction）。
 */
object InteractiveCardSanitizer {

    /** 兜底 title 文案：当 Input.Toggle 连 label 都没有时使用。 */
    private const val TOGGLE_FALLBACK_TITLE = "开关"

    /**
     * 返回 sanitize 过的卡片 JSON 深拷贝；原对象不动。
     * 若入参为 null 直接返回 null（调用方走原有 fallback 路径）。
     */
    fun sanitize(cardJson: JSONObject?): JSONObject? {
        if (cardJson == null) return null
        return sanitizeNode(cardJson) as? JSONObject ?: cardJson
    }

    /** 递归 walk：JSONObject / JSONArray / primitive；type 命中特定 element 时做字段改写。 */
    private fun sanitizeNode(node: Any?): Any? {
        return when (node) {
            is JSONObject -> sanitizeObject(node)
            is JSONArray -> sanitizeArray(node)
            else -> node
        }
    }

    private fun sanitizeObject(obj: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out.put(k, sanitizeNode(obj.opt(k)))
        }
        when (out.optString("type")) {
            "Input.ChoiceSet" -> forceExpandedChoiceSet(out)
            "Input.Toggle" -> ensureToggleTitle(out)
        }
        return out
    }

    private fun sanitizeArray(arr: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            out.put(sanitizeNode(arr.opt(i)))
        }
        return out
    }

    /** 一律强制 expanded，绕开 SDK compact 下拉浮层的样式 & 生命周期问题。 */
    private fun forceExpandedChoiceSet(el: JSONObject) {
        el.put("style", "expanded")
    }

    /**
     * title 缺失/空串 → 用 label 提升为 title（并删掉 label 避免上下重复展示），
     * 无 label 时用 [TOGGLE_FALLBACK_TITLE] 兜底。这样 AC SDK 反序列化不会挂。
     */
    private fun ensureToggleTitle(el: JSONObject) {
        val title = el.optString("title").trim()
        if (title.isNotEmpty()) return
        val label = el.optString("label").trim()
        if (label.isNotEmpty()) {
            el.put("title", label)
            el.remove("label")
        } else {
            el.put("title", TOGGLE_FALLBACK_TITLE)
        }
    }
}
