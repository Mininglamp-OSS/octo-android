/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.create

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chat.base.base.WKBaseActivity
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.model.SourceType
import com.chat.base.summary.model.TopicTemplate
import com.chat.base.summary.model.TopicTemplatePlaceholder
import com.chat.uikit.R
import com.chat.uikit.databinding.ActSmartSummaryCreateBinding
import com.chat.uikit.summary.SummaryHud
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import kotlinx.coroutines.launch

/**
 * 发起总结页. 1:1 对齐 iOS [OctoSummaryCreateVC]:
 *   - 顶栏右侧 "开始总结" (主题非空 + 至少 1 来源 时高亮)
 *   - topic 卡: 输入框 + 横向模板 chip
 *   - source 卡: 选择聊天 (chevron) + 已选 pill 流式
 *
 * 选源走全局 [EndpointManager.invoke(showChooseChatView)] (与项目其它转发入口一致),
 * 内部启动 ChooseChatActivity, 通过 [ChatChooseContacts.IChoose] 异步回调结果。
 *
 * 创建成功后通过 [Activity.RESULT_OK] 通知调用方刷新列表。
 */
class SmartSummaryCreateActivity : WKBaseActivity<ActSmartSummaryCreateBinding>() {

    private val viewModel: SmartSummaryCreateViewModel by viewModels()
    private var saveBtn: Button? = null

