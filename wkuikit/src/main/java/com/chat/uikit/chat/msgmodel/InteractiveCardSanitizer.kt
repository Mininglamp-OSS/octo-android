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
 * 服务端下发的 payload 有三类结构在 Android 侧需要客户端 sanitize：
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
 * 3. **ColumnSet 里 auto column 装 ≥2 个 Action 的 ActionSet**（移动端拆行）。
 *    服务端为桌面宽卡设计的横向 ColumnSet 布局在群聊 216dp 窄气泡下装不下 —— SDK 3.7.0
 *    发现 auto column 需要宽度超过可用空间时**直接 skip 整个 auto column 的渲染**（不 wrap
 *    也不 truncate）。观测：pending 卡底部 `[stretch: 申请于 11:03] [auto: 查看详情/拒绝/允许]`
 *    在 216dp 群接收下 3 个按钮完全消失，在 256dp 私聊/自己发送下正常显示。
 *
 *    移动端拆行策略：命中"columns ≥ 2 且至少一个 column 里有 actions ≥ 2 的 ActionSet"
 *    时，把 ColumnSet 转成 Container（垂直堆叠）—— 各 column 的 items 平铺成 Container.items
 *    独占一行。这样 ActionSet 独立一行拿到全宽，不再挤占。**只在 actions ≥ 2 时才拆**
 *    是因为 approved/rejected 卡的底部只有 1 个"查看详情" button，SDK 装得下，
 *    不需要拆；拆了反倒把"处理于 11:08"从右上角挪到独立一行，多占垂直空间。
 *
 * 处理原则：**不改原字典**，返回 sanitize 过的深拷贝。递归遍历所有可能的容器字段
 * （body/items/columns/rows/cells/facts/choices/actions/selectAction/inlineAction）。
 */
object InteractiveCardSanitizer {

    /** 兜底 title 文案：当 Input.Toggle 连 label 都没有时使用。 */
    private const val TOGGLE_FALLBACK_TITLE = "开关"

    /**
     * 触发移动端 ColumnSet 拆行的 ActionSet 最小 actions 数。1 个 button 通常能装下不拆；
     * ≥2 个才要独立一行。跟 [SDK auto column skip] 观测行为对齐。
     */
    private const val MOBILE_STACK_MIN_ACTIONS = 2

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
            "ColumnSet" -> stackColumnSetForMobileActions(out)
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

    /**
     * 把 `ColumnSet(cols=[stretch text, auto ActionSet])` 拆成 `Container(items=[text, ActionSet])`
     * —— 让 ActionSet 独占一行拿到全宽，绕开 SDK 3.7.0 auto column skip 行为。
     *
     * 命中条件（both required）：
     *  - columns.length >= 2（单列 ColumnSet 不需要拆）
     *  - 任一 column 里的 ActionSet 有 >= [MOBILE_STACK_MIN_ACTIONS] 个 actions（单 button 装得下，不拆）
     *
     * 改写方式：
     *  - `type` 从 `ColumnSet` 改为 `Container`
     *  - 各 column 的 items 依次平铺成 Container.items（保序，先左后右）
     *  - `columns` 字段删除
     *  - `verticalContentAlignment` 属于 ColumnSet 专属，删除避免 SDK 疑惑
     *  - `style` / `spacing` / `bleed` / `separator` / `isVisible` / `id` 保留（Container 一样吃）
     */
    private fun stackColumnSetForMobileActions(el: JSONObject) {
        val columns = el.optJSONArray("columns") ?: return
        if (columns.length() < 2) return

        // 扫一遍看是否命中拆行条件。
        var shouldStack = false
        for (i in 0 until columns.length()) {
            val col = columns.optJSONObject(i) ?: continue
            val items = col.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                if (item.optString("type") == "ActionSet") {
                    val actions = item.optJSONArray("actions")
                    if (actions != null && actions.length() >= MOBILE_STACK_MIN_ACTIONS) {
                        shouldStack = true
                        break
                    }
                }
            }
            if (shouldStack) break
        }
        if (!shouldStack) return

        // 平铺各 column.items 到新的 Container.items 数组。
        val flatItems = JSONArray()
        for (i in 0 until columns.length()) {
            val col = columns.optJSONObject(i) ?: continue
            val items = col.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                flatItems.put(items.opt(j))
            }
        }
        el.put("type", "Container")
        el.put("items", flatItems)
        el.remove("columns")
        el.remove("verticalContentAlignment")
    }
}
