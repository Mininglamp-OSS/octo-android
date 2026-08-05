/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.provider.card

/**
 * 卡片交互动作的**纯 Kotlin 值对象**。
 *
 * 存在的意义：AdaptiveCards SDK 的 [io.adaptivecards.objectmodel.BaseActionElement] /
 * [io.adaptivecards.renderer.RenderedAdaptiveCard] 是 SWIG C++ binding，JVM 单测
 * 无法直接构造。所以在 SDK 边界处把 action 展平成一份没有 SDK 依赖的值对象，
 * dispatcher 只依赖它 → 全量 JVM 单测覆盖。
 *
 * 只覆盖当前 provider 会**主动处理**的两类：
 *  - [OpenUrl]：拉起 App 内 WebView（对齐 iOS `1527a713`）
 *  - [Submit]：POST /v1/message/card/action
 *
 * SDK 内部处理的 Action.ToggleVisibility / Action.ShowCard 不进入这个 sealed，
 * 由 SDK adapter 层直接 return。
 *
 * `Action.CopyToClipboard` 曾在早期草案里作为独立变体存在。AC 3.7.0 Android SDK
 * 无内置 `CopyToClipboardActionParser`（web AC JS SDK 有；Teams AAR 也未提供），
 * 真正实现需自写 SWIG native parser，工作量与收益不匹配（iOS 目前也没实现它）。
 * 当前策略（对齐 iOS 净效果）：[com.chat.uikit.chat.msgmodel.InteractiveCardDecision] **容忍**
 * 未知 action 不毙整卡，[com.chat.uikit.chat.msgmodel.InteractiveCardSanitizer] 在喂 SDK 前
 * 剥掉该按钮 → 含 Copy 的卡**正常渲染、仅复制按钮不出现**（不再整卡降级 plain）。
 */
sealed interface CardAction {
    val actionId: String

    data class OpenUrl(
        override val actionId: String,
        val url: String,
    ) : CardAction

    data class Submit(
        override val actionId: String,
        val inputs: Map<String, String>,
    ) : CardAction
}
