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
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.chat.manager.WKRichTextComposeModel

/**
 * 图文混排（RichText=14）输入框上方「待发送图片栏」适配器（对齐 iOS WKConversationPendingImageBar）。
 *
 * <p>把 [WKRichTextComposeModel] 的有序图片项渲染成一排缩略图：每张右上角带 ✕ 移除按钮；
 * 末尾追加一个「+」cell（仅当 count<MAX 时显示），点击触发 [onAddTapped] 由宿主再拉相册；
 * 点缩略图触发 [onPreview] 由宿主拉起全屏图片浏览器。整排支持长按拖拽调序（外部
 * ItemTouchHelper 驱动；本适配器仅暴露 [onItemMove] 把 UI 拖拽映射回模型，[isReorderable]
 * 用于挡住 + cell 不可拖动）。完全用代码构建 itemView，不引入 layout xml。
 *
 * <p>视觉参数对齐 iOS：thumb 64dp / 间距 8dp / 圆角 12dp / ✕ 22dp 半透明黑底白叉，整条
 * bar 高 80dp（外层容器 padding 配合 64+padding*2）。
 *
 * @param model       托盘数据源（顺序的唯一权威）
 * @param onRemove    点 ✕ 移除某项（按稳定 id）后回调，宿主据此刷新发送键可见性等
 * @param onReorder   拖拽结束 / 任一次相邻交换后回调，宿主据此知道顺序已变
 * @param onAddTapped 点末尾 + cell 时回调，宿主负责再次拉起相册（按 remaining 限张数）后写回模型
 * @param onPreview   点缩略图时回调，宿主负责拉起全屏图片浏览器（index 与模型有序条目对齐）
 */
