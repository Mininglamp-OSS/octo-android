/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.confirm

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.chat.base.base.WKBaseActivity
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.model.Participant
import com.chat.base.summary.model.ParticipantStatus
import com.chat.base.summary.model.SourceItem
import com.chat.base.summary.repository.SummaryRepository
import com.chat.uikit.R
import com.chat.uikit.databinding.ActSmartSummaryConfirmBinding
import com.chat.uikit.databinding.ItemSummaryConfirmRowBinding
import com.chat.uikit.summary.SummaryHud
import kotlinx.coroutines.launch

/**
 * 参与确认页. 1:1 对齐 iOS [OctoSummaryConfirmVC]:
 *   - 进入时拉一次 detail 填充 participants + sources (避开 Parcelable / Serializable 传参)
 *   - 参与者 read only: name + status (已确认 / 已拒绝 / 等待中)
 *   - 来源可勾选, 默认全选, 行点击切换
 *   - 底部 "确认参与" 紫色胶囊: 至少一个勾选才允许提交,
 *     成功后 hud 提示 + finish 返回详情页
 */
class SmartSummaryConfirmActivity : WKBaseActivity<ActSmartSummaryConfirmBinding>() {

    private val repository: SummaryRepository = SummaryDeps.repository

    private var taskId: Long = 0L
    private var participants: List<Participant> = emptyList()
    private var sources: List<SourceItem> = emptyList()
    private val checkedSourceKeys: MutableSet<String> = mutableSetOf()

    override fun getViewBinding(): ActSmartSummaryConfirmBinding =
        ActSmartSummaryConfirmBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.summary_confirm_title)
    }

    override fun initView() {
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        wkVBinding.confirmBtn.setOnClickListener { onConfirm() }
        loadDetail()
    }

    private fun loadDetail() {
        if (taskId <= 0L) return
        lifecycleScope.launch {
            val res = repository.getSummaryDetail(taskId)
            val detail = res.getOrNull() ?: return@launch
            participants = detail.participants
            sources = detail.sources
            checkedSourceKeys.clear()
            sources.forEach { checkedSourceKeys += keyOf(it) }
            renderParticipants()
            renderSources()
        }
    }

    private fun renderParticipants() {
        val container = wkVBinding.participantsContainer
        container.removeAllViews()
        for (p in participants) {
            val row = ItemSummaryConfirmRowBinding.inflate(LayoutInflater.from(this), container, false)
            row.nameTv.text = p.userName?.takeIf { it.isNotEmpty() } ?: p.userId
            row.statusTv.visibility = View.VISIBLE
            row.statusTv.text = participantStatusText(p.status)
            row.checkIv.visibility = View.GONE
            row.root.isClickable = false
            row.root.background = null
            container.addView(row.root)
        }
    }

    private fun renderSources() {
        val container = wkVBinding.sourcesContainer
        container.removeAllViews()
        for (s in sources) {
            val row = ItemSummaryConfirmRowBinding.inflate(LayoutInflater.from(this), container, false)
            row.nameTv.text = s.sourceName?.takeIf { it.isNotEmpty() } ?: s.sourceId
            row.statusTv.visibility = View.GONE
            row.checkIv.visibility = if (keyOf(s) in checkedSourceKeys) View.VISIBLE else View.INVISIBLE
            row.root.setOnClickListener {
                val key = keyOf(s)
                if (key in checkedSourceKeys) checkedSourceKeys -= key else checkedSourceKeys += key
                row.checkIv.visibility = if (key in checkedSourceKeys) View.VISIBLE else View.INVISIBLE
            }
            container.addView(row.root)
        }
    }

    private fun participantStatusText(status: ParticipantStatus): String = when (status) {
        ParticipantStatus.Confirmed -> getString(R.string.summary_confirm_status_confirmed)
        ParticipantStatus.Declined -> getString(R.string.summary_confirm_status_declined)
        else -> getString(R.string.summary_card_status_waiting)
    }

    private fun onConfirm() {
        val picked = sources.filter { keyOf(it) in checkedSourceKeys }
        if (picked.isEmpty()) {
            SummaryHud.show(this, R.string.summary_confirm_pick_one_source)
            return
        }
        if (taskId <= 0L) return
        wkVBinding.confirmBtn.isEnabled = false
        lifecycleScope.launch {
            val res = repository.confirmParticipation(taskId, picked)
            wkVBinding.confirmBtn.isEnabled = true
            if (res.isFailure) {
                SummaryHud.show(this@SmartSummaryConfirmActivity, R.string.summary_confirm_submit_failed)
                return@launch
            }
            SummaryHud.show(this@SmartSummaryConfirmActivity, R.string.summary_confirm_success)
            finish()
        }
    }

    private fun keyOf(s: SourceItem): String = "${s.sourceType.raw}:${s.sourceId}"

    companion object {
        private const val EXTRA_TASK_ID = "task_id"

        fun newIntent(ctx: Context, taskId: Long): Intent =
            Intent(ctx, SmartSummaryConfirmActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
    }
}
