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

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.SummaryListItem
import com.chat.base.summary.model.TaskStatus
import com.chat.uikit.R
import com.chat.uikit.summary.time.RelativeTime

/**
 * 把 [SummaryListItem] 映射到 item_summary_card 的字段集. 1:1 对齐 iOS [OctoSummaryCardCell]:
 *   - bind: initiator + 标题(可关键词高亮) + body (preview / processing 文案 / 隐藏) + footer + 状态徽章
 *   - 三态视觉: completed/failed/cancelled = 普通; pending/processing = 紫底 + spinner; waitingConfirm = 状态徽章橙
 *   - 关键词高亮: 命中段紫色 semibold; 整段超长 + 命中靠后 → 前置 "…<前 6 字>" 窗口
 */
internal object SummaryCardBinder {

    fun bind(view: View, item: SummaryListItem, currentUserName: String?, keyword: String?) {
        val ctx = view.context
        val cardRoot: FrameLayout = view.findViewById(R.id.cardRoot)
        val initiatorTv: TextView = view.findViewById(R.id.initiatorTv)
        val statusBadge: FrameLayout = view.findViewById(R.id.statusBadge)
        val statusBadgeTv: TextView = view.findViewById(R.id.statusBadgeTv)
        val spinner: ProgressBar = view.findViewById(R.id.processingSpinner)
        val titleTv: TextView = view.findViewById(R.id.titleTv)
        val bodyTv: TextView = view.findViewById(R.id.bodyTv)
        val footerTimeTv: TextView = view.findViewById(R.id.footerTimeTv)
        val moreBtn: ImageView = view.findViewById(R.id.moreBtn)

        val isProcessing = item.status == TaskStatus.Processing || item.status == TaskStatus.Pending
        val isCompleted = item.status == TaskStatus.Completed
        val isWaiting = item.status == TaskStatus.WaitingConfirm

        // === card background ===
        cardRoot.setBackgroundResource(
            if (isProcessing) R.drawable.bg_summary_card_processing
            else R.drawable.bg_summary_card_normal
        )

        // === initiator + status badge / spinner ===
        initiatorTv.text = initiatorTextFor(ctx, item, currentUserName)

        when {
            isProcessing -> {
                statusBadge.visibility = View.GONE
                spinner.visibility = View.VISIBLE
            }
            isWaiting -> {
                applyStatusBadge(
                    badge = statusBadge,
                    label = statusBadgeTv,
                    text = ctx.getString(R.string.summary_card_status_waiting),
                    fg = ContextCompat.getColor(ctx, R.color.summary_orange),
                    bg = ContextCompat.getColor(ctx, R.color.summary_orange_10),
                )
                spinner.visibility = View.GONE
            }
            item.status == TaskStatus.Failed -> {
                applyStatusBadge(
                    badge = statusBadge,
                    label = statusBadgeTv,
                    text = ctx.getString(R.string.summary_card_status_failed),
                    fg = ContextCompat.getColor(ctx, R.color.summary_red),
                    bg = ContextCompat.getColor(ctx, R.color.summary_red_10),
                )
                spinner.visibility = View.GONE
            }
            item.status == TaskStatus.Cancelled -> {
                applyStatusBadge(
                    badge = statusBadge,
                    label = statusBadgeTv,
                    text = ctx.getString(R.string.summary_card_status_cancelled),
                    fg = ContextCompat.getColor(ctx, R.color.summary_text_muted),
                    bg = ContextCompat.getColor(ctx, R.color.summary_chip_border),
                )
                spinner.visibility = View.GONE
            }
            else -> {
                statusBadge.visibility = View.GONE
                spinner.visibility = View.GONE
            }
        }

        // === title (可能带关键词高亮 + 窗口化前置) ===
        applyTitle(titleTv, item, keyword)

        // === body ===
        when {
            isCompleted && !item.summaryPreview.isNullOrEmpty() -> {
                bodyTv.visibility = View.VISIBLE
                bodyTv.maxLines = 2
                bodyTv.setTextColor(ContextCompat.getColor(ctx, R.color.summary_text_muted))
                bodyTv.text = item.summaryPreview
            }
            isProcessing -> {
                bodyTv.visibility = View.VISIBLE
                bodyTv.maxLines = 1
                bodyTv.setTextColor(ContextCompat.getColor(ctx, R.color.summary_purple))
                bodyTv.text = ctx.getString(R.string.summary_card_processing)
            }
            else -> {
                bodyTv.visibility = View.GONE
                bodyTv.text = null
            }
        }

        // === footer (time) ===
        if (isProcessing) {
            footerTimeTv.visibility = View.INVISIBLE
            footerTimeTv.text = null
        } else {
            footerTimeTv.visibility = View.VISIBLE
            val isoTime = if (isCompleted) item.completedAt ?: item.createdAt else item.createdAt
            footerTimeTv.text = RelativeTime.relativeFromISO(ctx, isoTime)
        }

        moreBtn.tag = item
    }

