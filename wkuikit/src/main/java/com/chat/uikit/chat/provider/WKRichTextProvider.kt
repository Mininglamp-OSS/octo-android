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
import android.widget.TextView
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

        // 群/社区里 RichText 接收消息显示发送者昵称：基类对 richText 跳过通用
        // sender-name 路径（WKChatBaseProvider:464/190），故在 provider 内手动复用
        // 基类 setFromName 渲染外部名字行（含实名徽章 / @Space 后缀逻辑）。
        applyFromName(parentView, uiChatMsgItemEntity, from)

        val textColor = if (from == WKChatIteMsgFromType.SEND) {
            ContextCompat.getColor(context, R.color.colorDark)
        } else {
            ContextCompat.getColor(context, R.color.receive_text_color)
        }

        // 文本块最大宽度：与文本消息一致取 getViewWidth(...)（pane 宽减头像/勾选/边距），
        // 约束长文本块换行而非撑成超宽气泡 clip/溢出（对齐 WKTextProvider:1240）。
        val textMaxWidth = getViewWidth(from, uiChatMsgItemEntity)

        val model = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKRichTextContent
        if (model == null) {
            // 解析失败兜底：展示已知 plain（若有），否则未知消息提示。
            addTextBlock(blocksLayout, textColor, context.getString(R.string.base_unknow_msg), textMaxWidth)
            addLongClick(contentTvLayout, uiChatMsgItemEntity)
            return
        }

        val blocks = model.blocks
        if (blocks.isNullOrEmpty()) {
            // 无 block：回退顶层 plain，仍为空则未知消息提示。
            addTextBlock(blocksLayout, textColor, fallbackText(model), textMaxWidth)
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
                        addTextBlock(blocksLayout, textColor, block.text, textMaxWidth)
                    }
                }
            }
        }

        // 全部 block 渲染后内容仍为空（如纯未知 block 无 text）→ 回退 plain，勿留空气泡。
        if (blocksLayout.childCount == 0) {
            addTextBlock(blocksLayout, textColor, fallbackText(model), textMaxWidth)
        }

        // 复制 / 回复 / 转发 / 删除 / reaction 走基类标准长按菜单。
        addLongClick(contentTvLayout, uiChatMsgItemEntity)
    }

    /**
     * 渲染发送者昵称行。RichText item 复用基类 chat_item_base_layout 的外部名字行
     * （receivedNameTv，是 wkBaseContentLayout 的兄弟节点，非其后代），故从 parentView
     * 的父容器 fullContentLayout 内查 receivedNameTv 再交给基类 setFromName 处理
     * （群/社区可见、私聊隐藏、实名徽章、@Space 后缀均由 setFromName 内部统一裁决）。
     *
     * <p>full-bind（WKChatBaseProvider.showData → setData）与局部刷新
     * （convert(payloads) → resetFromName）两条路径都需调用，避免首屏昵称不显示。
     */
    private fun applyFromName(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val receivedNameTv = (parentView.parent as? View)
            ?.findViewById<TextView>(R.id.receivedNameTv) ?: return
        setFromName(uiChatMsgItemEntity, from, receivedNameTv)
    }

    override fun resetFromName(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        applyFromName(parentView, uiChatMsgItemEntity, from)
    }

    /** block 渲染为空时的兜底文本：优先顶层 plain，否则未知消息提示。 */
    private fun fallbackText(model: WKRichTextContent): String {
        return if (!TextUtils.isEmpty(model.plain)) {
            model.plain
        } else {
            context.getString(R.string.base_unknow_msg)
        }
    }

    private fun addTextBlock(parent: LinearLayout, textColor: Int, text: String?, maxWidth: Int) {
        val tv = EmojiTextView(context).apply {
            this.text = text ?: ""
            setTextColor(textColor)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                context.resources.getDimension(R.dimen.font_size_16)
            )
            setLineSpacing(2f * context.resources.displayMetrics.density, 1f)
            movementMethod = LinkMovementMethod.getInstance()
            // 与文本消息一致：约束最大宽度，长文本块换行而非撑成超宽气泡 clip/溢出。
            if (maxWidth > 0) {
                this.maxWidth = maxWidth
            }
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
        val showUrl = WKApiConfig.getShowUrl(block.url)
        // URL 为空时不 addView：避免一个有尺寸的空白 ImageView 在气泡里留下空白矩形。
        if (TextUtils.isEmpty(showUrl)) {
            return
        }
        val imageView = AppCompatImageView(context)
        val wh = ImageUtils.getInstance().getImageWidthAndHeightToTalk(block.width, block.height)
        val lp = LinearLayout.LayoutParams(wh[0], wh[1]).apply {
            topMargin = AndroidUtilities.dp(4f)
            bottomMargin = AndroidUtilities.dp(4f)
        }
        GlideUtils.getInstance().showImg(context, showUrl, wh[0], wh[1], imageView)
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
