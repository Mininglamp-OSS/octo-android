/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.chat.provider

import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.emoji2.widget.EmojiTextView
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKMsgBgType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.ImageUtils
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.WKRichTextContent

/**
 * 图文混排消息（RichText=14）接收渲染 provider（Phase 1，仅接收）。
 *
 * <p>消费 {@link WKRichTextContent#blocks}，按数组顺序在气泡内垂直穿插 text /
 * image 子 View：
 * <ul>
 *   <li>text block → [EmojiTextView]，MVP 锁纯文本（不渲 markdown），emoji 仍由
 *       EmojiTextView 渲染；</li>
 *   <li>image block → [AppCompatImageView] + Glide 内联加载，尺寸走与图片消息
 *       一致的 [ImageUtils.getImageWidthAndHeightToTalk]。</li>
 * </ul>
 *
 * <p>复制 / 回复 / 转发 / 删除 / 撤回 / reaction 等长按操作复用基类标准
 * [addLongClick] 弹出菜单；复制项由 WKUIKitApplication 注册的 wkChatPopupItem
 * 菜单提供，取顶层 plain（{@link WKRichTextContent#getDisplayContent}），勿丢字。
 *
 * <p>所有渲染逻辑封装在本 provider，未触碰宿主 ChatActivity / ChatFragment。
 */
class WKRichTextProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_richtext, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val contentTvLayout = parentView.findViewById<BubbleLayout>(R.id.contentTvLayout)
        val blocksLayout = parentView.findViewById<LinearLayout>(R.id.richBlocksLayout)
        // RecyclerView 复用：每次重建子 View，避免残留上一条消息内容。
        blocksLayout.removeAllViews()

        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        contentLayout.gravity =
            if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START else Gravity.END

        val textColor = if (from == WKChatIteMsgFromType.SEND) {
            ContextCompat.getColor(context, R.color.colorDark)
        } else {
            ContextCompat.getColor(context, R.color.receive_text_color)
        }

        val model = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKRichTextContent
        if (model == null) {
            // 解析失败兜底：展示已知 plain（若有），否则未知消息提示。
            addTextBlock(blocksLayout, textColor, context.getString(R.string.base_unknow_msg))
            addLongClick(contentTvLayout, uiChatMsgItemEntity)
            return
        }

        val blocks = model.blocks
        if (blocks.isNullOrEmpty()) {
            // 无 block：回退顶层 plain，仍为空则未知消息提示。
            addTextBlock(blocksLayout, textColor, fallbackText(model))
            addLongClick(contentTvLayout, uiChatMsgItemEntity)
            return
        }

        for (block in blocks) {
            if (block == null) continue
            when {
                block.isImage() -> addImageBlock(blocksLayout, block)
                else -> {
                    // text block 与未知 type（带 text）都走文本渲染，前向兼容二期扩展。
                    if (!TextUtils.isEmpty(block.text)) {
                        addTextBlock(blocksLayout, textColor, block.text)
                    }
                }
            }
        }

        // 全部 block 渲染后内容仍为空（如纯未知 block 无 text）→ 回退 plain，勿留空气泡。
        if (blocksLayout.childCount == 0) {
            addTextBlock(blocksLayout, textColor, fallbackText(model))
        }

        // 复制 / 回复 / 转发 / 删除 / reaction 走基类标准长按菜单。
        addLongClick(contentTvLayout, uiChatMsgItemEntity)
    }

    /** block 渲染为空时的兜底文本：优先顶层 plain，否则未知消息提示。 */
    private fun fallbackText(model: WKRichTextContent): String {
        return if (!TextUtils.isEmpty(model.plain)) {
            model.plain
        } else {
            context.getString(R.string.base_unknow_msg)
        }
    }

    private fun addTextBlock(parent: LinearLayout, textColor: Int, text: String?) {
        val tv = EmojiTextView(context).apply {
            this.text = text ?: ""
            setTextColor(textColor)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                context.resources.getDimension(R.dimen.font_size_16)
            )
            setLineSpacing(2f * context.resources.displayMetrics.density, 1f)
            movementMethod = LinkMovementMethod.getInstance()
        }
        parent.addView(
            tv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addImageBlock(parent: LinearLayout, block: WKRichTextContent.RichTextBlock) {
        val imageView = AppCompatImageView(context)
        val wh = ImageUtils.getInstance().getImageWidthAndHeightToTalk(block.width, block.height)
        val lp = LinearLayout.LayoutParams(wh[0], wh[1]).apply {
            topMargin = AndroidUtilities.dp(4f)
            bottomMargin = AndroidUtilities.dp(4f)
        }
        val showUrl = WKApiConfig.getShowUrl(block.url)
        if (!TextUtils.isEmpty(showUrl)) {
            GlideUtils.getInstance().showImg(context, showUrl, wh[0], wh[1], imageView)
        }
        parent.addView(imageView, lp)
    }

    override val itemViewType: Int
        get() = WKContentType.richText

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val contentTvLayout = parentView.findViewById<BubbleLayout>(R.id.contentTvLayout) ?: return
        val bgType: WKMsgBgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        contentTvLayout.setAll(bgType, from, WKContentType.WK_TEXT)
    }
}
