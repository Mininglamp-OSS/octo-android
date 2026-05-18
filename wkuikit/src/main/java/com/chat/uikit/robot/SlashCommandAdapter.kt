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

package com.chat.uikit.robot

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.uikit.R
import com.chat.uikit.robot.entity.WKRobotMenuEntity

class SlashCommandAdapter : BaseQuickAdapter<WKRobotMenuEntity, BaseViewHolder>(R.layout.item_slash_command) {

    private var allItems: List<WKRobotMenuEntity> = emptyList()

    override fun convert(holder: BaseViewHolder, item: WKRobotMenuEntity) {
        holder.setText(R.id.cmdTv, "/${item.cmd.orEmpty()}")
        holder.setText(R.id.remarkTv, item.remark.orEmpty())
    }

    fun setAllItems(items: List<WKRobotMenuEntity>) {
        allItems = items.filter { !it.cmd.isNullOrEmpty() }
        setList(allItems)
    }

    fun filter(query: String) {
        if (query.isEmpty()) {
            setList(allItems)
            return
        }
        val lower = query.lowercase()
        val filtered = allItems.filter {
            it.cmd?.lowercase()?.contains(lower) == true
                    || it.remark?.lowercase()?.contains(lower) == true
        }
        setList(filtered)
    }
}
