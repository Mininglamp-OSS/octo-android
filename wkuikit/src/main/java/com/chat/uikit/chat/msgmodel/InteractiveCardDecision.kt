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
 * InteractiveCard(=17) 渲染前策略层：sender trust → profile/version 协商 → octo 白名单校验。
 *
 * 对齐 web `octo-web/dmworkbase/InteractiveCard/{guards,renderDecision,validateCardForOcto}.ts`。
 *
 * **必须整卡降级为 plain 的场景**（对齐服务端契约，无 per-element fallback）：
 * 1. sender 非可信（human / pending）→ plain（fail-closed）
 * 2. profile 非 octo/v1 或 octo/v2 → plain + "需更新客户端" 提示
 * 3. card_version > 客户端上限（1.6）→ plain + "需更新客户端" 提示
 * 4. octo 白名单外的 element / action / 结构损坏 / URL 非法 / 深度/节点越界 → plain
 *
 * 通过全部检查 → 交给 SDK 渲染；octo/v2 才允许 Input.* 与 Action.Submit。
 */
object InteractiveCardDecision {

    // ── profile / version 常量（对齐 web types.ts） ──
    const val PROFILE_OCTO_V1 = "octo/v1"
    const val PROFILE_OCTO_V2 = "octo/v2"

    /** 客户端支持的最高 card_version —— 对齐官方 AC SDK 上限（1.6）。 */
    const val MAX_CARD_VERSION_MAJOR = 1
    const val MAX_CARD_VERSION_MINOR = 6

    /** 防御上限（与服务端 enforced 上限对齐）。 */
    const val MAX_DEPTH = 16
    const val MAX_NODES = 200

    // ── 元素白名单（严格按 web validateCardForOcto 的 octo 子集） ──
    private val ELEMENTS_V1 = setOf(
        "TextBlock", "RichTextBlock", "Image", "ImageSet",
        "Container", "ColumnSet", "Column", "FactSet", "Fact", "Table",
        "TableRow", "TableCell", "ActionSet"
    )

    /** octo/v2 追加：Input.* 交互输入（v1 遇到 Input.* 即整卡降级）。 */
    private val ELEMENTS_V2_INTERACTIVE = setOf(
        "Input.Text", "Input.Toggle", "Input.ChoiceSet", "Input.Number", "Input.Date", "Input.Time"
    )

    private val ACTIONS_ALL = setOf(
        "Action.OpenUrl", "Action.ToggleVisibility", "Action.CopyToClipboard"
    )
    private val ACTIONS_V2_INTERACTIVE = setOf("Action.Submit")

    // Action.Execute / ShowCard / 模板绑定 永不支持
    private val ACTIONS_FORBIDDEN = setOf("Action.Execute", "Action.ShowCard")

    /** 渲染决策结果。 */
    sealed class Decision {
        /** 走 plain 兜底文案。 */
        object Plain : Decision()

        /** 走 plain 兜底 + "请更新客户端" 提示。 */
        object Hint : Decision()

        /** 通过全部校验，交 SDK 渲染。allowInteractive 决定 Input/Submit 是否放开。 */
        data class Card(val allowInteractive: Boolean, val interactive: Boolean) : Decision()
    }

    /**
     * 完整决策：sender trust → negotiate → validate。
     * @param interactiveByTrust 当前 sender 是否可交互（bot=true / webhook=false 展示-only）
     */
    fun decide(
        trust: CardSenderTrust,
        profile: String,
        cardVersion: String,
        card: JSONObject?
    ): Decision {
        // 1. sender trust gate（fail-closed for human/pending）
        if (!CardSenderClassifier.isTrusted(trust)) {
            return Decision.Plain
        }
        // 2. profile / cardVersion 协商
        if (!isSupportedProfile(profile) || !isSupportedCardVersion(cardVersion)) {
            return Decision.Hint
        }
        // 3. octo 白名单 + 结构 + 预算校验
        val allowInteractive = profile == PROFILE_OCTO_V2
        if (card == null || !validateOcto(card, allowInteractive)) {
            return Decision.Plain
        }
        // 交互（提交）仅对 bot-sender 卡开放；webhook 卡展示-only（无事件消费端）。
        val interactive = trust == CardSenderTrust.BOT
        return Decision.Card(allowInteractive = allowInteractive, interactive = interactive)
    }

    fun isSupportedProfile(profile: String): Boolean =
        profile == PROFILE_OCTO_V1 || profile == PROFILE_OCTO_V2

    /** "M.N" 格式版本号比较，非法返回 false。上限 = 客户端 MAX_CARD_VERSION。 */
    fun isSupportedCardVersion(version: String): Boolean {
        val match = Regex("^(\\d+)\\.(\\d+)$").matchEntire(version.trim()) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: return false
        if (major < MAX_CARD_VERSION_MAJOR) return true
        if (major > MAX_CARD_VERSION_MAJOR) return false
        return minor <= MAX_CARD_VERSION_MINOR
    }

    /**
     * octo 白名单 + 结构校验（递归）。
     *
     * 与 web `validateCardForOcto` 对齐（简化）：
     *  - 只允许白名单 element / action 类型；
     *  - 深度 ≤ 16、节点数 ≤ 200；
     *  - Input.* / Action.Submit 的 id 必填且帧内唯一（v2 才可出现）；
     *  - Action.OpenUrl.url 必须是 http/https；
     *  - Action.Execute / ShowCard / 未知类型 → 整卡降级。
     */
    fun validateOcto(card: JSONObject, allowInteractive: Boolean): Boolean {
        return try {
            val ctx = ValidateCtx(allowInteractive)
            walkCard(card, ctx, depth = 0)
            if (ctx.budgetExceeded) {
                android.util.Log.w("InteractiveCard", "validate 预算超限：nodes=${ctx.nodeCount}")
            }
            !ctx.budgetExceeded
        } catch (e: OctoInvalidCard) {
            android.util.Log.w("InteractiveCard", "validate 失败：${e.message}")
            false
        }
    }

