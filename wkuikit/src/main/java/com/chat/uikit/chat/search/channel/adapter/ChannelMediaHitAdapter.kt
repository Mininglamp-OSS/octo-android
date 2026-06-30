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

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.glide.GlideUtils
import com.chat.base.search.channel.dto.MediaHit
import com.chat.base.views.pinnedsectionitemdecoration.utils.FullSpanUtil
import com.chat.uikit.R

/**
 * 频道内媒体（图片+视频）网格 Adapter。
 *  - [ITEM_TYPE_HEADER]: 月份分组头（YYYY-MM），sticky 由 PinnedHeaderItemDecoration(1) 处理
 *  - [ITEM_TYPE_MEDIA] : 单个媒体格子
 *
 * 数据排好序后 [setData]：相邻同月份的 hit 之间不再插 header。
 */
class ChannelMediaHitAdapter(private val cellSize: Int) :
    BaseMultiItemQuickAdapter<ChannelMediaHitAdapter.Entry, BaseViewHolder>() {

    init {
        addItemType(ITEM_TYPE_MEDIA, R.layout.item_channel_search_media)
        addItemType(ITEM_TYPE_HEADER, R.layout.item_channel_search_media_header)
    }

    sealed class Entry : MultiItemEntity {
        data class Header(val monthBucket: String) : Entry() {
            override val itemType: Int = ITEM_TYPE_HEADER
        }

        data class Item(val hit: MediaHit) : Entry() {
            override val itemType: Int = ITEM_TYPE_MEDIA
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        FullSpanUtil.onAttachedToRecyclerView(recyclerView, this, ITEM_TYPE_HEADER)
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        super.onViewAttachedToWindow(holder)
        FullSpanUtil.onViewAttachedToWindow(holder, this, ITEM_TYPE_HEADER)
    }

    override fun convert(holder: BaseViewHolder, item: Entry) {
        when (item) {
            is Entry.Header -> holder.setText(R.id.monthHeaderTv, item.monthBucket)
            is Entry.Item -> {
                // 把单元格 root 撑到 cellSize × cellSize：仅改 ImageView 大小不够，因为 layout 根布局
                // 以前用 match_parent，会让 GridLayoutManager 把整行拉到 RV 高度，出现"上面一行全是空白"的现象。
                val root = holder.itemView
                root.layoutParams = (root.layoutParams ?: ViewGroup.LayoutParams(cellSize, cellSize))
                    .also { it.width = cellSize; it.height = cellSize }
                val iv = holder.getView<ImageView>(R.id.thumbIv)
                GlideUtils.getInstance().showImg(context, item.hit.thumb_url, iv)
                val playIv = holder.getView<ImageView>(R.id.playIv)
                val durationTv = holder.getView<TextView>(R.id.durationTv)
                val isVideo = item.hit.isVideo()
                playIv.visibility = if (isVideo) View.VISIBLE else View.GONE
                if (isVideo && item.hit.duration_ms > 0) {
                    durationTv.text = formatDuration(item.hit.duration_ms)
                    durationTv.visibility = View.VISIBLE
                } else {
                    durationTv.visibility = View.GONE
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    companion object {
        const val ITEM_TYPE_HEADER = 1
        const val ITEM_TYPE_MEDIA = 0

        /** 给定一段已按 [MediaHit.sent_at] 倒序的命中，按 month_bucket 切分插入 header。 */
        fun toEntries(hits: List<MediaHit>): List<Entry> {
            val out = ArrayList<Entry>(hits.size + 8)
            var lastBucket: String? = null
            for (hit in hits) {
                if (hit.month_bucket != lastBucket) {
                    out.add(Entry.Header(hit.month_bucket))
                    lastBucket = hit.month_bucket
                }
                out.add(Entry.Item(hit))
            }
            return out
        }
    }
}
