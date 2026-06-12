/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.context

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chat.uikit.R

/**
 * 上下文 Tab 主页入口网格 adapter, 1:1 对齐 iOS OctoContextEntryVC.buildItems。
 *
 * iOS 的 [OctoContextEntryItem] 模型有: itemId / titleKey / iconBuilder / onTap。
 * Android 这里用 sealed-like data class [Entry], 把图标类型与点击行为内聚。
 *
 * 当前只有 1 项 "智能总结" (smart_summary), 未来加新 AI 能力时往 buildEntries 里追加。
 */
class ContextEntryAdapter(
    private val entries: List<Entry>,
) : RecyclerView.Adapter<ContextEntryAdapter.ViewHolder>() {

    fun interface OnEntryClick {
        fun onClick(entry: Entry)
    }

    private var clickListener: OnEntryClick? = null

    fun onClick(listener: OnEntryClick) {
        clickListener = listener
    }

    data class Entry(
        val itemId: String,
        /** 标题资源 ID, 切语言时通过 notifyDataSetChanged 自动取最新 LLang 文案. */
        val titleRes: Int,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_context_entry, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val e = entries[position]
        holder.label.setText(e.titleRes)
        holder.itemView.setOnClickListener { clickListener?.onClick(e) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.labelTv)
    }
}
