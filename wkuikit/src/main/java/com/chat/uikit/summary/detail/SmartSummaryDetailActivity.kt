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
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chat.base.base.WKBaseActivity
import com.chat.base.summary.model.SummaryDetail
import com.chat.base.summary.model.TaskStatus
import com.chat.base.utils.WKDialogUtils
import com.chat.uikit.R
import com.chat.uikit.summary.SummaryHud
import com.chat.uikit.databinding.ActSmartSummaryDetailBinding
import com.chat.uikit.summary.list.SummaryItemActionPopup
import com.chat.uikit.summary.markdown.CitationPostProcessor
import com.chat.uikit.summary.time.RelativeTime
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
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

    private lateinit var markwon: Markwon

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
        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(HtmlPlugin.create())
            // Markwon 默认 H1≈base*2 / H2≈base*1.5, 远大于 iOS OctoSummaryMarkdownRender 的
            // H1=base+5 / H2=base+3 / H3=base+1。base=14 时 iOS H1=19sp, Markwon H1=28sp,
            // 内容含 ## 标题时整段显得"字号变大"。这里把倍数对齐 iOS。
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    val base = 14f
                    builder.headingTextSizeMultipliers(
                        floatArrayOf(
                            (base + 5f) / base,  // H1 19
                            (base + 3f) / base,  // H2 17
                            (base + 1f) / base,  // H3 15
                            1f, 1f, 1f,          // H4-H6 与正文同
                        ),
                    )
                    builder.headingBreakHeight(0)
                }
            })
            .build()

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
        tv.setTextColor(0xFF1F1F1F.toInt())
        when {
            completed -> {
                val raw = d.result?.content.orEmpty()
                if (raw.isEmpty()) {
                    tv.text = ""
                    return
                }
                val rendered: Spanned = markwon.toMarkdown(raw)
                val withCitations = CitationPostProcessor.process(this, rendered) { indices ->
                    onCitationTap(indices)
                }
                tv.text = withCitations
                tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()
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
                tv.setTextColor(0x80000000.toInt())
            }
        }
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