    /**
     * 不走 EndpointSID.showChooseChatView (那条链路 isChoose=true 强制弹"转发到 X 个聊天?"
     * 二次对话框, 仅 messageContentList==null 才直接回调; 但 endpoint 总把 list 设成非空).
     * 这里直接 startActivityForResult ChooseChatActivity 走 setResult 路径, 拿 list extra 即可。
     */
    private val pickSourcesLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val channels: List<WKChannel> =
            data.getParcelableArrayListExtra<WKChannel>("list")?.toList().orEmpty()
        val sources = channels.mapNotNull { channelToSource(it) }
        viewModel.setSources(sources)
    }

    override fun getViewBinding(): ActSmartSummaryCreateBinding =
        ActSmartSummaryCreateBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.summary_create_title)
    }

    override fun getRightBtnText(titleRightBtn: Button?): String {
        saveBtn = titleRightBtn
        // 1:1 对齐 iOS:
        //   - 背景固定黑底白字胶囊 (canSubmit=false 时 alpha 0.5 让黑变"灰", 不是改背景色)
        //   - 14sp medium (不是 bold), 高 32, 圆角 16
        //   - 禁掉 Material Button 默认的 disabled tint + stateListAnimator,
        //     否则 disabled 时背景被自动套上系统灰色, 与 iOS 行为不一致
        titleRightBtn?.apply {
            isAllCaps = false
            backgroundTintList = null
            stateListAnimator = null
            setBackgroundResource(R.drawable.bg_summary_submit_btn)
            setTextColor(getColor(R.color.summary_button_solid_fg))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            val hPad = dp(14f)
            val vPad = dp(5f)
            setPadding(hPad, vPad, hPad, vPad)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            elevation = 0f
            isEnabled = false
            alpha = 0.5f
            (layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32f),
            )).also {
                it.height = dp(32f)
                layoutParams = it
            }
        }
        return getString(R.string.summary_create_submit)
    }

    override fun rightButtonClick() {
        viewModel.submit(
            originChannelId = intent.getStringExtra(EXTRA_ORIGIN_CHANNEL_ID).orEmpty(),
            originChannelType = intent.getIntExtra(EXTRA_ORIGIN_CHANNEL_TYPE, 0),
        )
    }

    override fun initView() {
        // "选择聊天 *" 红星
        val labelText = SpannableLabel.build(
            this,
            getString(R.string.summary_create_choose_chat),
            " *",
            getColor(R.color.summary_red),
        )
        wkVBinding.sourceFieldLabel.text = labelText

        wkVBinding.topicEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setTopic(s?.toString().orEmpty())
            }
        })

        wkVBinding.sourceChevron.setOnClickListener { pickSources() }
        wkVBinding.sourcePlaceholder.setOnClickListener { pickSources() }

        wkVBinding.selectedSources.maxRows = 3
        wkVBinding.selectedSources.onRemove = { item -> viewModel.removeSource(item) }

        // 装本地 fallback 模板, 后端拉到再覆盖
        viewModel.setLocalTemplates(localFallbackTemplates())

        observeState()
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

    private fun render(state: CreateUiState) {
        // submit 按钮
        saveBtn?.let { btn ->
            btn.isEnabled = state.canSubmit
            btn.alpha = if (state.canSubmit) 1f else 0.5f
        }

        // 输入框 (避免重置光标, 仅模板插入时同步)
        if (wkVBinding.topicEt.text?.toString().orEmpty() != state.topic) {
            wkVBinding.topicEt.setText(state.topic)
            wkVBinding.topicEt.setSelection(state.topic.length)
        }

        // 模板 chips
        renderTemplateChips(state.templates, state.activeTemplateId)

        // 已选 sources
        val hasSel = state.sources.isNotEmpty()
        wkVBinding.sourcePlaceholder.isVisible = !hasSel
        wkVBinding.selectedSources.isVisible = hasSel
        if (hasSel) wkVBinding.selectedSources.items = state.sources

        // effect
        state.effect?.let { effect ->
            when (effect) {
                is CreateEffect.ToastRes -> SummaryHud.show(this, effect.resId)
                CreateEffect.Done -> {
                    SummaryHud.show(this, R.string.summary_create_success)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            viewModel.consumeEffect()
        }
    }

    private fun renderTemplateChips(templates: List<TopicTemplate>, activeId: String?) {
        val row = wkVBinding.templateRow
        // 简化策略: templates 变化时全清重建; 单 active 切换走 child.tag 标记后单独刷新
        val needsRebuild = row.childCount != templates.size ||
            templates.indices.any { (row.getChildAt(it).tag as? String) != templates[it].templateId }
        if (needsRebuild) {
            row.removeAllViews()
            templates.forEachIndexed { _, t ->
                row.addView(makeTemplateChip(t, t.templateId == activeId))
            }
        } else {
            // 仅 active 状态变化, 重设背景与色
            for (i in 0 until row.childCount) {
                val v = row.getChildAt(i)
                applyTemplateChipStyle(v, templates[i].templateId == activeId)
            }
        }
    }

    private fun makeTemplateChip(t: TopicTemplate, active: Boolean): View {
        // chip = LinearLayout horizontal: icon 14dp + label, 1:1 对齐 iOS UIControl 几何
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = t.templateId
            val pad = dp(12f)
            setPadding(pad, 0, pad, 0)
            setOnClickListener { applyTemplate(t) }
            isClickable = true
            isFocusable = true
        }

        val icon = android.widget.ImageView(this).apply {
            setImageResource(iconResForTemplateId(t.templateId))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(14f), dp(14f))
        }
        chip.addView(icon)

        val label = AppCompatTextView(this).apply {
            text = t.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { leftMargin = dp(6f) }
        }
        chip.addView(label)

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(28f),
        ).apply { rightMargin = dp(8f) }
        chip.layoutParams = lp

        applyTemplateChipStyle(chip, active)
        return chip
    }

    /** templateId → 本地 icon res. 未知 id 用 file 兜底, 与 iOS 同思路. */
    private fun iconResForTemplateId(tid: String): Int = when (tid) {
        "weekly_report" -> R.drawable.ic_summary_tpl_calendar
        "chat_content" -> R.drawable.ic_summary_tpl_message
        "project_progress" -> R.drawable.ic_summary_tpl_file
        "task_tracking" -> R.drawable.ic_summary_tpl_list
        else -> R.drawable.ic_summary_tpl_file
    }

    private fun applyTemplateChipStyle(chip: View, active: Boolean) {
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_summary_template_chip_active)
        } else {
            chip.setBackgroundResource(R.drawable.bg_summary_template_chip_inactive)
        }
        // 子 view 颜色
        if (chip is LinearLayout) {
            for (i in 0 until chip.childCount) {
                val c = chip.getChildAt(i)
                if (c is android.widget.ImageView) {
                    c.setColorFilter(
                        if (active) getColor(R.color.summary_purple)
                        else getColor(R.color.summary_text_strong),
                    )
                } else if (c is TextView) {
                    c.setTextColor(
                        if (active) getColor(R.color.summary_purple)
                        else getColor(R.color.summary_text_strong),
                    )
                }
            }
        }
    }

    private fun applyTemplate(t: TopicTemplate) {
        val pattern = t.pattern.ifEmpty { t.label }
        // parameterized: 把 {key} 部分替换为空, 焦点定到该位置; fixed: 直接整段填入
        val isParam = t.type == "parameterized"
        val (text, caret) = if (isParam) extractParameterized(pattern) else (pattern to pattern.length)
        viewModel.applyTemplate(t, text)
        // 模板插入后聚焦 + 设光标
        wkVBinding.topicEt.requestFocus()
        wkVBinding.topicEt.setSelection(caret.coerceAtMost(text.length))
    }

    private fun extractParameterized(pattern: String): Pair<String, Int> {
        val open = pattern.indexOf('{')
        val close = pattern.indexOf('}')
        if (open < 0 || close <= open) return pattern to pattern.length
        val cleaned = pattern.removeRange(open, close + 1)
        return cleaned to open
    }

    private fun pickSources() {
        // 直接启动 ChooseChatActivity 走 setResult 路径, 不再二次弹框
        pickSourcesLauncher.launch(Intent(this, com.chat.uikit.chat.ChooseChatActivity::class.java))
    }

    private fun channelToSource(ch: WKChannel): SourceItem? {
        val type = when (ch.channelType) {
            WKChannelType.PERSONAL -> SourceType.DirectMessage
            WKChannelType.COMMUNITY_TOPIC -> SourceType.Thread
            else -> SourceType.GroupChat
        }
        return SourceItem(
            sourceType = type,
            sourceId = ch.channelID,
            sourceName = ch.channelName?.ifEmpty { null } ?: ch.channelRemark?.ifEmpty { null },
        )
    }

    private fun localFallbackTemplates(): List<TopicTemplate> = listOf(
        TopicTemplate(
            templateId = "project_progress",
            label = getString(R.string.summary_tpl_project_label),
            icon = null,
            description = null,
            type = "parameterized",
            pattern = getString(R.string.summary_tpl_project_pattern),
            placeholders = listOf(
                TopicTemplatePlaceholder("project_name", getString(R.string.summary_tpl_project_placeholder), null),
            ),
        ),
        TopicTemplate(
            templateId = "task_tracking",
            label = getString(R.string.summary_tpl_task_label),
            icon = null,
            description = null,
            type = "parameterized",
            pattern = getString(R.string.summary_tpl_task_pattern),
            placeholders = listOf(
                TopicTemplatePlaceholder("task_name", getString(R.string.summary_tpl_task_placeholder), null),
            ),
        ),
        TopicTemplate(
            templateId = "weekly_report",
            label = getString(R.string.summary_tpl_weekly_label),
            icon = null,
            description = null,
            type = "fixed",
            pattern = getString(R.string.summary_tpl_weekly_pattern),
            placeholders = emptyList(),
        ),
        TopicTemplate(
            templateId = "chat_content",
            label = getString(R.string.summary_tpl_chat_label),
            icon = null,
            description = null,
            type = "fixed",
            pattern = getString(R.string.summary_tpl_chat_pattern),
            placeholders = emptyList(),
        ),
    )

    private fun dp(v: Float): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val EXTRA_ORIGIN_CHANNEL_ID = "origin_channel_id"
        private const val EXTRA_ORIGIN_CHANNEL_TYPE = "origin_channel_type"

        fun newIntent(
            ctx: Context,
            originChannelId: String = "",
            originChannelType: Int = 0,
        ): Intent = Intent(ctx, SmartSummaryCreateActivity::class.java).apply {
            putExtra(EXTRA_ORIGIN_CHANNEL_ID, originChannelId)
            putExtra(EXTRA_ORIGIN_CHANNEL_TYPE, originChannelType)
        }
    }
}

/** 标题栏右侧 "*" 红色尾巴 (复用项目里没有专门 SpannableUtil, 临时写一个). */
private object SpannableLabel {
    fun build(ctx: Context, base: String, suffix: String, suffixColor: Int): CharSequence {
        val builder = android.text.SpannableStringBuilder(base + suffix)
        builder.setSpan(
            android.text.style.ForegroundColorSpan(suffixColor),
            base.length, base.length + suffix.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return builder
    }
}
