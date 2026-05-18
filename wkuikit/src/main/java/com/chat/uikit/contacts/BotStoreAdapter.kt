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

package com.chat.uikit.contacts

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.LayoutHelper
import com.chat.uikit.R

class BotStoreAdapter :
    BaseQuickAdapter<BotStoreUIEntity, BaseViewHolder>(R.layout.item_friend_layout) {

    override fun convert(holder: BaseViewHolder, item: BotStoreUIEntity) {
        holder.setText(R.id.nameTv, item.channel.channelName ?: "")

        val index: Int = holder.bindingAdapterPosition - headerLayoutCount
        val firstLetter = if (item.pying.isNotEmpty()) item.pying.substring(0, 1) else "#"
        val index1: Int = getPositionForSection(firstLetter)
        holder.setText(R.id.pyTv, firstLetter)
        holder.setGone(R.id.pyTv, index != index1)

        val avatarView: AvatarView = holder.getView(R.id.avatarView)
        avatarView.setSize(50f)
        avatarView.showAvatar(item.channel, true)

        // AI badge
        val linearLayout: LinearLayout = holder.getView(R.id.categoryLayout)
        linearLayout.removeAllViews()
        val aiBadge = TextView(context).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_ai_badge)
            val hPad = AndroidUtilities.dp(5f)
            val vPad = AndroidUtilities.dp(1f)
            setPadding(hPad, vPad, hPad, vPad)
        }
        linearLayout.addView(
            aiBadge,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 5, 1, 1, 0
            )
        )

        // Show description in offlineTv position
        val offlineTv: TextView = holder.getView(R.id.offlineTv)
        if (!TextUtils.isEmpty(item.description)) {
            offlineTv.text = item.description
            offlineTv.visibility = android.view.View.VISIBLE
        } else {
            offlineTv.visibility = android.view.View.GONE
        }
    }

    private fun getPositionForSection(catalog: String): Int {
        var i = 0
        val size = data.size
        while (i < size) {
            val pying = data[i].pying
            val sortStr = if (pying.isNotEmpty()) pying.substring(0, 1) else "#"
            if (catalog.equals(sortStr, ignoreCase = true)) {
                return i
            }
            i++
        }
        return -1
    }
}
