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

import com.xinbida.wukongim.entity.WKMsg

/**
 * 一次卡片交互动作发生时，Provider 传给 [CardActionDispatcher] 的消息上下文。
 *
 * C1 里每次 setData 都构造新实例（对齐旧 CardActionHandler 的语义）；C3 引入
 * view 缓存后，同一份 rendered view 会跨消息复用，此时 [MessageContext] 会
 * 挂在 view.tag 上按 bind 动态刷新——dispatch 边界保持不变，是这层抽象存在
 * 的理由。
 */
data class MessageContext(
    val wkMsg: WKMsg,
    /** Sender trust=bot 且 profile=octo/v2 才 true；webhook 卡展示-only。 */
    val allowSubmit: Boolean,
)
