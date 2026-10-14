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

import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.ui.Theme
import com.chat.base.ui.components.AvatarView
import com.chat.base.ui.components.CheckBox
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R

/** 筛选页二级多选页 adapter：头像 + 名称 + 右 checkbox，复用 [ForwardDirAdapter] 同款视觉。 */
class FilterPickAdapter : BaseQuickAdapter<FilterPickItem, BaseViewHolder>(
    R.layout.item_forward_dir_layout,
) {
    override fun convert(holder: BaseViewHolder, item: FilterPickItem) {
        holder.setText(R.id.nameTv, item.displayName)
        holder.setGone(R.id.aiBadge, true)
        holder.setGone(R.id.hashTag, true)

        val avatar: AvatarView = holder.getView(R.id.avatarView)
        avatar.visibility = View.VISIBLE
        avatar.showAvatar(item.channel)

        val cb: CheckBox = holder.getView(R.id.checkbox)
        cb.setResId(context, R.mipmap.round_check2)
        cb.setDrawBackground(true)
        cb.setHasBorder(true)
        cb.setStrokeWidth(AndroidUtilities.dp(1.5f))
        cb.setBorderColor(ContextCompat.getColor(context, R.color.color999))
        cb.setSize(22)
        cb.setColor(Theme.colorAccount, ContextCompat.getColor(context, R.color.white))
        cb.visibility = View.VISIBLE
        cb.setChecked(item.isCheck, false)
    }
}
