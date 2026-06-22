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
import android.os.Build
import android.view.WindowInsetsController
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.ICommonListener
import com.chat.base.space.PendingGroupInvite
import com.chat.base.utils.WKDialogUtils
import com.chat.scan.ScanJoinGroupActivity
import com.chat.uikit.TabActivity
import com.chat.uikit.WKUIKitApplication
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceCreateDialog
import com.chat.uikit.space.SpaceEntity
import com.chat.uikit.space.SpaceModel
import com.octoim.app.databinding.ActivitySpaceGuideBinding

class SpaceGuideActivity : WKBaseActivity<ActivitySpaceGuideBinding>() {

    override fun getViewBinding(): ActivitySpaceGuideBinding {
        return ActivitySpaceGuideBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        setupStatusBar()

        wkVBinding.btnJoin.setOnClickListener {
            wkVBinding.viewFlipper.displayedChild = 1
        }

        wkVBinding.btnCreate.setOnClickListener {
            showCreateDialog()
        }

        wkVBinding.btnBack.setOnClickListener {
            wkVBinding.viewFlipper.displayedChild = 0
        }

        wkVBinding.btnDoJoin.setOnClickListener {
            val code = wkVBinding.etInviteCode.text.toString().trim()
            if (code.isBlank()) return@setOnClickListener
            doJoinSpace(code)
        }

        // issue #66：新注册用户在邀请码页没有团队可进，必须给一个出口。
        // 与设置页"退出登录"行为一致：弹二次确认 → exitLogin(0) 清账号 +
        // disconnect IM + 关 DB + 跳回登录页。
        wkVBinding.btnLogout.setOnClickListener {
            WKDialogUtils.getInstance().showDialog(
                this,
                getString(R.string.login_out),
                getString(R.string.login_out_dialog),
                true,
                "",
                getString(R.string.login_out),
                0, 0
            ) { index ->
                if (index == 1) {
                    WKUIKitApplication.getInstance().exitLogin(0)
                }
            }
        }
    }

    private fun setupStatusBar() {
        window.statusBarColor = 0xFF7761F4.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun doJoinSpace(inviteCode: String) {
        wkVBinding.btnDoJoin.isEnabled = false
        SpaceModel.getInstance().joinSpace(inviteCode, object : ICommonListener {
            override fun onResult(code: Int, msg: String?) {
                wkVBinding.btnDoJoin.isEnabled = true
                if (code == 200) {
                    showToast(getString(R.string.space_joined))
                    fetchSpacesAndGo()
                } else if (msg != null && (msg.contains("已经是") || msg.contains("已是") || msg.contains("already"))) {
                    fetchSpacesAndGo()
                } else {
                    showToast(msg ?: getString(R.string.space_invite_invalid))
                }
            }
        })
    }

    private fun fetchSpacesAndGo() {
        SpaceModel.getInstance().getMySpaces(object : SpaceModel.ISpaceListListener {
            override fun onResult(list: List<SpaceEntity>?) {
                if (!list.isNullOrEmpty()) {
                    MsgModel.getInstance().setCurrentSpaceId(list[0].space_id, list[0].name ?: "")
                    goToTab()
                } else {
                    showToast(getString(R.string.space_join_failed))
                }
            }

            override fun onError(code: Int, msg: String?) {
                goToTab()
            }
        })
    }

    private fun showCreateDialog() {
        val dialog = SpaceCreateDialog(this)
        dialog.setOnSpaceCreatedListener { space ->
            if (space != null) {
                MsgModel.getInstance().setCurrentSpaceId(space.space_id, space.name ?: "")
            }
            goToTab()
        }
        dialog.show()
    }

    private fun goToTab() {
        //  Phase 2 · 若有暂存的 pending_group_invite（扫码 / App Link 入口
        // 命中 need_space 时落盘），加 Space 成功后优先回到 ScanJoinGroupActivity
        // 重新发起入群请求；无则走常规 Tab 跳转。
        //
        // 注意：这里不限定 extras 里的 `pending_group_invite=true`，因为
        //   1) 用户也可能走登录后冷启动的 SpaceGate 路径加 Space；
        //   2) consume() 是一次性的，没有 pending 时 no-op。
        val pending = try {
            PendingGroupInvite.consume()
        } catch (t: Throwable) {
            null
        }
        if (pending != null) {
            val retry = Intent(this@SpaceGuideActivity, ScanJoinGroupActivity::class.java).apply {
                putExtra("group_no", pending.groupNo)
                putExtra("auth_code", pending.authCode)
                putExtra("group_name", pending.groupName)
                putExtra("avatar", pending.avatar)
                putExtra("member_count", pending.memberCount)
                putExtra("is_member", pending.isMember)
                putExtra("space_id", pending.spaceId)
                putExtra("space_name", pending.spaceName)
                // 确保先跳 Tab、再从 Tab 上拉起 ScanJoin，避免背景栈只剩扫码页。
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            // 先进 Tab 兜底（若 ScanJoin 失败仍有主界面可回），再叠加 ScanJoin。
            startActivity(Intent(this@SpaceGuideActivity, TabActivity::class.java))
            startActivity(retry)
            finish()
            return
        }
        startActivity(Intent(this@SpaceGuideActivity, TabActivity::class.java))
        finish()
    }
}
