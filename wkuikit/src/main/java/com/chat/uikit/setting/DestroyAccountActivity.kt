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

package com.chat.uikit.setting

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActDestroyAccountLayoutBinding
import com.chat.uikit.user.service.UserModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DestroyAccountActivity : WKBaseActivity<ActDestroyAccountLayoutBinding>() {

    private var isRequesting = false

    override fun getViewBinding(): ActDestroyAccountLayoutBinding {
        return ActDestroyAccountLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.destroy_account)
    }

    override fun initPresenter() {}

    override fun initView() {
        fetchStatus()
    }

    override fun initListener() {
        val passwordEt = wkVBinding.passwordEt
        val applyBtn = wkVBinding.applyBtn

        passwordEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrEmpty()
                applyBtn.alpha = if (hasText) 1.0f else 0.5f
                applyBtn.isEnabled = hasText
            }
        })

        applyBtn.isEnabled = false
        applyBtn.setOnClickListener {
            val password = passwordEt.text.toString().trim()
            if (password.isEmpty()) return@setOnClickListener
            showApplyConfirmDialog(password)
        }

        wkVBinding.cancelDestroyBtn.setOnClickListener {
            showCancelConfirmDialog()
        }
    }

    private fun fetchStatus() {
        UserModel.getInstance().getDestroyStatus(object : UserModel.IDestroyStatusListener {
            override fun onResult(status: Int, remainingDays: Int, expireAt: String) {
                updateUI(status, remainingDays, expireAt)
            }

            override fun onError(code: Int, msg: String?) {
                updateUI(0, 0, "")
            }
        })
    }

    private fun isAlive(): Boolean = !isFinishing && !isDestroyed

    private fun updateUI(status: Int, remainingDays: Int, expireAt: String) {
        if (!isAlive()) return
        if (status == 1) {
            wkVBinding.normalLayout.visibility = View.GONE
            wkVBinding.applyingLayout.visibility = View.VISIBLE
            wkVBinding.remainingDaysTv.text =
                String.format(getString(R.string.destroy_remaining_days), remainingDays)
            if (expireAt.isNotEmpty()) {
                wkVBinding.expireDateTv.text =
                    String.format(getString(R.string.destroy_expire_date), formatDate(expireAt))
                wkVBinding.expireDateTv.visibility = View.VISIBLE
            } else {
                wkVBinding.expireDateTv.visibility = View.GONE
            }
        } else {
            wkVBinding.normalLayout.visibility = View.VISIBLE
            wkVBinding.applyingLayout.visibility = View.GONE
        }
    }

    private fun showApplyConfirmDialog(password: String) {
        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.destroy_account),
            getString(R.string.destroy_confirm_msg),
            true,
            getString(R.string.cancel),
            getString(R.string.destroy_confirm_btn),
            0,
            Theme.colorAccount,
        ) { index ->
            if (index == 1) {
                doApply(password)
            }
        }
    }

    private fun doApply(password: String) {
        if (isRequesting) return
        isRequesting = true
        UserModel.getInstance().applyDestroy(password, object : UserModel.IDestroyStatusListener {
            override fun onResult(status: Int, remainingDays: Int, expireAt: String) {
                isRequesting = false
                if (!isAlive()) return
                WKToastUtils.getInstance().showToastNormal(getString(R.string.destroy_applied_toast))
                updateUI(status, remainingDays, expireAt)
            }

            override fun onError(code: Int, msg: String?) {
                isRequesting = false
                if (!isAlive()) return
                WKToastUtils.getInstance().showToastNormal(msg ?: getString(R.string.str_net_error))
            }
        })
    }

    private fun showCancelConfirmDialog() {
        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.destroy_cancel_btn),
            getString(R.string.destroy_cancel_confirm_msg),
            true,
            getString(R.string.cancel),
            getString(R.string.destroy_cancel_confirm_btn),
            0,
            Theme.colorAccount,
        ) { index ->
            if (index == 1) {
                doCancelDestroy()
            }
        }
    }

    private fun doCancelDestroy() {
        if (isRequesting) return
        isRequesting = true
        UserModel.getInstance().cancelDestroy { code, msg ->
            isRequesting = false
            if (!isAlive()) return@cancelDestroy
            if (code == HttpResponseCode.success.toInt()) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.destroy_cancelled_toast))
                updateUI(0, 0, "")
            } else {
                WKToastUtils.getInstance().showToastNormal(msg ?: getString(R.string.str_net_error))
            }
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(isoDate)
            if (date != null) outputFormat.format(date) else isoDate
        } catch (_: Exception) {
            isoDate
        }
    }
}
