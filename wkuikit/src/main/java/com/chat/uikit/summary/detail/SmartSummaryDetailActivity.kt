/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.detail

import android.content.Intent
import android.graphics.Color
import android.text.Spanned
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chat.base.base.WKBaseActivity
import com.chat.base.markdown.WKMarkwonProvider
import com.chat.base.markdown.WKTableData
import com.chat.base.markdown.WKTablePlugin
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.chat.base.utils.WKDialogUtils
import com.chat.uikit.R
import com.chat.uikit.summary.SummaryHud
import com.chat.uikit.databinding.ActSmartSummaryDetailBinding
import com.chat.uikit.summary.list.SummaryItemActionPopup
import com.chat.uikit.summary.markdown.CitationPostProcessor
import com.chat.uikit.summary.time.RelativeTime
import kotlinx.coroutines.launch

/**
 * 智能总结详情页. 1:1 对齐 iOS [OctoSummaryDetailVC]:
 *   - title 固定 "智能总结", 右侧 ⋯ 按状态切动作菜单
 *   - body: 标题 + 来源 chip + 创建时间 + 状态卡 (processing / waiting / 内容)
 *   - 底部悬浮 footer (转发到聊天 / 编辑) — 仅 completed 显示
 *   - processing/pending 状态下 8s 轮询直至状态变化
 *
 * citation 点击: 暂打 toast 等 PR5 的关联聊天 sheet 接入
 * 转发到聊天: 暂打 toast (依赖 WKForwardSelectVC, 后续接入)
 * waiting "查看确认状态": 暂打 toast 等 PR6 的 ConfirmVC
 */
class SmartSummaryDetailActivity : WKBaseActivity<ActSmartSummaryDetailBinding>() {

    private val viewModel: SmartSummaryDetailViewModel by viewModels { factory() }

    /** content 容器中本次 render 之外的动态 view (表格卡 + 后续段 TextView) 用此 tag 标记, 重复 render 前清理. */
    private val dynamicTag = "summary_md_dynamic"

