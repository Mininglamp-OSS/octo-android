package com.dmwork.im

import android.content.Intent
import android.os.Build
import android.view.WindowInsetsController
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.ICommonListener
import com.chat.uikit.TabActivity
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceCreateDialog
import com.chat.uikit.space.SpaceEntity
import com.chat.uikit.space.SpaceModel
import com.dmwork.im.databinding.ActivitySpaceGuideBinding

class SpaceGuideActivity : WKBaseActivity<ActivitySpaceGuideBinding>() {

    override fun getViewBinding(): ActivitySpaceGuideBinding {
        return ActivitySpaceGuideBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        setupStatusBar()

        // 欢迎页：点击"输入邀请码加入团队" → 切换到加入页
        wkVBinding.btnJoin.setOnClickListener {
            wkVBinding.viewFlipper.displayedChild = 1
        }

        // 欢迎页：点击"创建新团队" → 弹出创建 Dialog
        wkVBinding.btnCreate.setOnClickListener {
            showCreateDialog()
        }

        // 加入页：返回 → 切换回欢迎页
        wkVBinding.btnBack.setOnClickListener {
            wkVBinding.viewFlipper.displayedChild = 0
        }

        // 加入页：加入按钮
        wkVBinding.btnDoJoin.setOnClickListener {
            val code = wkVBinding.etInviteCode.text.toString().trim()
            if (code.isBlank()) return@setOnClickListener
            doJoinSpace(code)
        }
    }

    private fun setupStatusBar() {
        window.statusBarColor = 0xFF6366f1.toInt()
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
                // 无论成功还是"已是成员"，都查 Space 列表后进主页
                fetchSpacesAndGo()
            }
        })
    }

    private fun fetchSpacesAndGo() {
        SpaceModel.getInstance().getMySpaces(object : SpaceModel.ISpaceListListener {
            override fun onResult(list: List<SpaceEntity>?) {
                if (!list.isNullOrEmpty()) {
                    MsgModel.getInstance().setCurrentSpaceId(list[0].space_id)
                    goToTab()
                } else {
                    showToast("加入失败，请重试")
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
                MsgModel.getInstance().setCurrentSpaceId(space.space_id)
            }
            goToTab()
        }
        dialog.show()
    }

    private fun goToTab() {
        startActivity(Intent(this@SpaceGuideActivity, TabActivity::class.java))
        finish()
    }
}
