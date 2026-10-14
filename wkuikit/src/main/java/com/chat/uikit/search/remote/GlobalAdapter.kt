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

package com.chat.uikit.search.remote

import android.graphics.Color
import android.os.Build
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msgitem.WKContentType
import com.chat.base.search.channel.Rfc3339
import com.chat.base.space.SpaceFilter
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.provider.WKFileProvider
import com.xinbida.wukongim.entity.WKChannelType

class GlobalAdapter : BaseMultiItemQuickAdapter<DataVO, BaseViewHolder>() {
    init {
        addItemType(-1, R.layout.item_global_span_layout)
        addItemType(0, R.layout.item_global_text_layout)
        addItemType(1, R.layout.item_global_channel_layout)
        addItemType(2, R.layout.item_global_message_layout)
        addItemType(3, R.layout.item_global_search_layout)
        addItemType(4, R.layout.item_global_message_layout)
        addItemType(DataVO.FILE, R.layout.item_channel_search_file)
        addItemType(DataVO.MORE, R.layout.item_global_more_layout)
    }

    override fun convert(holder: BaseViewHolder, item: DataVO) {
        if (item.itemType == 0) {
            holder.setText(R.id.textView, item.text)
        } else if (item.itemType == 1 || item.itemType == 2) {
            val avatarView = holder.getView<AvatarView>(R.id.avatarView)
            avatarView.setSize(40f)
            avatarView.showAvatar(item.channel?.channel_id, item.channel!!.channel_type)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                holder.setText(R.id.nameTv,  Html.fromHtml(item.channel.getHtmlName(),Html.FROM_HTML_MODE_LEGACY))
            } else {
                holder.setText(R.id.nameTv,  Html.fromHtml(item.channel.getHtmlName()))
            }
            if (item.itemType == 2) {

                val contentTv = holder.getView<TextView>(R.id.contentTv)
                val type = item.message?.getContentType()
                if (type == WKContentType.WK_TEXT) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        contentTv.text = Html.fromHtml(item.message.getHtmlText(),Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        contentTv.text = Html.fromHtml(item.message.getHtmlText())
                    }
                } else if (type == WKContentType.WK_FILE) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        contentTv.text = Html.fromHtml(item.message.getHtmlWithField("name"), Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        contentTv.text = Html.fromHtml(item.message.getHtmlWithField("name"))
                    }
                }
                holder.setText(
                    R.id.timeTv,
                    WKTimeUtils.getInstance().getTimeString(item.message!!.timestamp * 1000)
                )
            }

        } else if (item.itemType == 3) {
            holder.setText(R.id.searchKeyTv, item.text)
        } else if (item.itemType == 4) {
            val avatarView = holder.getView<AvatarView>(R.id.avatarView)
            avatarView.setSize(40f)
            // 子区（channel_type=5）的 channel_id 是 "{parent}____{thread}" 复合结构，
            // AvatarView 无法直接加载；子区在产品上没有独立头像，沿用父群头像。
            val ch = item.channel!!
            if (ch.channel_type == WKChannelType.COMMUNITY_TOPIC) {
                val parent = SpaceFilter.extractParentGroupId(ch.channel_id) ?: ch.channel_id
                avatarView.showAvatar(parent, WKChannelType.GROUP)
            } else {
                avatarView.showAvatar(ch.channel_id, ch.channel_type)
            }
            holder.setText(R.id.nameTv, ch.channel_name ?: "")
            val contentTv = holder.getView<TextView>(R.id.contentTv)
            contentTv.text = highlightKeyword(item.text, item.keyword)
            holder.setText(R.id.timeTv, "")
        } else if (item.itemType == DataVO.FILE) {
            val hit = item.fileHit!!
            val iconIv = holder.getView<ImageView>(R.id.fileTagIv)
            WKFileProvider.setFileIcon(iconIv, hit.file_ext, hit.file_name)

            val nameTv = holder.getView<TextView>(R.id.nameTv)
            val raw = hit.file_name
            if (raw.contains("<mark>")) {
                val html = raw.replace("<mark>", "<font color=#7761F4>").replace("</mark>", "</font>")
                nameTv.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION") Html.fromHtml(html)
                }
            } else {
                nameTv.text = raw
            }

            val sizeTv = holder.getView<TextView>(R.id.sizeTv)
            sizeTv.text = WKFileProvider.formatFileSize(hit.file_size_bytes)

            val senderTv = holder.getView<TextView>(R.id.senderTv)
            senderTv.text = hit.sender_name.orEmpty()
            senderTv.visibility = if (TextUtils.isEmpty(hit.sender_name)) View.GONE else View.VISIBLE

            val timeTv = holder.getView<TextView>(R.id.timeTv)
            val epochSec = Rfc3339.toEpochSeconds(hit.sent_at)
            timeTv.text = if (epochSec > 0) WKTimeUtils.getInstance().getTimeString(epochSec * 1000) else ""
        } else if (item.itemType == DataVO.MORE) {
            holder.setText(R.id.moreTv, item.text)
        }
    }

    companion object {
        private const val HIGHLIGHT_COLOR = 0xFF7761F4.toInt()

        fun highlightKeyword(text: String, keyword: String): CharSequence {
            if (keyword.isEmpty() || text.isEmpty()) return text
            val spannable = SpannableString(text)
            val lowerText = text.lowercase()
            val lowerKeyword = keyword.lowercase()
            var start = lowerText.indexOf(lowerKeyword)
            while (start >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(HIGHLIGHT_COLOR),
                    start, start + keyword.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                start = lowerText.indexOf(lowerKeyword, start + keyword.length)
            }
            return spannable
        }
    }
}
