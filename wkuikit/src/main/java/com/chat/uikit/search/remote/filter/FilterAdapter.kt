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

package com.chat.uikit.search.remote.filter

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.uikit.R

/**
 * 筛选页列表 adapter。单一 RecyclerView 渲染全部 section，用 [FilterRow.itemType] 区分 3 种 ItemType。
 * 点击行为完全由构建行列表时注入的 [FilterRow.Nav.onClick]/[FilterRow.Check.onClick] 闭包决定，
 * adapter 本身不持有任何业务语义。
 */
class FilterAdapter : BaseMultiItemQuickAdapter<FilterRow, BaseViewHolder>() {

    init {
        addItemType(FilterRow.HEADER, R.layout.item_filter_header_layout)
        addItemType(FilterRow.NAV, R.layout.item_filter_nav_row)
        addItemType(FilterRow.CHECK, R.layout.item_filter_check_row)
    }

    override fun convert(holder: BaseViewHolder, item: FilterRow) {
        when (item) {
            is FilterRow.Header -> {
                (holder.itemView as? android.widget.TextView)?.text = item.title
            }
            is FilterRow.Nav -> {
                holder.setText(R.id.titleTv, item.title)
                holder.setText(R.id.detailTv, item.detail)
                holder.itemView.setOnClickListener { item.onClick() }
            }
            is FilterRow.Check -> {
                holder.setText(R.id.titleTv, item.title)
                holder.setVisible(R.id.checkTv, item.checked)
                holder.itemView.setOnClickListener { item.onClick() }
            }
        }
    }
}
