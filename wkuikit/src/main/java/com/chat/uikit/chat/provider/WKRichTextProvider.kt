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

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.emoji2.widget.EmojiTextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chat.base.config.WKApiConfig
import com.chat.base.msg.ChatContentSpanType
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKMsgBgType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKDialogUtils
import com.chat.base.ui.Theme
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.WKRichTextContent
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.msgmodel.WKMsgEntity

class WKRichTextProvider : WKChatBaseProvider() {

    companion object {
        private const val MAX_RENDER_IMAGES = 20
    }

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
        blocksLayout.removeAllViews()

        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        contentLayout.gravity =
            if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START else Gravity.END

        applyFromName(parentView, uiChatMsgItemEntity, from)

        val textColor = if (from == WKChatIteMsgFromType.SEND) {
            ContextCompat.getColor(context, R.color.colorDark)
        } else {
            ContextCompat.getColor(context, R.color.receive_text_color)
        }

        val textMaxWidth = getViewWidth(from, uiChatMsgItemEntity)

        val model = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKRichTextContent
        if (model == null) {
            addTextBlock(blocksLayout, textColor, context.getString(R.string.base_unknow_msg), textMaxWidth)
            addLongClick(contentTvLayout, uiChatMsgItemEntity)
            return
        }

        val blocks = model.blocks
        if (blocks.isNullOrEmpty()) {
            addTextBlock(blocksLayout, textColor, fallbackText(model), textMaxWidth)
            addLongClick(contentTvLayout, uiChatMsgItemEntity)
            return
        }

        val mentionEntities = uiChatMsgItemEntity.wkMsg.baseContentMsgModel?.entities
            ?.filter { it.type == ChatContentSpanType.mention }
            ?: emptyList()

        val isSelfDarkBubble = Theme.isDark()
                && from == WKChatIteMsgFromType.SEND
        val mentionColor = if (isSelfDarkBubble) Color.WHITE else Theme.colorAccount

        val wkMsg = uiChatMsgItemEntity.wkMsg
        val broadcastsAll = wkMsg.baseContentMsgModel.mentionAll == 1
                || wkMsg.baseContentMsgModel.mentionHumans == 1
                || (wkMsg.baseContentMsgModel.mentionInfo?.humans == true)
        val broadcastsAis = wkMsg.baseContentMsgModel.mentionAis == 1

        val allImageUrls = mutableListOf<String>()
        var imageCount = 0
        var renderedImageIndex = 0
        var textOffset = 0
        for (block in blocks) {
            if (block == null) continue
            when {
                block.isImage() -> {
                    if (imageCount < MAX_RENDER_IMAGES) {
                        val showUrl = WKApiConfig.getShowUrl(block.url)
                        if (!TextUtils.isEmpty(showUrl)) {
                            allImageUrls.add(showUrl)
                            addImageBlock(blocksLayout, block, allImageUrls, renderedImageIndex)
                            renderedImageIndex++
                        }
                        imageCount++
                    }
                }
                else -> {
                    if (!TextUtils.isEmpty(block.text)) {
                        val blockLen = block.text.length
                        val blockEntities = if (mentionEntities.isNotEmpty()) {
                            val startOff = textOffset
                            val endOff = textOffset + blockLen
                            mentionEntities.filter { e ->
                                e.offset >= startOff && (e.offset + e.length) <= endOff
                            }
                        } else {
                            emptyList()
                        }
                        addTextBlock(
                            blocksLayout, textColor, block.text, textMaxWidth,
                            blockEntities, textOffset, mentionColor,
                            uiChatMsgItemEntity,
                            broadcastsAll, broadcastsAis
                        )
                        textOffset += blockLen
                    }
                }
            }
        }

        if (blocksLayout.childCount == 0) {
            addTextBlock(blocksLayout, textColor, fallbackText(model), textMaxWidth)
        }

