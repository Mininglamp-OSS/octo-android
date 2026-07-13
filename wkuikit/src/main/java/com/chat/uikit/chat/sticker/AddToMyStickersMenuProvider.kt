/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import android.text.TextUtils
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChatItemPopupMenu
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKVectorStickerContent
import com.chat.base.R as BaseR
import com.chat.uikit.R
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.message.type.WKMsgContentType

/**
 * 长按贴图消息 → 弹出"添加到我的表情"菜单，收藏后 WKStickerManager 缓存自更新。
 *
 * 通过 EndpointCategory.wkChatPopupItem 注册，与其他长按项（copy / create_thread /
 * forward / withdraw）平级 —— 不侵入 WKChatBaseProvider.getPopupList()。
 *
 * 过滤逻辑：
 *   msg.type 必须是 12 或 13 —— 其它消息返回 null，菜单不加。
 *   msg.fromUID == self.uid → 自己发的贴图不出菜单（iOS 未做此过滤，Android 主动优化：
 *     自己刚发出的贴图本就该在收藏里，或即将成为，再让用户"添加"逻辑上冗余）。
 *   URL 已在缓存里 → 视为已收藏，返回 null（对齐 iOS collectStickers 检查）。
 */
object AddToMyStickersMenuProvider {

    private const val SID = "add_to_my_stickers"

    // sort 数值越小越靠前。iOS 长按面板里"添加表情"排在撤回/转发之后，用 20 即可
    // （create_thread 用 10，copy 用 90）。
    private const val SORT = 20

    fun register() {
        EndpointManager.getInstance().setMethod(SID, EndpointCategory.wkChatPopupItem, SORT) { obj ->
            val msg = obj as? WKMsg ?: return@setMethod null
            if (msg.type != WKMsgContentType.WK_VECTOR_STICKER &&
                msg.type != WKMsgContentType.WK_EMOJI_STICKER
            ) {
                return@setMethod null
            }
            // 自己发的贴图不弹菜单。selfUid 空（未登录 / 冷启动竞态）保守不过滤，
            // 交给下面已收藏判断兜底。
            val selfUid = WKConfig.getInstance().uid
            if (!TextUtils.isEmpty(selfUid) && selfUid == msg.fromUID) {
                return@setMethod null
            }
            val content = msg.baseContentMsgModel as? WKVectorStickerContent ?: return@setMethod null
            val url = content.url
            if (url.isNullOrEmpty()) return@setMethod null
            if (WKStickerManager.isCollected(url)) return@setMethod null

            val label = WKBaseApplication.getInstance().context
                .getString(BaseR.string.str_add_to_my_stickers)
            ChatItemPopupMenu(
                R.mipmap.icon_chat_toolbar_emoji,
                label,
                object : ChatItemPopupMenu.IPopupItemClick {
                    override fun onClick(mMsg: WKMsg, iConversationContext: IConversationContext) {
                        WKStickerManager.collect(mMsg)
                    }
                }
            ).apply { tag = SID }
        }
    }
}
