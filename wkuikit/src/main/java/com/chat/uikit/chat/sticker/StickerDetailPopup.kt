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

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.appcompat.widget.AppCompatTextView
import com.chat.base.R as BaseR
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.model.WKVectorStickerContent
import com.chat.base.ui.components.FilterImageView
import com.chat.base.utils.WKDialogUtils
import com.chat.uikit.R
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKMsgSetting

/**
 * 贴纸详情/预览弹窗。中央 180dp 预览 + 底部两个动作。
 *
 * 两个入口：
 *   - [showForChatMessage] —— 点击聊天里贴图消息触发。动作 [发送 / 添加到我的表情]。
 *     若已收藏，隐藏"添加"按钮，"发送"占满整行。
 *   - [showForPanel] —— 长按"我的表情"面板里的自定义贴图触发（对齐 iOS 长按放大预览）。
 *     动作 [发送 / 删除]。删除走二次确认。
 *
 * 两种模式共用同一份 `dialog_sticker_detail.xml` 布局，仅底部动作行的文案/回调不同。
 * 对齐 iOS `WKPOINT_TO_STICKER_INFO` 与 `UIContextMenuInteraction` 的合并简化版。
 */
object StickerDetailPopup {

    /** 消息内点击贴图 → 预览 + [发送 / 添加到我的表情]。 */
    fun showForChatMessage(context: Context, msg: WKMsg) {
        val content = msg.baseContentMsgModel as? WKVectorStickerContent ?: return
        val url = content.url ?: return

        showInternal(
            context = context,
            url = url,
            format = content.format,
            primaryText = context.getString(BaseR.string.str_sticker_send),
            primaryAction = { resendSticker(msg, content) },
            secondaryText = if (WKStickerManager.isCollected(url)) null
                else context.getString(BaseR.string.str_add_to_my_stickers),
            secondaryAction = if (WKStickerManager.isCollected(url)) null
                else { { WKStickerManager.collect(msg) } }
        )
    }

    /** 长按面板里已收藏的贴图 → 预览 + [发送 / 删除]。 */
    fun showForPanel(context: Context, sticker: WKSticker, onSend: (WKSticker) -> Unit) {
        val url = sticker.path ?: return
        showInternal(
            context = context,
            url = url,
            format = sticker.format,
            primaryText = context.getString(BaseR.string.str_sticker_send),
            primaryAction = { onSend(sticker) },
            secondaryText = context.getString(BaseR.string.str_delete),
            secondaryAction = {
                // 二次确认后走 API 删除
                WKDialogUtils.getInstance().showDialog(
                    context,
                    null,
                    context.getString(BaseR.string.str_sticker_delete_confirm),
                    true,
                    context.getString(BaseR.string.cancel),
                    context.getString(BaseR.string.sure),
                    0, 0
                ) { which ->
                    if (which == 1) WKStickerManager.delete(sticker.sticker_id, null)
                }
            }
        )
    }

    private fun showInternal(
        context: Context,
        url: String,
        format: String?,
        primaryText: String,
        primaryAction: () -> Unit,
        secondaryText: String?,
        secondaryAction: (() -> Unit)?,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_sticker_detail, null)
        val image = view.findViewById<FilterImageView>(R.id.stickerDetailImage)
        val primaryBtn = view.findViewById<AppCompatTextView>(R.id.stickerDetailSend)
        val secondaryBtn = view.findViewById<AppCompatTextView>(R.id.stickerDetailAdd)
        val divider = view.findViewById<View>(R.id.stickerDetailDivider)

        // 静态图走 Glide，Lottie / 未知走占位（复用 provider 的判定）
        when {
            StickerUrlUtils.isStaticImage(url) ->
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), image)
            StickerUrlUtils.isLottieFormat(url, format) ->
                image.setImageResource(BaseR.drawable.default_view_bg)
            else ->
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), image)
        }

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        primaryBtn.text = primaryText
        primaryBtn.setOnClickListener {
            primaryAction()
            dialog.dismiss()
        }

        if (secondaryText != null && secondaryAction != null) {
            secondaryBtn.text = secondaryText
            secondaryBtn.setOnClickListener {
                secondaryAction()
                dialog.dismiss()
            }
        } else {
            // 无第二动作 → 隐藏"添加"按钮 + 分隔线，主动作按钮占满整行
            secondaryBtn.visibility = View.GONE
            divider.visibility = View.GONE
        }

        dialog.show()
    }

    /** 快速转发：构造一条新 WKMsg 用相同 content 发送到当前频道。 */
    private fun resendSticker(origin: WKMsg, content: WKVectorStickerContent) {
        val copy = WKVectorStickerContent().apply {
            url = content.url
            width = content.width
            height = content.height
            category = content.category
            placeholder = content.placeholder
            format = content.format
        }
        val newMsg = WKMsg().apply {
            channelID = origin.channelID
            channelType = origin.channelType
            baseContentMsgModel = copy
            setting = WKMsgSetting()
        }
        WKSendMsgUtils.getInstance().sendMessage(newMsg)
    }
}
