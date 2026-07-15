/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.ui.components.FilterImageView
import com.chat.uikit.R

/**
 * "我的贴图" 表情面板 grid adapter，1:1 复刻 iOS `WKMyStickerContentView` 交互，
 * 尤其是编辑态下"长按 hold-still 出预览 / 长按+移动 触发 drag"这条 iOS
 * `UIContextMenuInteraction` 同时识别路径。
 *
 * <h3>viewType 布局</h3>
 * - position 0 → 首格 "+" 按钮，永远存在
 * - position 1..N → 用户收藏的 [WKSticker]
 *
 * <h3>非编辑态</h3>
 * - tap sticker → [Callbacks.onStickerClick] 发送
 * - long-press sticker → [Callbacks.onEnterEditMode]（外层触发 haptic + setEditMode(true)）
 * - tap "+" → [Callbacks.onAddClick] 打开选图上传
 *
 * <h3>编辑态（cells 抖动 + × 徽章显示）</h3>
 * - tap sticker → 退出编辑态（对齐 iOS "tap in edit exits"）
 * - tap × 徽章 → [Callbacks.onDeleteSticker] 二次确认删除
 * - long-press hold-still ≥500ms → [Callbacks.onPreviewSticker] 弹预览
 * - long-press 立即移动（超 touchSlop）→ [Callbacks.startDrag] 触发拖拽
 * - 空白 / 切 tab / "+" → 由外层 PanelManager 处理退出
 *
 * 编辑态下不用 `setOnLongClickListener`，改用 [View.OnTouchListener] 自己判定：
 * 系统 long-press timer (~500ms) 和我们的预览 timer 一起就会打架，把预览和 drag
 * 两个动作都塞进同一个 500ms 触发点里根本区分不开；OnTouchListener 里根据是否
 * 移动过 slop 分流才是干净的方案。同时 [ItemTouchHelper.isLongPressDragEnabled]
 * 必须置为 false，drag 由我们手动 [Callbacks.startDrag] 触发。
 */