        addLongClick(contentTvLayout, uiChatMsgItemEntity)
    }

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

    private fun fallbackText(model: WKRichTextContent): String {
        return if (!TextUtils.isEmpty(model.plain)) {
            model.plain
        } else {
            context.getString(R.string.base_unknow_msg)
        }
    }

    private fun addTextBlock(
        parent: LinearLayout,
        textColor: Int,
        text: String?,
        maxWidth: Int,
        mentionEntities: List<WKMsgEntity> = emptyList(),
        blockTextOffset: Int = 0,
        mentionColor: Int = 0,
        uiEntity: WKUIChatMsgItemEntity? = null,
        broadcastsAll: Boolean = false,
        broadcastsAis: Boolean = false
    ) {
        val rawText = text ?: ""
        val spannable = buildMentionSpans(
            rawText, mentionEntities, blockTextOffset, mentionColor, uiEntity,
            broadcastsAll, broadcastsAis
        )

        val tv = EmojiTextView(context).apply {
            if (spannable != null) {
                setText(spannable, TextView.BufferType.SPANNABLE)
            } else {
                this.text = rawText
            }
            setTextColor(textColor)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                context.resources.getDimension(R.dimen.font_size_16)
            )
            setLineSpacing(2f * context.resources.displayMetrics.density, 1f)
            movementMethod = LinkMovementMethod.getInstance()
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

    private fun buildMentionSpans(
        text: String,
        mentionEntities: List<WKMsgEntity>,
        blockTextOffset: Int,
        mentionColor: Int,
        uiEntity: WKUIChatMsgItemEntity?,
        broadcastsAll: Boolean,
        broadcastsAis: Boolean
    ): SpannableStringBuilder? {
        val hasEntities = mentionEntities.isNotEmpty()
        val hasBroadcasts = broadcastsAll || broadcastsAis
        if (!hasEntities && !hasBroadcasts) return null

        val ssb = SpannableStringBuilder(text)

        if (hasEntities && uiEntity != null) {
            val sortedEntities = mentionEntities.sortedByDescending { it.offset }
            var lastProcessedStart = Int.MAX_VALUE
            for (entity in sortedEntities) {
                val localStart = entity.offset - blockTextOffset
                val localEnd = localStart + entity.length
                if (localStart < 0 || localEnd > ssb.length || localEnd > lastProcessedStart) continue
                lastProcessedStart = localStart

                val uid = entity.value ?: continue
                val isSentinel = uid == "-1" || uid == "-2"

                var showName = ssb.subSequence(localStart, localEnd).toString()
                if (!isSentinel) {
                    val channel = WKIM.getInstance().channelManager.getChannel(uid, WKChannelType.PERSONAL)
                    if (channel != null) {
                        val name = if (TextUtils.isEmpty(channel.channelRemark)) channel.channelName else channel.channelRemark
                        if (!TextUtils.isEmpty(name)) {
                            showName = if (name.startsWith("@")) name else "@$name"
                            showName = "$showName "
                        }
                    }
                }

                val nameSpan = SpannableStringBuilder(showName)
                nameSpan.setSpan(StyleSpan(Typeface.BOLD), 0, showName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                if (!isSentinel && uiEntity.iLinkClick != null) {
                    val wkMsg = uiEntity.wkMsg
                    val groupNo = if (wkMsg.channelType == WKChannelType.GROUP
                        || wkMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
                        wkMsg.channelID
                    } else ""
                    val clickContent = "$uid|$groupNo"
                    nameSpan.setSpan(
                        NormalClickableSpan(
                            false, mentionColor,
                            NormalClickableContent(NormalClickableContent.NormalClickableTypes.Remind, clickContent),
                            object : NormalClickableSpan.IClick {
                                override fun onClick(view: View) {
                                    uiEntity.iLinkClick.onShowUserDetail(uid, groupNo)
                                }
                            }
                        ),
                        0, showName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    nameSpan.setSpan(
                        ForegroundColorSpan(mentionColor),
                        0, showName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                ssb.replace(localStart, localEnd, nameSpan)
            }
        }

        if (hasBroadcasts) {
            applyBroadcastHighlight(ssb, mentionColor, broadcastsAll, broadcastsAis)
        }

        return ssb
    }

    private fun applyBroadcastHighlight(
        ssb: SpannableStringBuilder,
        mentionColor: Int,
        broadcastsAll: Boolean,
        broadcastsAis: Boolean
    ) {
        val tokens = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        if (broadcastsAll) {
            addToken(tokens, seen, "@所有人")
            addToken(tokens, seen, "@All People")
            addToken(tokens, seen, "@All")
            addToken(tokens, seen, "@" + context.getString(R.string.base_mention_all))
        }
        if (broadcastsAis) {
            addToken(tokens, seen, "@所有AI")
            addToken(tokens, seen, "@All AIs")
            addToken(tokens, seen, "@" + context.getString(R.string.base_mention_all_ais))
        }
        tokens.sortByDescending { it.length }

        val currentText = ssb.toString()
        for (token in tokens) {
            var fromIndex = 0
            while (fromIndex < currentText.length) {
                val idx = currentText.indexOf(token, fromIndex)
                if (idx < 0) break
                val end = idx + token.length
                if (end < currentText.length) {
                    val nextCh = currentText[end]
                    if (nextCh.isLetterOrDigit() || nextCh == '_') {
                        fromIndex = idx + 1
                        continue
                    }
                }
                ssb.setSpan(ForegroundColorSpan(mentionColor), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(StyleSpan(Typeface.BOLD), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                fromIndex = end
            }
        }
    }

    private fun addToken(tokens: MutableList<String>, seen: MutableSet<String>, token: String) {
        val key = token.lowercase()
        if (key.length <= 1 || seen.contains(key)) return
        seen.add(key)
        tokens.add(token)
    }

    private fun addImageBlock(parent: LinearLayout, block: WKRichTextContent.RichTextBlock, allImageUrls: List<String>, imageIndex: Int) {
        val showUrl = WKApiConfig.getShowUrl(block.url)
        if (TextUtils.isEmpty(showUrl)) return

        val maxLength = AndroidUtilities.dp(220f)
        val (w, h) = scaleToFit(block.width, block.height, maxLength)

        val imageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        val lp = LinearLayout.LayoutParams(w, h).apply {
            topMargin = AndroidUtilities.dp(4f)
            bottomMargin = AndroidUtilities.dp(4f)
        }

        Glide.with(context)
            .load(showUrl)
            .transform(CenterInside(), RoundedCorners(AndroidUtilities.dp(8f)))
            .override(w, h)
            .into(imageView)

        imageView.setOnClickListener {
            val imgList = allImageUrls.map { it as Any }.toMutableList()
            val ivList = mutableListOf<ImageView?>(imageView)
            for (i in 1 until imgList.size) ivList.add(null)
            WKDialogUtils.getInstance().showImagePopup(
                context, imgList, ivList, imageView, imageIndex, null, null, null
            )
        }

        parent.addView(imageView, lp)
    }

    private fun scaleToFit(origW: Int, origH: Int, maxLength: Int): Pair<Int, Int> {
        if (origW <= 0 || origH <= 0) return Pair(maxLength, maxLength)
        val scale = minOf(maxLength.toFloat() / origW, maxLength.toFloat() / origH, 1f)
        val w = (origW * scale).toInt().coerceAtLeast(AndroidUtilities.dp(60f))
        val h = (origH * scale).toInt().coerceAtLeast(AndroidUtilities.dp(60f))
        return Pair(w, h)
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
