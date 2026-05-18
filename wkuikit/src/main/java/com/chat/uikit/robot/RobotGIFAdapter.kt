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

import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.robot.entity.WKRobotGIFEntity

class RobotGIFAdapter :
    BaseQuickAdapter<WKRobotGIFEntity, BaseViewHolder>(R.layout.item_robot_gif_layout) {
    var showWH = 0

    init {
        showWH = AndroidUtilities.getScreenWidth() / 3
    }

    override fun convert(holder: BaseViewHolder, item: WKRobotGIFEntity) {
        val imageView: ImageView = holder.getView(R.id.imageView)
        imageView.layoutParams.width = showWH
        imageView.layoutParams.height = showWH
        if (!item.isNull) {
            var top = 1
            if (holder.bindingAdapterPosition == 1 || holder.bindingAdapterPosition == 2 || holder.bindingAdapterPosition == 3) {
                top = AndroidUtilities.dp(12f)
            }
            imageView.setPadding(1, top, 1, 1)
            GlideUtils.getInstance().showImg(context, item.url, imageView)
        }
        if (holder.bindingAdapterPosition == 1) {
            holder.setBackgroundResource(R.id.contentLayout, R.drawable.left_bg)
        } else if (holder.bindingAdapterPosition == 3) {
            holder.setBackgroundResource(R.id.contentLayout, R.drawable.right_bg)
        } else {
            holder.setBackgroundColor(
                R.id.contentLayout,
                ContextCompat.getColor(context, R.color.layoutColor)
            )
        }
    }
}