/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.choose

import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.ui.Theme
import com.chat.base.ui.components.AvatarView
import com.chat.base.ui.components.CheckBox
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R

/**
 * 新建会话页 adapter. 单一 cell 布局: 头像 / # / 名 / AI 角标 / 右 checkbox.
 */
class ForwardDirAdapter : BaseQuickAdapter<ForwardDirItem, BaseViewHolder>(
    R.layout.item_forward_dir_layout,
) {
    override fun convert(holder: BaseViewHolder, item: ForwardDirItem) {
        holder.setText(R.id.nameTv, item.displayName)
        holder.setGone(R.id.aiBadge, !item.isRobot)

        val avatar: AvatarView = holder.getView(R.id.avatarView)
        val hash: View = holder.getView(R.id.hashTag)
        if (item.showHashPrefix) {
            avatar.visibility = View.GONE
            hash.visibility = View.VISIBLE
        } else {
            avatar.visibility = View.VISIBLE
            hash.visibility = View.GONE
            avatar.showAvatar(item.channel)
        }

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
