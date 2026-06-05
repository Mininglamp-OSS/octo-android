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

package com.chat.uikit.chat.adapter

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.chat.manager.WKRichTextComposeModel

/**
 * 图文混排（RichText=14）输入框附件托盘的缩略图适配器（Phase 2，对齐 web#237）。
 *
 * <p>把 [WKRichTextComposeModel] 的有序图片项渲染成一排缩略图：每个缩略图右上角带一个 ✕
 * 移除按钮，整排支持长按拖拽调序（由外部 ItemTouchHelper 驱动，本适配器只暴露
 * [onItemMove] 把 UI 拖拽映射回模型）。完全用代码构建 itemView（不引入新 layout xml），
 * 与既有 newImageLayout / chatTopView 等输入区附属视图同款做法。
 *
 * @param model      托盘数据源（顺序的唯一权威）
 * @param onRemove   点 ✕ 移除某项（按稳定 id）后回调，宿主据此刷新发送键可见性等
 * @param onReorder  拖拽结束 / 任一次相邻交换后回调，宿主据此知道顺序已变
 */
class WKRichTextTrayAdapter(
    private val context: Context,
    private val model: WKRichTextComposeModel,
    private val onRemove: (id: Long) -> Unit,
    private val onReorder: () -> Unit,
) : RecyclerView.Adapter<WKRichTextTrayAdapter.TrayViewHolder>() {

    private val thumbSizeDp = 60
    private val cellSizeDp = 72 // 缩略图 + ✕ 溢出的外边距

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrayViewHolder {
        val cell = FrameLayout(context)
        cell.layoutParams = RecyclerView.LayoutParams(
            AndroidUtilities.dp(cellSizeDp.toFloat()),
            AndroidUtilities.dp(cellSizeDp.toFloat())
        )

        val thumb = AppCompatImageView(context)
        thumb.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        thumb.setImageResource(R.drawable.default_view_bg)
        val thumbLp = FrameLayout.LayoutParams(
            AndroidUtilities.dp(thumbSizeDp.toFloat()),
            AndroidUtilities.dp(thumbSizeDp.toFloat()),
            Gravity.CENTER
        )
        cell.addView(thumb, thumbLp)

        val remove = AppCompatImageView(context)
        remove.setImageResource(com.chat.base.R.drawable.ic_tray_remove)
        remove.setBackgroundResource(com.chat.base.R.drawable.bg_tray_remove_btn)
        remove.scaleType = android.widget.ImageView.ScaleType.CENTER
        val removeLp = FrameLayout.LayoutParams(
            AndroidUtilities.dp(22f),
            AndroidUtilities.dp(22f),
            Gravity.TOP or Gravity.END
        )
        cell.addView(remove, removeLp)

        return TrayViewHolder(cell, thumb, remove)
    }

    override fun onBindViewHolder(holder: TrayViewHolder, position: Int) {
        val items = model.items()
        if (position < 0 || position >= items.size) {
            return
        }
        val item = items[position]
        GlideUtils.getInstance().showImg(context, item.localPath, holder.thumb)
        holder.removeBtn.setOnClickListener {
            // 用稳定 id（非 adapterPosition）移除，避免拖拽 / 并发刷新时下标错位删错图。
            onRemove(item.id)
        }
    }

    override fun getItemCount(): Int = model.size()

    /**
     * ItemTouchHelper 的逐格拖拽回调：把 UI 的 from→to 交换如实落到模型，保证发送时
     * 的「真实当前顺序」与用户所见一致。
     *
     * @return true 表示交换成功（模型已变）
     */
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val moved = model.move(fromPosition, toPosition)
        if (moved) {
            notifyItemMoved(fromPosition, toPosition)
            onReorder()
        }
        return moved
    }

    class TrayViewHolder(
        itemView: View,
        val thumb: AppCompatImageView,
        val removeBtn: AppCompatImageView,
    ) : RecyclerView.ViewHolder(itemView)
}