class WKRichTextTrayAdapter(
    private val context: Context,
    private val model: WKRichTextComposeModel,
    private val onRemove: (id: Long) -> Unit,
    private val onReorder: () -> Unit,
    private val onAddTapped: () -> Unit = {},
    private val onPreview: (index: Int) -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val thumbSizeDp = 64       // 与 iOS kPendingThumbSize 对齐
    private val cellGapDp = 8          // 与 iOS kPendingThumbGap 对齐（右侧 marginEnd 实现）
    private val deleteSizeDp = 22      // 与 iOS kPendingDeleteSize 对齐
    private val cornerRadiusDp = 12f   // 与 iOS cell.layer.cornerRadius 对齐

    companion object {
        private const val TYPE_THUMB = 0
        private const val TYPE_ADD = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (position < model.size()) TYPE_THUMB else TYPE_ADD

    override fun getItemCount(): Int {
        val count = model.size()
        val showAdd = count < WKRichTextComposeModel.MAX_IMAGES
        return count + if (showAdd) 1 else 0
    }

    /** 该位置是否可拖动调序（+ cell 不可拖动；外部 ItemTouchHelper 用此判定）。 */
    fun isReorderable(position: Int): Boolean = position < model.size()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ADD) createAddCell() else createThumbCell()
    }

    private fun createThumbCell(): ThumbViewHolder {
        val cell = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                AndroidUtilities.dp(thumbSizeDp.toFloat()),
                AndroidUtilities.dp(thumbSizeDp.toFloat())
            ).apply { marginEnd = AndroidUtilities.dp(cellGapDp.toFloat()) }
        }

        val thumb = AppCompatImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.default_view_bg)
            // 圆角通过 Glide 的 RoundedCorners transform 在绑定时实现，clip 在容器层做
        }
        cell.addView(
            thumb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        // 容器层裁圆角（与 iOS iv.layer.cornerRadius 等价）
        cell.clipToOutline = true
        cell.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    AndroidUtilities.dp(cornerRadiusDp).toFloat()
                )
            }
        }

        val remove = AppCompatImageView(context).apply {
            setImageResource(com.chat.base.R.drawable.ic_tray_remove)
            setBackgroundResource(com.chat.base.R.drawable.bg_tray_remove_btn)
            scaleType = android.widget.ImageView.ScaleType.CENTER
        }
        // ✕ 在 cell bounds 内紧贴右上角，与 iOS frame=(thumbSize-deleteSize, 0) 对齐。
        // 单独再用一个不裁剪的外层包一下让 ✕ 不被父 cell 的 clipToOutline 切掉。
        val outer = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                AndroidUtilities.dp(thumbSizeDp.toFloat()),
                AndroidUtilities.dp(thumbSizeDp.toFloat())
            ).apply { marginEnd = AndroidUtilities.dp(cellGapDp.toFloat()) }
            clipChildren = false
        }
        outer.addView(
            cell,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        outer.addView(
            remove,
            FrameLayout.LayoutParams(
                AndroidUtilities.dp(deleteSizeDp.toFloat()),
                AndroidUtilities.dp(deleteSizeDp.toFloat()),
                Gravity.TOP or Gravity.END
            )
        )
        return ThumbViewHolder(outer, thumb, remove)
    }

    private fun createAddCell(): AddViewHolder {
        val cell = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                AndroidUtilities.dp(thumbSizeDp.toFloat()),
                AndroidUtilities.dp(thumbSizeDp.toFloat())
            ).apply { marginEnd = AndroidUtilities.dp(cellGapDp.toFloat()) }
            // 浅灰底 + 1dp 边框 + 12dp 圆角，对齐 iOS buildAddCell。
            background = makeAddCellBackground()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height,
                        AndroidUtilities.dp(cornerRadiusDp).toFloat()
                    )
                }
            }
            isClickable = true
            isFocusable = true
        }
        val plus = TextView(context).apply {
            text = "+"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTextColor(Color.parseColor("#737373"))
            gravity = Gravity.CENTER
        }
        cell.addView(
            plus,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return AddViewHolder(cell)
    }

    private fun makeAddCellBackground(): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = AndroidUtilities.dp(cornerRadiusDp).toFloat()
            setColor(Color.parseColor("#F6F6F6"))      // iOS light: 246/246/246
            setStroke(AndroidUtilities.dp(1f), Color.parseColor("#D9D9D9")) // iOS light: white 0.85
        }
        return drawable
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ThumbViewHolder -> {
                val items = model.items()
                if (position < 0 || position >= items.size) return
                val item = items[position]
                GlideUtils.getInstance().showImg(context, item.localPath, holder.thumb)
                holder.removeBtn.setOnClickListener {
                    if (richTextTraySendingGate()) return@setOnClickListener
                    onRemove(item.id)
                }
                holder.itemView.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos in 0 until model.size()) onPreview(pos)
                }
            }
            is AddViewHolder -> {
                holder.itemView.setOnClickListener {
                    if (richTextTraySendingGate()) return@setOnClickListener
                    onAddTapped()
                }
            }
        }
    }

    /**
     * 适配器层不持有 in-flight 标志，由调用方注入的 onRemove / onAddTapped 内部各自再做一次门控
     * （ChatPanelManager 已有 richTextTraySending 字段）。这里只是个 hook 兜底用。
     */
    private fun richTextTraySendingGate(): Boolean = false

    /**
     * ItemTouchHelper 的逐格拖拽回调：把 UI 的 from→to 交换如实落到模型，保证发送时的真实当前
     * 顺序与用户所见一致。+ cell 位置不可作为目标（外部应过滤）。
     *
     * @return true 表示交换成功（模型已变）
     */
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!isReorderable(fromPosition) || !isReorderable(toPosition)) return false
        val moved = model.move(fromPosition, toPosition)
        if (moved) {
            notifyItemMoved(fromPosition, toPosition)
            onReorder()
        }
        return moved
    }

    class ThumbViewHolder(
        itemView: View,
        val thumb: AppCompatImageView,
        val removeBtn: AppCompatImageView,
    ) : RecyclerView.ViewHolder(itemView)

    class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
