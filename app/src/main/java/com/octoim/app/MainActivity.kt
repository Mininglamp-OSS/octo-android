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

package com.octoim.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.WKDialogUtils
import com.chat.login.ui.PerfectUserInfoActivity
import com.chat.login.ui.WKLoginActivity
import com.chat.uikit.TabActivity
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceModel


/**
 * 轻量级路由 Activity：不继承 WKBaseActivity，不膨胀布局（除非首次显示协议弹窗）。
 * 对于已登录用户的冷启动，onCreate 只做条件判断 + startActivity，省去 ~200ms 的布局膨胀开销。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isShowDialog = WKSharedPreferencesUtil.getInstance()
            .getBoolean("show_agreement_dialog")
        if (!isShowDialog) {
            // 快速路径：已同意协议，直接路由，不膨胀布局
            gotoApp()
            return
        }

        // 慢路径：首次启动，需要显示协议弹窗
        setContentView(R.layout.activity_main)
        showDialog()
    }

    private fun gotoApp() {
        if (!TextUtils.isEmpty(WKConfig.getInstance().token)) {
            if (TextUtils.isEmpty(WKConfig.getInstance().userInfo.name)) {
                startActivity(Intent(this, PerfectUserInfoActivity::class.java))
            } else {
                loadSpaceAndGo()
                return
            }
        } else {
            val intent = Intent(this, WKLoginActivity::class.java)
            intent.putExtra("from", getIntent().getIntExtra("from", 0))
            startActivity(intent)
        }
        finish()
    }

    private fun loadSpaceAndGo() {
        MsgModel.getInstance().loadCurrentSpaceId()
        val currentSpaceId = MsgModel.getInstance().currentSpaceId
        if (!currentSpaceId.isNullOrEmpty()) {
            val intent = Intent(this, TabActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
            return
        }
        // 没有 currentSpaceId，从服务器获取 Space 列表
        SpaceModel.getInstance().getMySpaces(object : SpaceModel.ISpaceListListener {
            override fun onResult(list: List<com.chat.uikit.space.SpaceEntity>?) {
                if (!list.isNullOrEmpty() && !list[0].space_id.isNullOrEmpty()) {
                    MsgModel.getInstance().setCurrentSpaceId(list[0].space_id, list[0].name ?: "")
                    startActivity(Intent(this@MainActivity, TabActivity::class.java))
                } else {
                    val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
                    intent.putExtra("from", getIntent().getIntExtra("from", 0))
                    startActivity(intent)
                }
                finish()
            }

            override fun onError(code: Int, msg: String?) {
                val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
                intent.putExtra("from", getIntent().getIntExtra("from", 0))
                startActivity(intent)
                finish()
            }
        })
    }

    private fun showDialog() {
        val content = getString(R.string.dialog_content)
        val linkSpan = SpannableStringBuilder()
        linkSpan.append(content)
        val userAgreementIndex = content.indexOf(getString(R.string.main_user_agreement))
        linkSpan.setSpan(
            NormalClickableSpan(
                true,
                ContextCompat.getColor(this, R.color.blue),
                NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                object : NormalClickableSpan.IClick {
                    override fun onClick(view: View) {
                        startActivity(
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse(WKApiConfig.baseWebUrl + "user_agreement.html"))
                        )
                    }
                }), userAgreementIndex, userAgreementIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val privacyPolicyIndex = content.indexOf(getString(R.string.main_privacy_policy))
        linkSpan.setSpan(
            NormalClickableSpan(true,
                ContextCompat.getColor(this, R.color.blue),
                NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                object : NormalClickableSpan.IClick {
                    override fun onClick(view: View) {
                        startActivity(
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse(WKApiConfig.baseWebUrl + "privacy_policy.html"))
                        )
                    }
                }), privacyPolicyIndex, privacyPolicyIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.dialog_title),
            linkSpan,
            false,
            getString(R.string.disagree),
            getString(R.string.agree),
            0,
            0
        ) { index ->
            if (index == 1) {
                WKSharedPreferencesUtil.getInstance()
                    .putBoolean("show_agreement_dialog", false)
                WKBaseApplication.getInstance().init(
                    WKBaseApplication.getInstance().packageName,
                    WKBaseApplication.getInstance().application
                )
                gotoApp()
            } else {
                finish()
            }
        }
    }
}