class CollectStickerAdapter(
    private val callbacks: Callbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onAddClick()
        fun onStickerClick(sticker: WKSticker, position: Int)
        fun onEnterEditMode()
        fun onPreviewSticker(sticker: WKSticker, position: Int)
        fun onDeleteSticker(sticker: WKSticker, position: Int)
        fun startDrag(viewHolder: RecyclerView.ViewHolder)
    }

    private val stickers: MutableList<WKSticker> = mutableListOf()
    private var editMode: Boolean = false

    fun submitList(list: List<WKSticker>) {
        stickers.clear()
        stickers.addAll(list)
        notifyDataSetChanged()
    }

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        notifyDataSetChanged()
    }

    fun isEditMode(): Boolean = editMode

    /** 拖拽重排：交换 adapter 位置 [from] 和 [to]（均为 adapter position，含首格 +）。 */
    fun moveItem(from: Int, to: Int): Boolean {
        if (from == 0 || to == 0) return false // "+" 首格不参与
        val fromStickerIdx = from - 1
        val toStickerIdx = to - 1
        if (fromStickerIdx !in stickers.indices || toStickerIdx !in stickers.indices) return false
        val moved = stickers.removeAt(fromStickerIdx)
        stickers.add(toStickerIdx, moved)
        notifyItemMoved(from, to)
        return true
    }

    /** 拖拽结束后取当前顺序（sticker_id 列表，不含首格 +）用于 [WKStickerManager.reorder]。 */
    fun currentOrderIds(): List<String> = stickers.map { it.sticker_id }

    fun getStickerAt(position: Int): WKSticker? {
        if (position <= 0 || position > stickers.size) return null
        return stickers[position - 1]
    }

    override fun getItemCount(): Int = stickers.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_ADD else VIEW_STICKER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_ADD) {
            AddVH(inflater.inflate(R.layout.item_collect_sticker_add, parent, false))
        } else {
            StickerVH(inflater.inflate(R.layout.item_collect_sticker, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AddVH -> {
                holder.itemView.setOnClickListener { callbacks.onAddClick() }
            }
            is StickerVH -> {
                val sticker = stickers[position - 1]
                bindStickerImage(holder.imageView, sticker)
                holder.deleteBadge.visibility = if (editMode) View.VISIBLE else View.GONE
                if (editMode) holder.startShake() else holder.stopShake()
                bindStickerListeners(holder, sticker)
            }
        }
    }

    private fun bindStickerListeners(holder: StickerVH, sticker: WKSticker) {
        if (editMode) {
            // 编辑态：tap = 退出编辑；长按 hold-still = 预览；长按 + 移动 = drag
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.setOnClickListener {
                setEditMode(false)
            }
            holder.itemView.setOnTouchListener(
                EditModeTouchListener(holder, sticker, callbacks)
            )
        } else {
            // 非编辑态：tap = 发送；长按 = 进编辑态
            holder.itemView.setOnTouchListener(null)
            holder.itemView.setOnClickListener {
                callbacks.onStickerClick(sticker, holder.bindingAdapterPosition)
            }
            holder.itemView.setOnLongClickListener {
                callbacks.onEnterEditMode()
                true
            }
        }
        holder.deleteBadge.setOnClickListener {
            callbacks.onDeleteSticker(sticker, holder.bindingAdapterPosition)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is StickerVH) {
            holder.stopShake()
            holder.itemView.setOnTouchListener(null)
        }
        super.onViewRecycled(holder)
    }

    private fun bindStickerImage(imageView: FilterImageView, item: WKSticker) {
        val url = item.path
        val ctx = imageView.context
        when {
            url.isNullOrEmpty() ->
                imageView.setImageResource(com.chat.base.R.drawable.default_view_bg)
            StickerUrlUtils.isStaticImage(url) ->
                GlideUtils.getInstance().showImg(ctx, WKApiConfig.getShowUrl(url), imageView)
            StickerUrlUtils.isLottieFormat(url, item.format) ->
                imageView.setImageResource(com.chat.base.R.drawable.default_view_bg)
            else ->
                GlideUtils.getInstance().showImg(ctx, WKApiConfig.getShowUrl(url), imageView)
        }
    }

    /**
     * 编辑态下 sticker cell 的 touch dispatcher：模拟 iOS UIContextMenuInteraction
     * 的"按住 hold-still → context menu / 按住立即 move → drag"分流。
     *
     * 流程：
     * - ACTION_DOWN：记录起点，post 500ms 后的 preview Runnable
     * - ACTION_MOVE：位移超 touchSlop → 取消 preview，改为 drag，标记 [dragStarted]
     * - ACTION_UP / CANCEL：撤 preview 定时器；若已 fire 过 preview / drag → 消费 UP
     *   阻断后续 click（不然预览完抬手会走到 [setOnClickListener] 触发 exit edit）
     *
     * 返回值：DOWN 一律 false 让 click 通道保留（真 tap 场景下 UP 时才决定是否消费）。
     */
    private class EditModeTouchListener(
        private val holder: RecyclerView.ViewHolder,
        private val sticker: WKSticker,
        private val callbacks: Callbacks,
    ) : View.OnTouchListener {

        private val handler = Handler(Looper.getMainLooper())
        private val previewDelayMs = 500L
        private var downX = 0f
        private var downY = 0f
        private var slop = 0
        private var previewFired = false
        private var dragStarted = false
        private var previewRunnable: Runnable? = null

        @Suppress("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    previewFired = false
                    dragStarted = false
                    slop = ViewConfiguration.get(v.context).scaledTouchSlop
                    val r = Runnable {
                        if (!dragStarted) {
                            previewFired = true
                            callbacks.onPreviewSticker(sticker, holder.bindingAdapterPosition)
                        }
                    }
                    previewRunnable = r
                    handler.postDelayed(r, previewDelayMs)
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragStarted || previewFired) return true
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (dx * dx + dy * dy > slop * slop) {
                        // 用户移动 → 取消 preview 计时器，让 ItemTouchHelper 接管 drag
                        previewRunnable?.let { handler.removeCallbacks(it) }
                        dragStarted = true
                        callbacks.startDrag(holder)
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    previewRunnable?.let { handler.removeCallbacks(it) }
                    previewRunnable = null
                    val consumed = previewFired || dragStarted
                    // 重置 previewFired 前先读取；下次 DOWN 会重新清零
                    return consumed
                }
            }
            return false
        }
    }

    private class AddVH(view: View) : RecyclerView.ViewHolder(view) {
        val root: FrameLayout = view.findViewById(R.id.stickerAddRoot)
    }

    private class StickerVH(view: View) : RecyclerView.ViewHolder(view) {
        val root: FrameLayout = view.findViewById(R.id.stickerRoot)
        val imageView: FilterImageView = view.findViewById(R.id.stickerImageView)
        val deleteBadge: ImageView = view.findViewById(R.id.stickerDeleteBadge)

        private var shakeAnimator: ObjectAnimator? = null

        fun startShake() {
            if (shakeAnimator?.isRunning == true) return
            val animator = ObjectAnimator.ofFloat(root, View.ROTATION, -2.2f, 2.2f).apply {
                duration = 160L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                // 各 cell 用 startDelay 打散，避免整齐同步（对齐 iOS
                // WKStickerGIFCell 的 arc4random_uniform(160)/1000 相位偏移）
                startDelay = ((bindingAdapterPosition * 37L) + 13L) % 160L
            }
            animator.start()
            shakeAnimator = animator
        }

        fun stopShake() {
            shakeAnimator?.cancel()
            shakeAnimator = null
            root.rotation = 0f
        }
    }

    companion object {
        private const val VIEW_ADD = 0
        private const val VIEW_STICKER = 1
    }
}
