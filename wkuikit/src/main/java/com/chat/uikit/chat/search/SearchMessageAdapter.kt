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

package com.chat.uikit.chat.search

import android.os.Build
import android.text.Html
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.entity.GlobalMessage
import com.chat.base.msgitem.WKContentType
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.search.remote.GlobalAdapter

class SearchMessageAdapter :
    BaseQuickAdapter<GlobalMessage, BaseViewHolder>(R.layout.item_global_message_layout) {

    var keyword: String = ""

    override fun convert(holder: BaseViewHolder, item: GlobalMessage) {
        val avatarView = holder.getView<AvatarView>(R.id.avatarView)
        avatarView.setSize(40f)
        avatarView.showAvatar(item.channel.channel_id, item.channel.channel_type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            holder.setText(
                R.id.nameTv,
                Html.fromHtml(item.channel.getHtmlName(), Html.FROM_HTML_MODE_LEGACY)
            )
        } else {
            holder.setText(R.id.nameTv, Html.fromHtml(item.channel.getHtmlName()))
        }
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val rawContent = item.payload["content"]
        if (rawContent is String && rawContent.isNotEmpty() && !rawContent.contains("<mark>")) {
            contentTv.text = GlobalAdapter.highlightKeyword(rawContent, keyword)
        } else {
            val type = item.getContentType()
            if (type == WKContentType.WK_TEXT) {
                contentTv.text = Html.fromHtml(item.getHtmlText(), Html.FROM_HTML_MODE_LEGACY)
            } else if (type == WKContentType.WK_FILE) {
                contentTv.text = Html.fromHtml(item.getHtmlWithField("name"), Html.FROM_HTML_MODE_LEGACY)
            }
        }
        holder.setText(
            R.id.timeTv,
            WKTimeUtils.getInstance().getTimeString(item.timestamp * 1000)
        )
    }
}
