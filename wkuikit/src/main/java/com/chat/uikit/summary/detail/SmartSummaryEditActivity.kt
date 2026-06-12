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

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.chat.base.base.WKBaseActivity
import com.chat.base.summary.SummaryDeps
import com.chat.base.summary.repository.SummaryRepository
import com.chat.base.utils.WKDialogUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActSmartSummaryEditBinding
import com.chat.uikit.summary.SummaryHud
import kotlinx.coroutines.launch

/**
 * 编辑总结正文. 1:1 对齐 iOS [OctoSummaryEditVC]:
 *   - 全屏 EditText 直接编辑 markdown 原文
 *   - 标题栏右侧 "保存": 调用 editSummary, 成功后 finish; 409 提示已被他人更新
 *   - 返回前若有未保存修改, 弹确认对话框
 */
class SmartSummaryEditActivity : WKBaseActivity<ActSmartSummaryEditBinding>() {

    private val repository: SummaryRepository = SummaryDeps.repository

    private var initialContent: String = ""
    private var taskId: Long = 0L
    private var resultId: Long = 0L

    override fun getViewBinding(): ActSmartSummaryEditBinding =
        ActSmartSummaryEditBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.summary_edit_title)
    }

    override fun getRightTvText(textView: TextView?): String =
        getString(R.string.summary_edit_save)

    override fun rightLayoutClick() {
        save()
    }

    override fun initView() {
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        resultId = intent.getLongExtra(EXTRA_RESULT_ID, 0L)
        initialContent = intent.getStringExtra(EXTRA_CONTENT).orEmpty()

        wkVBinding.contentEt.setText(initialContent)
        wkVBinding.contentEt.setSelection(initialContent.length)
    }

    override fun onBackPressed() {
        if (wkVBinding.contentEt.text?.toString() == initialContent) {
            super.onBackPressed()
            return
        }
        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.summary_edit_discard_title),
            getString(R.string.summary_edit_discard_msg),
            true,
            getString(R.string.summary_edit_keep_editing),
            getString(R.string.summary_edit_discard),
            0,
            getColor(R.color.summary_red),
        ) { idx ->
            if (idx == 1) finish()
        }
    }

    private fun save() {
        if (taskId <= 0L) return
        val content = wkVBinding.contentEt.text?.toString().orEmpty()
        lifecycleScope.launch {
            val res = repository.editSummary(taskId, content, resultId)
            if (res.isFailure) {
                val msg = res.exceptionOrNull()?.message.orEmpty()
                val resId = if (msg.contains("409")) {
                    R.string.summary_edit_conflict
                } else R.string.summary_edit_save_failed
                SummaryHud.show(this@SmartSummaryEditActivity, resId)
                return@launch
            }
            finish()
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_RESULT_ID = "result_id"
        private const val EXTRA_CONTENT = "content"

        fun newIntent(ctx: Context, taskId: Long): Intent =
            Intent(ctx, SmartSummaryEditActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }

        fun newIntent(ctx: Context, taskId: Long, resultId: Long, content: String): Intent =
            Intent(ctx, SmartSummaryEditActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_RESULT_ID, resultId)
                putExtra(EXTRA_CONTENT, content)
            }
    }
}
