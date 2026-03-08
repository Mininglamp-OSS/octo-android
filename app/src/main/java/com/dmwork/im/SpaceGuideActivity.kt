package com.dmwork.im

import android.content.Intent
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.ICommonListener
import com.chat.base.utils.WKDialogUtils
import com.chat.uikit.TabActivity
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceModel
import com.dmwork.im.databinding.ActivitySpaceGuideBinding

class SpaceGuideActivity : WKBaseActivity<ActivitySpaceGuideBinding>() {

    override fun getViewBinding(): ActivitySpaceGuideBinding {
        return ActivitySpaceGuideBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()

        wkVBinding.btnJoin.setOnClickListener {
            showJoinDialog()
        }

        wkVBinding.btnCreate.setOnClickListener {
            showCreateDialog()
        }
    }

    private fun showJoinDialog() {
        WKDialogUtils.getInstance().showInputDialog(
            this,
            "加入团队",
            "请输入邀请码",
            "",
            "邀请码",
            20,
            object : WKDialogUtils.IInputDialog {
                override fun onResult(text: String?) {
                    if (text.isNullOrBlank()) return
                    SpaceModel.getInstance().joinSpace(text, object : ICommonListener {
                        override fun onResult(code: Int, msg: String?) {
                            if (code == 200) {
                                // 加入成功后重新获取 space 列表来拿 spaceId
                                SpaceModel.getInstance().getMySpaces(object : SpaceModel.ISpaceListListener {
                                    override fun onResult(list: List<com.chat.uikit.space.SpaceEntity>?) {
                                        if (!list.isNullOrEmpty()) {
                                            MsgModel.getInstance().setCurrentSpaceId(list[0].space_id)
                                        }
                                        goToTab()
                                    }

                                    override fun onError(code: Int, msg: String?) {
                                        goToTab()
                                    }
                                })
                            } else {
                                showToast(msg ?: "加入失败")
                            }
                        }
                    })
                }
            }
        )
    }

    private fun showCreateDialog() {
        WKDialogUtils.getInstance().showInputDialog(
            this,
            "创建新团队",
            "请输入团队名称",
            "",
            "团队名称",
            30,
            object : WKDialogUtils.IInputDialog {
                override fun onResult(text: String?) {
                    if (text.isNullOrBlank()) return
                    SpaceModel.getInstance().createSpace(text, "", object : SpaceModel.ISpaceListener {
                        override fun onResult(space: com.chat.uikit.space.SpaceEntity?) {
                            if (space != null) {
                                MsgModel.getInstance().setCurrentSpaceId(space.space_id)
                            }
                            goToTab()
                        }

                        override fun onError(code: Int, msg: String?) {
                            showToast(msg ?: "创建失败")
                        }
                    })
                }
            }
        )
    }

    private fun goToTab() {
        startActivity(Intent(this@SpaceGuideActivity, TabActivity::class.java))
        finish()
    }
}
