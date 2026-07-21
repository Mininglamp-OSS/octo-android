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
 * 一张卡的**内容规格**——纯值对象，作为 [InteractiveCardRenderer] 的输入。
 *
 * cardJson 和 cardVersion 完全决定 SDK 渲染输出（HostConfig 由 renderer 从 context
 * 读，isDark 也由 context 隐含），所以缓存键用 `(cardJson.hashCode, cardVersion, isDark)`
 * 三维穷举即可。
 *
 * 跟 [MessageContext] 分开：一份 spec 可能对应 N 条 message（bot 群发同一张通知卡），
 * 缓存 view 按 spec 复用；每次 bind 通过 [MessageContext] 更新 view.tag 使 Submit
 * 打到正确的当前消息。
 */
data class CardRenderSpec(
    val cardJson: String,
    val cardVersion: String,
)