    /**
     * 转发到聊天: 启动 ChooseChatActivity 走 setResult 路径, 对选中的每个 channel 发一条
     * WKTextContent (markdown 原文). 与项目其它转发入口同套机制, 但不弹"转发到 X 个聊天?"
     * 二次确认对话框 (走 isChoose=false 路径直接 setResult).
     */
    private val forwardLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        @Suppress("DEPRECATION")
        val channels = data.getParcelableArrayListExtra<com.xinbida.wukongim.entity.WKChannel>("list")
            ?.toList().orEmpty()
        forwardToChannels(channels)
    }

    override fun getViewBinding(): ActSmartSummaryDetailBinding =
        ActSmartSummaryDetailBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.summary_detail_nav_title)
    }

    override fun getRightIvResourceId(imageView: ImageView?): Int = R.drawable.ic_more_vert

    override fun rightLayoutClick() {
        val detail = viewModel.state.value.detail ?: return
        val anchor = findViewById<View>(R.id.titleRightLayout) ?: return
        SummaryItemActionPopup.showWithStatus(
            anchor = anchor,
            status = detail.status,
            onCancel = { viewModel.performCancel() },
            onRegenerate = { viewModel.performRegenerate() },
            onRetry = { viewModel.performRegenerate() },
            onDelete = { confirmDelete() },
        )
    }

    override fun initView() {
        // markdown 渲染走 WKMarkwonProvider (项目共享实例): 表格不在文本中绘制,
        // 而是抽到 List<WKTableData>, 由 renderMarkdownContent() 拼成 LayoutInflater
        // 构建的原生 TableLayout 卡片, 与 ChatActivity 消息气泡同链路同视觉。
        // 之前用 Markwon 自带 TablePlugin 在窄视图里把 cell 内容压缩成乱码,
        // 复用项目方案后表格列宽被外层 HorizontalScrollView 撑开, 单元格再宽也能横向滑动看完。
        wkVBinding.sourcesView.onToggle = { /* 自动 requestLayout, 不需要额外动作 */ }

        // 1:1 对齐 ContextFragment: SmartRefreshLayout overScroll bounce 提供 iOS alwaysBounceVertical 同款手感
        wkVBinding.bounceLayout.setEnableRefresh(false)
        wkVBinding.bounceLayout.setEnableLoadMore(false)
        wkVBinding.bounceLayout.setEnableOverScrollDrag(true)
        wkVBinding.bounceLayout.setEnableOverScrollBounce(true)

        wkVBinding.waitingActionBtn.setOnClickListener {
            val detail = viewModel.state.value.detail ?: return@setOnClickListener
            startActivity(
                com.chat.uikit.summary.confirm.SmartSummaryConfirmActivity
                    .newIntent(this, detail.taskId),
            )
        }
        wkVBinding.footerForwardBtn.setOnClickListener {
            val content = viewModel.state.value.detail?.result?.content.orEmpty()
            if (content.isEmpty()) {
                SummaryHud.show(this, R.string.summary_forward_no_content)
                return@setOnClickListener
            }
            forwardLauncher.launch(android.content.Intent(this, com.chat.uikit.chat.ChooseChatActivity::class.java))
        }
        wkVBinding.footerEditBtn.setOnClickListener {
            val detail = viewModel.state.value.detail ?: return@setOnClickListener
            startActivity(
                SmartSummaryEditActivity.newIntent(
                    this,
                    detail.taskId,
                    detail.resultId ?: 0L,
                    detail.result?.content.orEmpty(),
                ),
            )
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        // 编辑页保存后回来 / 列表页跳进来后再次返回, 都重新拉一次保证最新内容
        viewModel.loadDetail()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: DetailUiState) {
        state.effect?.let { effect ->
            when (effect) {
                is DetailEffect.Toast ->
                    SummaryHud.show(this, toastResIdOf(effect.kind))
                DetailEffect.Close -> finish()
            }
            viewModel.consumeEffect()
        }

        val detail = state.detail ?: return
        renderDetail(detail)
    }

    private fun renderDetail(d: SummaryDetail) {
        wkVBinding.titleTv.text = d.title
        wkVBinding.sourcesView.items = d.sources

        val createdAt = d.createdAt?.let(RelativeTime::localFromISO).orEmpty()
        if (createdAt.isNotEmpty()) {
            wkVBinding.createdAtTv.text = getString(R.string.summary_detail_created_at, createdAt)
            wkVBinding.createdAtTv.isVisible = true
        } else {
            wkVBinding.createdAtTv.isVisible = false
        }

        val processing = d.status == TaskStatus.Processing || d.status == TaskStatus.Pending
        val waiting = d.status == TaskStatus.WaitingConfirm
        val completed = d.status == TaskStatus.Completed
        val failed = d.status == TaskStatus.Failed
        val cancelled = d.status == TaskStatus.Cancelled

        wkVBinding.processingCard.isVisible = processing
        wkVBinding.waitingCard.isVisible = waiting

        val hasContent = completed || failed || cancelled
        wkVBinding.contentCard.isVisible = hasContent
        if (hasContent) renderContent(d, completed, failed)

        wkVBinding.bottomBar.isVisible = completed
    }

    private fun renderContent(d: SummaryDetail, completed: Boolean, failed: Boolean) {
        val tv = wkVBinding.contentTv
        // 防止 status 切换 (Processing → Completed) 时上次 setTextColor / setTextSize 残留:
        // 进入每个分支前先 reset 到 layout 默认 14sp + 主色, 让本次 setText 的 spans 基线干净。
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.setTextColor(getColor(R.color.summary_text_strong))
        // 任何一次 render 都先清掉上次 addView 进来的表格卡 + 文本段 (用 dynamicTag 标记),
        // 否则 onResume 反复 loadDetail 会让卡片不断累加。
        clearDynamicViews()
        tv.visibility = View.VISIBLE
        when {
            completed -> {
                val raw = d.result?.content.orEmpty()
                if (raw.isEmpty()) {
                    tv.text = ""
                    return
                }
                renderMarkdownContent(raw)
            }
            failed -> {
                val msg = d.errorMessage?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.summary_detail_unknown_error)
                tv.text = getString(R.string.summary_detail_generation_failed) + msg
                tv.setTextColor(getColor(R.color.summary_red))
            }
            else -> {
                // cancelled
                tv.text = getString(R.string.summary_detail_cancelled_text)
                tv.setTextColor(getColor(R.color.summary_text_50))
            }
        }
    }

    /**
     * 复用 [WKMarkwonProvider.toMarkdownWithTables] (与消息气泡同链路): 表格被替换为 ￼
     * 占位符并抽出到 [WKTableData] 列表。我们再按 placeholder 切段, 文本段走 contentTv /
     * 追加 TextView, 表格段 inflate [com.chat.base.R.layout.layout_markdown_table_card] 渲染。
     *
     * citation 处理保持原样: 在最终拼好的整段 Spannable 上跑 [CitationPostProcessor]
     * 给 [N] 加可点击 link, 然后再分段。表格段没有 citation 风险所以不需要 patch。
     */
    private fun renderMarkdownContent(raw: String) {
        val (rendered: Spanned, tables) = WKMarkwonProvider.toMarkdownWithTables(this, raw)
        val withCitations = CitationPostProcessor.process(this, rendered) { indices ->
            onCitationTap(indices)
        }
        // 对齐 iOS OctoSummaryMarkdownRender: heading 字号 (H1 19 / H2 17 / H3 16 sp 粗体) +
        // inline code 紫色胶囊。WKMarkwonProvider 共享单例不动, 后处理走 spannable patch。
        val patched = applyTypography(withCitations)

        val tv = wkVBinding.contentTv
        val container = wkVBinding.contentCard

        if (tables.isEmpty()) {
            tv.text = patched
            tv.movementMethod = LinkMovementMethod.getInstance()
            return
        }

        // 找出 ￼ 位置, 与 tables 一一对应。占位符数量与表格数量不匹配时回退到
        // "全文 + 表格追加在末尾" 兜底, 避免部分场景 plugin 状态错位让用户看不到内容。
        val full = patched.toString()
        val positions = mutableListOf<Int>()
        var idx = 0
        while (idx < full.length) {
            val p = full.indexOf(WKTablePlugin.TABLE_PLACEHOLDER, idx)
            if (p < 0) break
            positions.add(p)
            idx = p + 1
        }

        if (positions.size != tables.size) {
            tv.text = patched
            tv.movementMethod = LinkMovementMethod.getInstance()
            for (t in tables) {
                container.addView(buildTableCard(container, t), wideLp())
            }
            return
        }

        // 按 placeholder 切段
        val segments = mutableListOf<CharSequence>()
        var start = 0
        for (p in positions) {
            segments.add(patched.subSequence(start, p))
            start = p + WKTablePlugin.TABLE_PLACEHOLDER.length
        }
        segments.add(patched.subSequence(start, patched.length))

        // 第一段塞 contentTv
        val firstTrim = trimEdgeNewlines(segments[0])
        if (firstTrim.isBlank()) {
            tv.visibility = View.GONE
        } else {
            tv.text = firstTrim
            tv.movementMethod = LinkMovementMethod.getInstance()
        }

        // 交叉添加: 表格 + 后续文本
        for (i in tables.indices) {
            container.addView(buildTableCard(container, tables[i]), wideLp())
            val next = segments.getOrNull(i + 1) ?: continue
            val nextTrim = trimEdgeNewlines(next)
            if (nextTrim.isBlank()) continue
            val extra = TextView(this).apply {
                text = nextTrim
                setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.textSize)
                setTextColor(tv.currentTextColor)
                setLineSpacing(3f * resources.displayMetrics.density, 1f)
                movementMethod = LinkMovementMethod.getInstance()
                tag = dynamicTag
            }
            container.addView(
                extra,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun clearDynamicViews() {
        val container = wkVBinding.contentCard
        // 倒着删, 跳过 contentTv 本身; 仅清打了 dynamicTag 的 view, 不动 layout 里其他静态 child。
        for (i in container.childCount - 1 downTo 0) {
            val c = container.getChildAt(i)
            if (c.tag == dynamicTag) container.removeViewAt(i)
        }
    }

    /** 去掉 CharSequence 首尾换行,保留中间内容与 spans. 复用 WKTextProvider 同款实现. */
    private fun trimEdgeNewlines(cs: CharSequence): CharSequence {
        var s = 0
        var e = cs.length
        while (s < e && cs[s] == '\n') s++
        while (e > s && cs[e - 1] == '\n') e--
        return if (s == 0 && e == cs.length) cs else cs.subSequence(s, e)
    }

    /**
     * 排版后处理: 把 Markwon 渲染出的 spanned 调整成 iOS [OctoSummaryMarkdownRender] 同款视觉。
     * WKMarkwonProvider 是项目共享单例 (消息气泡同款实例), 不能为了详情页改它的 plugin 链;
     * 全部走 spannable 后处理。
     *
     * heading → 在原 HeadingSpan 之上叠加 AbsoluteSizeSpan + Bold StyleSpan,
     *   字号 H1 19 / H2 17 / H3 16 sp, 替换掉 Markwon 默认 multiplier (2.0/1.5/1.17)
     *   带来的 H1≈28sp 视觉过大问题。
     *
     *   关键: 不 removeSpan(HeadingSpan)。HeadingSpan 同时是 MetricAffectingSpan
     *   (字号) + LeadingMarginSpan (在 drawLeadingMargin 里给 H1/H2 画下方分隔横线 ——
     *   "dmwork-ios 已审结 PR 汇总" 上方的那条线就来自上一个 H2 的 break);
     *   移除会丢失横线。后加的 AbsoluteSizeSpan 在 TextPaint 链里会覆盖 HeadingSpan
     *   设的字号倍数 (setTextSize 是赋值式而非乘式)。
     *
     *   HeadingSpan.level 是 package-private final int (markwon 4.6.2 稳定), 用反射读出。
     *
     * 注意: 紫色专属于 citation [N] 角标 (CitationPostProcessor 已加), 不要再扩散到 inline code,
     * 否则 backtick 标注的代码片段也变紫胶囊, 与 citation 视觉冲突, 用户报"现在乱了"。
     * inline code 保持 markwon 默认 CodeSpan (灰底等宽字)。
     */
    private fun applyTypography(input: CharSequence): CharSequence {
        val src: Spanned = input as? Spanned ?: return input
        val out = SpannableStringBuilder(src)

        val headingSpans: Array<io.noties.markwon.core.spans.HeadingSpan> = out.getSpans(
            0, out.length, io.noties.markwon.core.spans.HeadingSpan::class.java,
        )
        for (s in headingSpans) {
            val st = out.getSpanStart(s); val en = out.getSpanEnd(s)
            if (st < 0 || en <= st) continue
            val len = en - st

            // 防御后端 markdown 把整段正文也用 `##` 开头标成 H2 (实际生产数据出现过 179 字符的 H2):
            // 真实标题通常 < 40 字符, 中长标题极少超过 60。超过阈值视为误识, 直接 removeSpan
            // 把 HeadingSpan 拆掉让其退化成正文, 避免大字号 + 粗体 + 横线把整段正文吞掉。
            if (len > HEADING_MAX_LEN) {
                out.removeSpan(s)
                continue
            }

            val level = readHeadingLevel(s)
            // 字号梯度对齐 iOS OctoSummaryMarkdownRender (base 14):
            //   H1 19 (base+5) / H2 17 (base+3) / H3 16 (base+2) / H4-H6 = base
            // 关键是 H1 与 H2 必须差 ≥2sp 否则视觉上层级混在一起 (用户反馈"不协调")。
            val sizeSp = when (level) {
                1 -> 19
                2 -> 17
                3 -> 16
                else -> 14
            }
            // 不 removeSpan(s) — 保留 HeadingSpan 让它继续画 H1/H2 下方分隔线。
            // AbsoluteSizeSpan/StyleSpan 后加, 在 paint 链中后应用, 覆盖字号 + 强制粗体。
            out.setSpan(android.text.style.AbsoluteSizeSpan(sizeSp, true), st, en,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, en,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return out
    }

    private val headingLevelField by lazy {
        runCatching {
            io.noties.markwon.core.spans.HeadingSpan::class.java
                .getDeclaredField("level").apply { isAccessible = true }
        }.getOrNull()
    }

    /** 看起来超过这个长度的 "heading" 极大概率是后端 markdown 把正文用 `##` 开头错标的, 退回正文渲染. */
    private val HEADING_MAX_LEN = 60

    private fun readHeadingLevel(span: io.noties.markwon.core.spans.HeadingSpan): Int =
        headingLevelField?.runCatching { getInt(span) }?.getOrNull() ?: 1

    private fun wideLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).also {
        it.topMargin = (8 * resources.displayMetrics.density).toInt()
    }

    /** 复用 WKTextProvider.buildTableCardView 同款表格卡 (project shared layout). */
    private fun buildTableCard(parent: ViewGroup, data: WKTableData): View {
        val card = LayoutInflater.from(this)
            .inflate(com.chat.base.R.layout.layout_markdown_table_card, parent, false)
        card.tag = dynamicTag

        val table = card.findViewById<TableLayout>(com.chat.base.R.id.tableContent)
        val scroll = card.findViewById<HorizontalScrollView>(com.chat.base.R.id.tableScrollView)
        val copyBtn = card.findViewById<ImageView>(com.chat.base.R.id.tableCopyBtn)

        if (data.headers.isEmpty() && data.rows.isEmpty()) {
            table.setStretchAllColumns(false)
            return card
        }
        // 仅在水平方向手指占主导 (|dx| > |dy|) 时禁止外层拦截, 让外层 NestedScrollView
        // 仍能拿到纯垂直手势上下滚整页。之前不分轴向 disallowIntercept 导致垂直手指
        // 在表格区域被 HorizontalScrollView 截留, 整页无法上下滑。
        scroll.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var horizontalLocked = false
            private val touchSlop = android.view.ViewConfiguration.get(this@SmartSummaryDetailActivity).scaledTouchSlop
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x; downY = e.y; horizontalLocked = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!horizontalLocked) {
                            val dx = kotlin.math.abs(e.x - downX)
                            val dy = kotlin.math.abs(e.y - downY)
                            if (dx > touchSlop && dx > dy) {
                                horizontalLocked = true
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        horizontalLocked = false
                    }
                }
                return false
            }
        })

        val dp = resources.displayMetrics.density
        val padH = (10 * dp).toInt()
        val padV = (8 * dp).toInt()
        val textSize = 13f
        val headerBg = Color.parseColor("#F0F0F0")
        val evenRowBg = Color.parseColor("#FAFAFA")
        val border = Color.parseColor("#E8E8E8")
        val headerFg = Color.parseColor("#333333")
        val cellFg = Color.parseColor("#555555")

        table.setStretchAllColumns(true)

        if (data.headers.isNotEmpty()) {
            val hr = TableRow(this)
            hr.setBackgroundColor(headerBg)
            for ((ci, h) in data.headers.withIndex()) {
                hr.addView(buildCell(h.text, textSize, padH, padV, headerFg, true, data, ci, border))
            }
            table.addView(hr)
        }
        for ((ri, row) in data.rows.withIndex()) {
            val tr = TableRow(this)
            if (ri % 2 == 1) tr.setBackgroundColor(evenRowBg)
            for ((ci, cell) in row.withIndex()) {
                tr.addView(buildCell(cell.text, textSize, padH, padV, cellFg, false, data, ci, border))
            }
            table.addView(tr)
        }
        copyBtn.setOnClickListener {
            val sb = StringBuilder()
            if (data.headers.isNotEmpty()) {
                sb.appendLine(data.headers.joinToString("\t") { it.text })
            }
            for (row in data.rows) {
                sb.appendLine(row.joinToString("\t") { it.text })
            }
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("table", sb.toString().trimEnd()))
            SummaryHud.show(this, com.chat.base.R.string.str_table_copied)
        }
        return card
    }

    private fun buildCell(
        text: String,
        sizeSp: Float,
        padH: Int,
        padV: Int,
        textColor: Int,
        isHeader: Boolean,
        data: WKTableData,
        colIdx: Int,
        borderColor: Int,
    ): TextView {
        // cell 文本走 CitationPostProcessor: 把 [N] / [N][M]... 转成紫色徽章 + ClickableSpan
        // 跳源消息, 与正文 citation 行为一致 (1:1 对齐 iOS, 后端给的 cell text 里也带 [N] 标记)。
        val patchedCell: CharSequence = CitationPostProcessor.process(
            this, android.text.SpannableString(text),
        ) { indices -> onCitationTap(indices) }
        val tv = TextView(this).apply {
            this.text = patchedCell
            // citation badge 内含 ClickableSpan, 不设 movementMethod 点不响应
            movementMethod = LinkMovementMethod.getInstance()
            // 防止整行 ClickableSpan 在 selection 模式下变蓝高亮全行 (Android 默认行为)
            highlightColor = android.graphics.Color.TRANSPARENT
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setPadding(padH, padV, padH, padV)
            if (isHeader) typeface = android.graphics.Typeface.DEFAULT_BOLD
            // 与消息气泡 cell 同款 1px 描边背景: 直接 setBackgroundColor(borderColor) + 透明 inner padding,
            // 用 LayerDrawable 描边圆角更复杂, 先与 WKTextProvider 实现保持基本一致 (普通描边方块)。
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 列对齐
            if (colIdx < data.alignments.size) {
                gravity = when (data.alignments[colIdx]) {
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER -> android.view.Gravity.CENTER
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                    else -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                }
            }
        }
        // 1px 描边: 用前景描边 drawable 模拟. 简化版 — 给 cell 套 layer-list 的 stroke 效果。
        val strokeDp = resources.displayMetrics.density
        val stroke = android.graphics.drawable.GradientDrawable().apply {
            setStroke(strokeDp.toInt().coerceAtLeast(1), borderColor)
            setColor(android.graphics.Color.TRANSPARENT)
        }
        tv.foreground = stroke
        return tv
    }

    private fun onCitationTap(indices: List<Int>) {
        val citations = viewModel.state.value.detail?.result?.citations.orEmpty()
        if (citations.isEmpty() || indices.isEmpty()) return
        SummaryRelatedChatSheet.show(supportFragmentManager, citations, indices)
    }

    private fun forwardToChannels(channels: List<com.xinbida.wukongim.entity.WKChannel>) {
        if (channels.isEmpty()) return
        val content = viewModel.state.value.detail?.result?.content.orEmpty()
        if (content.isEmpty()) return
        var ok = 0
        var fail = 0
        for (ch in channels) {
            val tc = com.xinbida.wukongim.msgmodel.WKTextContent(content)
            val opts = com.xinbida.wukongim.entity.WKSendOptions()
            opts.setting.receipt = ch.receipt
            val msg = com.xinbida.wukongim.WKIM.getInstance().msgManager.sendWithOptions(tc, ch, opts)
            if (msg != null) ok++ else fail++
        }
        val text = if (fail == 0) {
            getString(R.string.summary_forward_succeeded, ok)
        } else {
            getString(R.string.summary_forward_partial, ok, fail)
        }
        SummaryHud.show(this, text)
    }

    private fun confirmDelete() {
        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.summary_detail_confirm_delete_title),
            getString(R.string.summary_detail_confirm_delete_msg),
            true,
            getString(R.string.summary_common_cancel),
            getString(R.string.summary_common_delete),
            0,
            getColor(R.color.summary_red),
        ) { idx ->
            if (idx == 1) viewModel.performDelete()
        }
    }

    private fun toastResIdOf(kind: DetailToastKind): Int = when (kind) {
        DetailToastKind.LoadFailed -> R.string.summary_detail_load_failed
        DetailToastKind.Cancelled -> R.string.summary_cancelled
        DetailToastKind.CancelFailed -> R.string.summary_cancel_failed
        DetailToastKind.RegenStarted -> R.string.summary_regenerate_started
        DetailToastKind.RegenFailed -> R.string.summary_regenerate_failed
        DetailToastKind.DeleteFailed -> R.string.summary_list_delete_failed
    }

    private fun factory(): ViewModelProvider.Factory {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SmartSummaryDetailViewModel(taskId) as T
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"

        fun newIntent(ctx: android.content.Context, taskId: Long): Intent =
            Intent(ctx, SmartSummaryDetailActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
    }
}
