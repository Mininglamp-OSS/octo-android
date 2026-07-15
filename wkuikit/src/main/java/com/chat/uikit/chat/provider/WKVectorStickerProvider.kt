/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.provider

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.model.WKVectorStickerContent
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.FilterImageView
import com.chat.uikit.R
import com.chat.uikit.chat.sticker.StickerDetailPopup
import com.chat.uikit.chat.sticker.StickerUrlUtils
import com.xinbida.wukongim.message.type.WKMsgContentType

/**
 * 矢量贴图 (contentType=12) provider —— 独立实现，不继承 WKImageProvider。
 *
 * 之所以不复用图片路径：贴图不能走大图预览 / "保存到相册" / 上传进度 / 焚阅气泡，
 * URL 常是 CDN 全路径或 .lim/.json (Lottie) 无法 Glide 加载，
 * 硬套 WKImageProvider 会崩或行为错乱。iOS `hiddenBubble=YES` 也强制无气泡。
 *
 * Emoji sticker (13) 走 [WKEmojiStickerProvider]（仅覆写 itemViewType）。
 *
 * 长按菜单复用基类 [addLongClick]：转发/撤回/删除/复制 由基类给；
 * "添加到我的表情"通过 EndpointCategory.wkChatPopupItem 扩展点由
 * AddToMyStickersMenuProvider 注入，按 msg.type 过滤。
 *
 * 单击行为：弹出 [StickerDetailPopup]（180dp 预览 + 发送 / 添加到我的表情）。
 * 对齐 iOS `WKPOINT_TO_STICKER_INFO` 的最简版本。
 */
open class WKVectorStickerProvider : WKChatBaseProvider() {

    override val itemViewType: Int
        get() = WKMsgContentType.WK_VECTOR_STICKER

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_sticker, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val imageView = parentView.findViewById<FilterImageView>(R.id.imageView)
        imageView.setAllCorners(10)

        val content = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKVectorStickerContent
        if (content == null) {
            imageView.setImageResource(R.drawable.default_view_bg)
            addLongClick(imageView, uiChatMsgItemEntity)
            return
        }

        val url = content.url
        when {
            url.isNullOrEmpty() -> imageView.setImageResource(R.drawable.default_view_bg)
            StickerUrlUtils.isStaticImage(url) ->
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), imageView)
            StickerUrlUtils.isLottieFormat(url, content.format) ->
                imageView.setImageResource(R.drawable.default_view_bg)
            else ->
                // 未知格式：先尝试当图片加载，失败降级到占位
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), imageView)
        }

        addLongClick(imageView, uiChatMsgItemEntity)
        imageView.setOnClickListener {
            StickerDetailPopup.showForChatMessage(imageView.context, uiChatMsgItemEntity.wkMsg)
        }
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val imageView = parentView.findViewById<FilterImageView>(R.id.imageView) ?: return
        addLongClick(imageView, uiChatMsgItemEntity)
        imageView.setOnClickListener {
            StickerDetailPopup.showForChatMessage(imageView.context, uiChatMsgItemEntity.wkMsg)
        }
    }
}
