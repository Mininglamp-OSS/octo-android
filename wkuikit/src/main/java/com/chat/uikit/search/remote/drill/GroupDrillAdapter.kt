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

package com.chat.uikit.search.remote.drill

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.search.channel.Rfc3339
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.space.SpaceFilter
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.xinbida.wukongim.entity.WKChannelType

/**
 * L2 深度浏览页 adapter。展示 [CombinedHit] 混合流（message + file 混排，按 sorted_at 倒序）。
 *
 * 复用 `item_global_message_layout`：avatar + 顶行 (name+time) + 底行 ([icon]+content)。
 * MESSAGE 和 FILE 使用同一 layout，通过 result_type 分派内容渲染，无需 multi-item。
 * - MESSAGE：avatar=会话头像，name=发送者名，content=snippet（服务端 `<mark>` 转紫色前景色）
 * - FILE：avatar=会话头像，name=文件名，content=大小 · 发送者名，前置文件图标
 */
class GroupDrillAdapter :
    BaseQuickAdapter<CombinedHit, BaseViewHolder>(R.layout.item_global_message_layout) {

    override fun convert(holder: BaseViewHolder, item: CombinedHit) {
        val avatar = holder.getView<AvatarView>(R.id.avatarView)
        avatar.setSize(40f)
        val nameTv = holder.getView<TextView>(R.id.nameTv)
        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val fileIcon = holder.getView<ImageView>(R.id.fileTagIv)

        if (item.isFile() && item.file != null) {
            val f = item.file!!
            showChannelAvatar(avatar, f.channel_id, f.channel_type)
            nameTv.text = f.file_name
            timeTv.text = f.sent_at?.let { formatTime(it) } ?: ""
            fileIcon.visibility = View.VISIBLE
            val senderName = f.sender_name?.takeIf { it.isNotEmpty() } ?: f.sender_id
            contentTv.text = "${formatFileSize(f.file_size_bytes)} · $senderName"
        } else if (item.isMessage() && item.message != null) {
            val m = item.message!!
            showChannelAvatar(avatar, m.channel_id, m.channel_type)
            nameTv.text = m.sender_name?.takeIf { it.isNotEmpty() } ?: m.sender_id
            timeTv.text = formatTime(m.sent_at)
            fileIcon.visibility = View.GONE
            contentTv.text = markToSpannable(m.snippet)
        }
    }

    /**
     * 子区（channel_type=5）channel_id 是 "{parent}____{thread}" 复合结构，
     * [AvatarView] 无法直接加载；子区在产品上没有独立头像，沿用父群头像。
     */
    private fun showChannelAvatar(avatar: AvatarView, channelId: String, channelType: Byte) {
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            val parent = SpaceFilter.extractParentGroupId(channelId) ?: channelId
            avatar.showAvatar(parent, WKChannelType.GROUP)
        } else {
            avatar.showAvatar(channelId, channelType)
        }
    }

    private fun formatTime(rfc3339: String): String {
        // 项目 minSdk=23 未开 coreLibraryDesugaring，禁用 java.time；用现有 Rfc3339 helper。
        // 解析失败返回 0，此时显示为空串，UI 上表现为无时间（可接受）。
        val seconds = Rfc3339.toEpochSeconds(rfc3339)
        return if (seconds > 0L) WKTimeUtils.getInstance().getTimeString(seconds * 1000L) else ""
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    /** 把服务端 `<mark>` 高亮标记转成前景色 SpannableString，与全局搜索一致的紫色。 */
    private fun markToSpannable(snippet: String?): CharSequence {
        if (snippet.isNullOrEmpty()) return ""
        val marks = mutableListOf<IntRange>()
        val plain = StringBuilder()
        var i = 0
        while (i < snippet.length) {
            when {
                snippet.startsWith("<mark>", i) -> {
                    val end = snippet.indexOf("</mark>", i + 6)
                    if (end < 0) {
                        plain.append(snippet, i, snippet.length)
                        break
                    }
                    val start = plain.length
                    plain.append(snippet, i + 6, end)
                    marks += start until plain.length
                    i = end + 7
                }
                else -> {
                    plain.append(snippet[i])
                    i++
                }
            }
        }
        val sp = SpannableString(plain.toString())
        for (r in marks) {
            sp.setSpan(
                ForegroundColorSpan(HIGHLIGHT_COLOR),
                r.first, r.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return sp
    }

    companion object {
        private const val HIGHLIGHT_COLOR = 0xFF7761F4.toInt()
    }
}
