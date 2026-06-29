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

package com.chat.uikit.chat.search.channel.adapter

import android.os.Build
import android.text.Html
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.provider.WKFileProvider

/** 频道内文件搜索结果 Adapter。复用 [WKFileProvider.setFileIcon] 与 [WKFileProvider.formatFileSize]。 */
class ChannelFileHitAdapter :
    BaseQuickAdapter<FileHit, BaseViewHolder>(R.layout.item_channel_search_file) {

    override fun convert(holder: BaseViewHolder, item: FileHit) {
        val iconIv = holder.getView<ImageView>(R.id.fileTagIv)
        WKFileProvider.setFileIcon(iconIv, item.file_ext, item.file_name)

        val nameTv = holder.getView<TextView>(R.id.nameTv)
        val raw = item.file_name
        if (raw.contains("<mark>")) {
            val html = raw.replace("<mark>", "<font color=#7761F4>").replace("</mark>", "</font>")
            nameTv.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            else
                @Suppress("DEPRECATION") Html.fromHtml(html)
        } else {
            nameTv.text = raw
        }

        val sizeTv = holder.getView<TextView>(R.id.sizeTv)
        sizeTv.text = WKFileProvider.formatFileSize(item.file_size_bytes)

        val senderTv = holder.getView<TextView>(R.id.senderTv)
        senderTv.text = item.sender_name.orEmpty()
        senderTv.visibility = if (TextUtils.isEmpty(item.sender_name)) View.GONE else View.VISIBLE

        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val epochSec = com.chat.base.search.channel.Rfc3339.toEpochSeconds(item.sent_at)
        timeTv.text = if (epochSec > 0)
            WKTimeUtils.getInstance().getTimeString(epochSec * 1000)
        else ""
    }
}
