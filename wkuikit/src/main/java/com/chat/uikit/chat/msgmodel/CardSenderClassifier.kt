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

import com.chat.uikit.group.webhook.service.IncomingWebhook
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType

/**
 * InteractiveCard(=17) 发送者信任分类（render gate）。
 *
 * **对齐 web `octo-web/dmworkbase/InteractiveCard/senderTrust.ts`。**
 *
 * 协议契约（非 UI 细节）：
 * - type-17 的 sync/拉取路径服务端原样透传，不替客户端遮蔽；
 * - direct-socket 写入可绕过 HTTP ingress，残余防线 = 客户端 render gate；
 * - 「是否渲染结构卡」必须 fail-closed 判定，普通用户塞的 type-17 一律退 plain
 *   （否则可造出钓鱼卡，视觉冒充 bot 交互）。
 *
 * 与服务端 `cardtrust` 口径一致：信任 = ExistRobot OR from_uid 前缀 iwh_。
 */
enum class CardSenderTrust {
    /** iwh_ 前缀（服务端连接鉴权绑定，不可伪造）→ 同步就地信任。 */
    WEBHOOK,

    /** ChannelInfo.robot == 1（服务端下发 profile）→ 信任。 */
    BOT,

    /** 普通用户 → 永不渲染结构卡，退 plain（fail-closed）。 */
    HUMAN,

    /** channelInfo 未命中 → fail-closed 先退 plain，Provider 拉取后由 SDK listener 重渲。 */
    PENDING
}

object CardSenderClassifier {

    /**
     * 分类发送者信任级别。纯读操作，无副作用（不触发 fetch）；
     * pending 的 fetch 由调用方（Provider）驱动，避免在 render 中产生副作用。
     */
    fun classify(fromUID: String?): CardSenderTrust {
        // webhook 优先：iwh_ 前缀是服务端权威信号，同步可判，无需异步等 channelInfo。
        if (!fromUID.isNullOrEmpty() && fromUID.startsWith(IncomingWebhook.UID_PREFIX)) {
            return CardSenderTrust.WEBHOOK
        }
        // 无发送者：无法建立信任，也无从 fetch，直接判 human（不渲结构卡）。
        if (fromUID.isNullOrEmpty()) {
            return CardSenderTrust.HUMAN
        }
        val channel = WKIM.getInstance().channelManager.getChannel(fromUID, WKChannelType.PERSONAL)
        // cache miss：fail-closed。返回 pending，调用方负责 fetch + 到达后重渲。
        if (channel == null) {
            return CardSenderTrust.PENDING
        }
        return if (channel.robot == 1) CardSenderTrust.BOT else CardSenderTrust.HUMAN
    }

    /** 是否可渲染结构卡。仅 webhook / bot 可信；human / pending 一律退 plain。 */
    fun isTrusted(trust: CardSenderTrust): Boolean =
        trust == CardSenderTrust.WEBHOOK || trust == CardSenderTrust.BOT

    /**
     * pending 时需主动拉取发送者 channelInfo；到达后 IM SDK 广播 ChannelInfoListener 触发重渲。
     * 幂等 —— 内部会 short-circuit 已在飞的请求。
     */
    fun fetchSenderChannelInfo(fromUID: String) {
        if (fromUID.isEmpty()) return
        WKIM.getInstance().channelManager.fetchChannelInfo(fromUID, WKChannelType.PERSONAL)
    }
}