    private class OctoInvalidCard(msg: String) : RuntimeException(msg)

    private class ValidateCtx(val allowInteractive: Boolean) {
        var nodeCount = 0
        var budgetExceeded = false
        val seenIds = mutableSetOf<String>()
    }

    /** 走一棵 AC 树：card 根 → body[] / actions[]。 */
    private fun walkCard(card: JSONObject, ctx: ValidateCtx, depth: Int) {
        if (depth > MAX_DEPTH) {
            ctx.budgetExceeded = true; return
        }
        if (!consumeNode(ctx)) return
        walkArray(card.optJSONArray("body"), ctx, depth + 1)
        walkArray(card.optJSONArray("actions"), ctx, depth + 1, isActionsRoot = true)
    }

    private fun walkArray(arr: JSONArray?, ctx: ValidateCtx, depth: Int, isActionsRoot: Boolean = false) {
        if (arr == null) return
        if (depth > MAX_DEPTH) {
            ctx.budgetExceeded = true; return
        }
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: throw OctoInvalidCard("array item must be object")
            if (isActionsRoot) walkAction(item, ctx, depth) else walkElement(item, ctx, depth)
            if (ctx.budgetExceeded) return
        }
    }

    private fun walkElement(el: JSONObject, ctx: ValidateCtx, depth: Int) {
        if (!consumeNode(ctx)) return
        val rawType = el.optString("type", "").trim()

        // AC 3.x 里 Column / TableRow / TableCell 的 type 字段可选（SDK 从父上下文推断）。
        // 缺 type 时不当作违规，只递归子节点即可 —— 元素白名单在有 type 时才生效。
        if (rawType.isEmpty()) {
            walkChildren(el, ctx, depth)
            return
        }

        // 白名单：v1 基础 + v2 追加 Input.*
        val allowedElement = ELEMENTS_V1.contains(rawType) ||
            (ctx.allowInteractive && ELEMENTS_V2_INTERACTIVE.contains(rawType))
        if (!allowedElement) {
            throw OctoInvalidCard("unknown element: $rawType")
        }

        // Input.* 必须有 id 且帧内唯一
        if (rawType.startsWith("Input.")) {
            val id = el.optString("id", "").trim()
            if (id.isEmpty()) throw OctoInvalidCard("Input.$rawType missing id")
            if (!ctx.seenIds.add(id)) throw OctoInvalidCard("duplicate id: $id")
        }

        walkChildren(el, ctx, depth)
    }

    /** 递归 element 的所有可能的子容器字段。 */
    private fun walkChildren(el: JSONObject, ctx: ValidateCtx, depth: Int) {
        walkArray(el.optJSONArray("body"), ctx, depth + 1)
        walkArray(el.optJSONArray("items"), ctx, depth + 1)
        walkArray(el.optJSONArray("columns"), ctx, depth + 1)
        walkArray(el.optJSONArray("rows"), ctx, depth + 1)
        walkArray(el.optJSONArray("cells"), ctx, depth + 1)
        walkArray(el.optJSONArray("facts"), ctx, depth + 1)
        walkArray(el.optJSONArray("images"), ctx, depth + 1)
        walkArray(el.optJSONArray("choices"), ctx, depth + 1)
        walkArray(el.optJSONArray("actions"), ctx, depth + 1, isActionsRoot = true)

        // selectAction / inlineAction 也是 Action，从这两个字段递归下去
        el.optJSONObject("selectAction")?.let { walkAction(it, ctx, depth + 1) }
        el.optJSONObject("inlineAction")?.let { walkAction(it, ctx, depth + 1) }
    }

    private fun walkAction(action: JSONObject, ctx: ValidateCtx, depth: Int) {
        if (!consumeNode(ctx)) return
        val type = action.optString("type", "").trim()
        if (type.isEmpty()) throw OctoInvalidCard("action missing type")
        if (ACTIONS_FORBIDDEN.contains(type)) throw OctoInvalidCard("forbidden action: $type")

        val allowedAction = ACTIONS_ALL.contains(type) ||
            (ctx.allowInteractive && ACTIONS_V2_INTERACTIVE.contains(type))
        if (!allowedAction) throw OctoInvalidCard("unknown action: $type")

        // Action.Submit 必须有 id 且帧内唯一（v2 才允许出现）
        if (type == "Action.Submit") {
            val id = action.optString("id", "").trim()
            if (id.isEmpty()) throw OctoInvalidCard("Action.Submit missing id")
            if (!ctx.seenIds.add(id)) throw OctoInvalidCard("duplicate id: $id")
        }

        // Action.OpenUrl.url 必须 http/https（防 javascript:/file:/intent: 等）
        if (type == "Action.OpenUrl") {
            val url = action.optString("url", "").trim()
            if (!isSafeUrl(url)) throw OctoInvalidCard("unsafe url: $url")
        }
    }

    private fun consumeNode(ctx: ValidateCtx): Boolean {
        ctx.nodeCount += 1
        if (ctx.nodeCount > MAX_NODES) {
            ctx.budgetExceeded = true
            return false
        }
        return true
    }

    /**
     * URL 安全性判定 —— 仅允许 http(s)。
     * 拒绝：javascript:、file:、intent:、data:、content: 以及任何非 http(s) scheme。
     * 对齐 web `Utils/security.isSafeUrl`。
     */
    fun isSafeUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
