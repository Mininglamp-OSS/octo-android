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
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msgitem.WKContentType
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R

class GlobalAdapter : BaseMultiItemQuickAdapter<DataVO, BaseViewHolder>() {
    init {
        addItemType(-1, R.layout.item_global_span_layout)
        addItemType(0, R.layout.item_global_text_layout)
        addItemType(1, R.layout.item_global_channel_layout)
        addItemType(2, R.layout.item_global_message_layout)
        addItemType(3, R.layout.item_global_search_layout)
        addItemType(4, R.layout.item_global_message_layout)
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
            avatarView.showAvatar(item.channel?.channel_id, item.channel!!.channel_type)
            holder.setText(R.id.nameTv, item.channel.channel_name ?: "")
            val contentTv = holder.getView<TextView>(R.id.contentTv)
            contentTv.text = highlightKeyword(item.text, item.keyword)
            holder.setText(R.id.timeTv, "")
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
