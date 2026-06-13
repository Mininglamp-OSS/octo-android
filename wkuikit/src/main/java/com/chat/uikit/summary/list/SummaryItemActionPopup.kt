/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.list

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.summary.model.SummaryListItem
import com.chat.base.summary.model.TaskStatus
import com.chat.uikit.R

/**
 * 浮层式状态菜单, 1:1 对齐 iOS [WKFloatingMenu] 几何与视觉:
 *   - 行高 44dp, 字号 15sp, 左 padding 16dp, 左对齐
 *   - 行间分隔线 0.5dp 灰 alpha 15%, 左右 inset 16dp
 *   - 圆角 12dp + 阴影 (offset 0,4 / radius 12 / opacity .15)
 *   - 弹出位置: anchor 上方 10dp; 上方剩余 < 60dp 才落到下方
 *   - 横向: 以 anchor 中心为锚居中, 屏幕右 10dp 处夹紧 (三点在卡右 → 自然贴屏右)
 *
 * 内容根据 item.status 动态生成 (与 iOS [OctoSummaryCardCell menuItemsForItem:] 一致):
 *   生成中 / 等待 / 等待参与 → 取消任务 + 删除
 *   已完成 → 重新生成 + 删除
 *   已取消 → 重新生成 + 删除
 *   失败 → 重试 + 删除
 *
 * 删除是危险操作 (红字), 由 destructive 标记控制。
 */
class SummaryItemActionPopup private constructor(
    private val context: Context,
    private val status: TaskStatus,
    private val onCancel: () -> Unit,
    private val onRegenerate: () -> Unit,
    private val onRetry: () -> Unit,
    private val onDelete: () -> Unit,
) {

    private val popup: PopupWindow
    private val container: LinearLayout
    private val popupWidthDp = 140f
    private val rowHeightDp = 44f

    init {
        val rows = mutableListOf<TextView>()
        when (status) {
            TaskStatus.Processing, TaskStatus.Pending, TaskStatus.WaitingConfirm ->
                rows += makeRow(R.string.summary_action_cancel_task, false) { onCancel() }
            TaskStatus.Completed ->
                rows += makeRow(R.string.summary_action_regenerate, false) { onRegenerate() }
            TaskStatus.Cancelled ->
                rows += makeRow(R.string.summary_action_regenerate, false) { onRegenerate() }
            TaskStatus.Failed ->
                rows += makeRow(R.string.summary_action_retry, false) { onRetry() }
        }
        rows += makeRow(R.string.summary_common_delete, true) { onDelete() }

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_summary_action_popup)
            elevation = dp(8f)
            // 行 + 行间分隔线 (除最后一行)
            rows.forEachIndexed { idx, row ->
                addView(row)
                if (idx < rows.size - 1) addView(makeSeparator())
            }
            layoutParams = ViewGroup.LayoutParams(
                dp(popupWidthDp).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // PopupWindow 必须显式给宽 — WRAP_CONTENT 会 ignore container 的 layoutParams
        popup = PopupWindow(
            container,
            dp(popupWidthDp).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            isFocusable = true
            elevation = dp(8f)
        }
    }

    private fun makeRow(textRes: Int, destructive: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            setText(textRes)
            textSize = 15f // iOS appFontOfSize:15
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(16f).toInt(), 0, dp(16f).toInt(), 0)
            setTextColor(
                if (destructive) ContextCompat.getColor(context, R.color.summary_red)
                else ContextCompat.getColor(context, R.color.summary_text_primary),
            )
            background = ContextCompat.getDrawable(
                context, android.R.drawable.list_selector_background,
            )
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(rowHeightDp).toInt(),
            )
            setOnClickListener {
                popup.dismiss()
                onClick()
            }
        }

    /** iOS sep: (16, ..., menuWidth-32, 0.5pt) gray alpha 0.15 */
    private fun makeSeparator(): View = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.summary_stroke_8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (1f).coerceAtLeast(dp(0.5f)).toInt(), // 至少 1px
        ).apply {
            leftMargin = dp(16f).toInt()
            rightMargin = dp(16f).toInt()
        }
    }

    fun show(anchor: View) {
        // 1:1 iOS WKFloatingMenu 几何:
        //   point = anchor 中心 (window/screen 坐标)
        //   showAbove = (point.y - menuHeight - 12) > 60
        //   menuY = above ? point.y - menuHeight - 10 : point.y + 10
        //   menuX = clamp(point.x - menuWidth/2, [10, screenWidth-10-menuWidth])
        val pos = IntArray(2)
        anchor.getLocationOnScreen(pos)
        val anchorCenterX = pos[0] + anchor.width / 2
        val anchorCenterY = pos[1] + anchor.height / 2
        val screenWidth = context.resources.displayMetrics.widthPixels
        val popupWidthPx = dp(popupWidthDp).toInt()
        val edgeInset = dp(10f).toInt()

        // 真实测量 popup 高 (按行数动态)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val popupHeightPx = container.measuredHeight

        val showAbove = (anchorCenterY - popupHeightPx - dp(12f).toInt()) > dp(60f).toInt()
        val y = if (showAbove) anchorCenterY - popupHeightPx - dp(10f).toInt()
                else anchorCenterY + dp(10f).toInt()

        var x = anchorCenterX - popupWidthPx / 2
        if (x < edgeInset) x = edgeInset
        if (x + popupWidthPx > screenWidth - edgeInset) x = screenWidth - popupWidthPx - edgeInset

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun dp(v: Float): Float = v * context.resources.displayMetrics.density

    companion object {
        fun show(
            anchor: View,
            item: SummaryListItem,
            onCancel: () -> Unit,
            onRegenerate: () -> Unit,
            onRetry: () -> Unit,
            onDelete: () -> Unit,
        ) {
            showWithStatus(anchor, item.status, onCancel, onRegenerate, onRetry, onDelete)
        }

        /** 详情页 / 列表 cell 共用入口: 直接传 status, 不强依赖 SummaryListItem. */
        fun showWithStatus(
            anchor: View,
            status: TaskStatus,
            onCancel: () -> Unit,
            onRegenerate: () -> Unit,
            onRetry: () -> Unit,
            onDelete: () -> Unit,
        ) {
            SummaryItemActionPopup(
                context = anchor.context,
                status = status,
                onCancel = onCancel,
                onRegenerate = onRegenerate,
                onRetry = onRetry,
                onDelete = onDelete,
            ).show(anchor)
        }
    }
}
