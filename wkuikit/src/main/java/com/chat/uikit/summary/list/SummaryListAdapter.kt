/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.summary.model.SummaryListItem
import com.chat.uikit.R

/**
 * RecyclerView adapter, 通过 [SummaryCardBinder] 把 item 投影到 item_summary_card。
 *
 * 不实现 multi-viewType: 单一 layout, processing/waiting 视觉差异由背景 + spinner +
 * statusBadge 显隐控制 (与 iOS 同 cell 内分支). DiffUtil 用 taskId + status + summaryPreview
 * 三元判断, 状态变化或 preview 回填都触发对应 row reload。
 */
class SummaryListAdapter : ListAdapter<SummaryListItem, SummaryListAdapter.ViewHolder>(DIFF) {

    fun interface OnItemClick {
        fun onClick(item: SummaryListItem)
    }

    fun interface OnMoreClick {
        fun onClick(item: SummaryListItem, anchor: View)
    }

    private var itemClick: OnItemClick? = null
    private var moreClick: OnMoreClick? = null

    var keyword: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_KEYWORD)
        }

    var currentUserName: String? = null

    fun onItemClick(listener: OnItemClick) {
        itemClick = listener
    }

    fun onMoreClick(listener: OnMoreClick) {
        moreClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_summary_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        SummaryCardBinder.bind(holder.itemView, item, currentUserName, keyword)
        holder.itemView.findViewById<View>(R.id.cardRoot)?.setOnClickListener {
            itemClick?.onClick(item)
        }
        holder.itemView.findViewById<ImageView>(R.id.moreBtn)?.setOnClickListener { btn ->
            moreClick?.onClick(item, btn)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private const val PAYLOAD_KEYWORD = "keyword"

        private val DIFF = object : DiffUtil.ItemCallback<SummaryListItem>() {
            override fun areItemsTheSame(o: SummaryListItem, n: SummaryListItem): Boolean =
                o.taskId == n.taskId

            override fun areContentsTheSame(o: SummaryListItem, n: SummaryListItem): Boolean =
                o.taskId == n.taskId &&
                    o.status == n.status &&
                    o.title == n.title &&
                    o.summaryPreview == n.summaryPreview &&
                    o.completedAt == n.completedAt
        }
    }
}
