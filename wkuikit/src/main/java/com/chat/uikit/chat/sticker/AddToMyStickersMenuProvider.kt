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

import com.chat.base.WKBaseApplication
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
 * 过滤逻辑（对齐 iOS）：
 *   - msg.type 必须是 12 或 13
 *   - URL 已在缓存里视为已收藏，返回 null
 *
 * 早期版本还额外过滤 "msg.fromUID == self.uid"（自己发的贴图不弹菜单），
 * 但这会误伤"用户删了自定义表情后想重新添加"的场景：删除后 isCollected=false，
 * 本应弹菜单，但如果这条消息恰好是删除前自己用过发出去的，fromUID==self 直接被
 * 拦掉。iOS 未做此过滤 —— 已移除，回归 iOS 行为。
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
            val content = msg.baseContentMsgModel as? WKVectorStickerContent ?: return@setMethod null
            val url = content.url
            if (url.isNullOrEmpty()) return@setMethod null
            if (WKStickerManager.isCollected(url)) return@setMethod null

            val label = WKBaseApplication.getInstance().context
                .getString(BaseR.string.str_add_to_my_stickers)
            ChatItemPopupMenu(
                // 用矢量图 ic_menu_add_to_stickers（贴纸框 + "+" 徽章），与 msg_copy /
                // msg_forward 同为 24dp 灰度线条风格。之前用 icon_chat_toolbar_emoji
                // （输入面板的黄脸大图标）在长按菜单里颜色/尺寸都突兀。
                R.drawable.ic_menu_add_to_stickers,
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
