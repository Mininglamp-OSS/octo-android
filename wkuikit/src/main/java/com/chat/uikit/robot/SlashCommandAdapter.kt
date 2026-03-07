package com.chat.uikit.robot

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.uikit.R
import com.chat.uikit.robot.entity.WKRobotMenuEntity

class SlashCommandAdapter : BaseQuickAdapter<WKRobotMenuEntity, BaseViewHolder>(R.layout.item_slash_command) {

    private var allItems: List<WKRobotMenuEntity> = emptyList()

    override fun convert(holder: BaseViewHolder, item: WKRobotMenuEntity) {
        holder.setText(R.id.cmdTv, item.cmd)
        holder.setText(R.id.remarkTv, item.remark)
    }

    fun setAllItems(items: List<WKRobotMenuEntity>) {
        allItems = items
        setList(items)
    }

    fun filter(query: String) {
        if (query.isEmpty()) {
            setList(allItems)
            return
        }
        val lower = query.lowercase()
        val filtered = allItems.filter {
            it.cmd.lowercase().contains(lower) || it.remark.lowercase().contains(lower)
        }
        setList(filtered)
    }
}
