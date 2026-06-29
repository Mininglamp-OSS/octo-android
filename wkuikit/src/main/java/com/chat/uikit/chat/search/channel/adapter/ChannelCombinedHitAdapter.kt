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
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.dto.MessageHit
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.provider.WKFileProvider
import com.xinbida.wukongim.WKIM

/**
 * `/_search_all` 混排结果适配器。message / file 两种 result_type 分别走不同 item 布局。
 *  - message 复用 [R.layout.item_global_message_layout]（与频道 / 全局搜索同视觉风格）
 *  - file 复用 [R.layout.item_channel_search_file]
 */
class ChannelCombinedHitAdapter(
    private val channelID: String,
    private val channelType: Byte,
    private val channelName: String,
) : BaseMultiItemQuickAdapter<ChannelCombinedHitAdapter.Entry, BaseViewHolder>() {

    init {
        addItemType(TYPE_MESSAGE, R.layout.item_global_message_layout)
        addItemType(TYPE_FILE, R.layout.item_channel_search_file)
    }

    sealed class Entry : MultiItemEntity {
        data class Message(val hit: MessageHit) : Entry() {
            override val itemType: Int = TYPE_MESSAGE
        }

        data class File(val hit: FileHit) : Entry() {
            override val itemType: Int = TYPE_FILE
        }
    }

    override fun convert(holder: BaseViewHolder, item: Entry) {
        when (item) {
            is Entry.Message -> bindMessage(holder, item.hit)
            is Entry.File -> bindFile(holder, item.hit)
        }
    }

    private fun bindMessage(holder: BaseViewHolder, hit: MessageHit) {
        val avatar = holder.getView<AvatarView>(R.id.avatarView)
        avatar.setSize(40f)
        avatar.showAvatar(channelID, channelType)
        holder.setText(R.id.nameTv, channelName)

        val contentTv = holder.getView<TextView>(R.id.contentTv)
        contentTv.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(hit.getHighlightedHtml(), Html.FROM_HTML_MODE_LEGACY)
        else
            @Suppress("DEPRECATION") Html.fromHtml(hit.getHighlightedHtml())

        holder.getView<View>(R.id.fileTagIv).visibility = View.GONE

        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val epochSec = com.chat.base.search.channel.Rfc3339.toEpochSeconds(hit.sent_at)
        timeTv.text = if (epochSec > 0)
            WKTimeUtils.getInstance().getTimeString(epochSec * 1000)
        else ""
    }

    private fun bindFile(holder: BaseViewHolder, hit: FileHit) {
        val iconIv = holder.getView<ImageView>(R.id.fileTagIv)
        WKFileProvider.setFileIcon(iconIv, hit.file_ext, hit.file_name)

        val nameTv = holder.getView<TextView>(R.id.nameTv)
        val raw = hit.file_name
        nameTv.text = if (raw.contains("<mark>")) {
            val html = raw.replace("<mark>", "<font color=#7761F4>").replace("</mark>", "</font>")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            else
                @Suppress("DEPRECATION") Html.fromHtml(html)
        } else {
            raw
        }

        holder.setText(R.id.sizeTv, WKFileProvider.formatFileSize(hit.file_size_bytes))
        val senderTv = holder.getView<TextView>(R.id.senderTv)
        senderTv.text = hit.sender_name.orEmpty()
        senderTv.visibility = if (TextUtils.isEmpty(hit.sender_name)) View.GONE else View.VISIBLE

        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val epochSec = com.chat.base.search.channel.Rfc3339.toEpochSeconds(hit.sent_at)
        timeTv.text = if (epochSec > 0)
            WKTimeUtils.getInstance().getTimeString(epochSec * 1000)
        else ""
    }

    companion object {
        const val TYPE_MESSAGE = 0
        const val TYPE_FILE = 1

        fun toEntries(hits: List<CombinedHit>): List<Entry> = hits.mapNotNull { h ->
            when {
                h.isMessage() && h.message != null -> Entry.Message(h.message!!)
                h.isFile() && h.file != null -> Entry.File(h.file!!)
                else -> null
            }
        }
    }
}