    /**
     * iOS 'initiatorTextForItem': "你发起 / Name 发起" + 可选 "· 来自 X" 或
     * "· 来自 X 等 N 个 群聊/私聊/聊天".
     */
    private fun initiatorTextFor(ctx: android.content.Context, item: SummaryListItem, currentUserName: String?): String {
        val prefix = when {
            item.creatorName.isNullOrEmpty() -> ctx.getString(R.string.summary_card_creator_self)
            !currentUserName.isNullOrEmpty() && item.creatorName == currentUserName ->
                ctx.getString(R.string.summary_card_creator_self)
            else -> ctx.getString(R.string.summary_card_creator_other, item.creatorName)
        }
        val suffix = sourceSuffix(ctx, item.sources)
        return if (suffix.isEmpty()) prefix else "$prefix · $suffix"
    }

    private fun sourceSuffix(ctx: android.content.Context, sources: List<SourceItem>): String {
        if (sources.isEmpty()) return ""
        val first = sources.first()
        val firstName = first.sourceName?.takeIf { it.isNotEmpty() } ?: first.sourceId
        if (sources.size == 1) return ctx.getString(R.string.summary_card_source_one, firstName)
        val allDM = sources.all { it.sourceType == SourceType.DirectMessage }
        val allGroup = sources.all { it.sourceType != SourceType.DirectMessage }
        val typeWord = when {
            allGroup -> ctx.getString(R.string.summary_card_source_word_group)
            allDM -> ctx.getString(R.string.summary_card_source_word_dm)
            else -> ctx.getString(R.string.summary_card_source_word_chat)
        }
        return ctx.getString(R.string.summary_card_source_more, firstName, sources.size, typeWord)
    }

    private fun applyStatusBadge(
        badge: FrameLayout,
        label: TextView,
        text: String,
        fg: Int,
        bg: Int,
    ) {
        badge.visibility = View.VISIBLE
        val bgDrawable = badge.background?.mutate() as? GradientDrawable
        bgDrawable?.setColor(bg)
        label.text = text
        label.setTextColor(fg)
    }

    /**
     * 1:1 对齐 iOS applyHighlightedTitleForWidth:
     *   - 没命中或没关键词: 普通 text
     *   - 命中: 命中段紫色 semibold; 命中位置 > 6 字符 + 整段比可见宽更长 → 前置 "…<前 6 字>"
     *
     * Android 这里不预测视图宽度 (RecyclerView 复用阶段 layout 不一定就绪),
     * 直接按字符长度近似: 标题超过 22 字符且命中靠后, 触发窗口化。22 字符 ≈ 可见单行宽度。
     */
    private fun applyTitle(titleTv: TextView, item: SummaryListItem, keyword: String?) {
        val raw = item.title.takeIf { it.isNotEmpty() }
            ?: titleTv.context.getString(R.string.summary_common_untitled)
        val kw = keyword?.takeIf { it.isNotEmpty() }
        if (kw == null) {
            titleTv.text = raw
            return
        }
        val matchStart = raw.indexOf(kw, ignoreCase = true)
        if (matchStart < 0) {
            titleTv.text = raw
            return
        }

        val ctxBefore = 6
        val maxNoTrunc = 22
        val (display, displayMatchStart) = if (raw.length > maxNoTrunc && matchStart > ctxBefore) {
            val start = matchStart - ctxBefore
            val newText = "…" + raw.substring(start)
            newText to (1 + ctxBefore)
        } else {
            raw to matchStart
        }

        val span = SpannableString(display)
        val end = (displayMatchStart + kw.length).coerceAtMost(display.length)
        if (displayMatchStart in 0 until end) {
            val purple = ContextCompat.getColor(titleTv.context, R.color.summary_purple)
            span.setSpan(ForegroundColorSpan(purple), displayMatchStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(StyleSpan(Typeface.BOLD), displayMatchStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        titleTv.text = span
    }
}
