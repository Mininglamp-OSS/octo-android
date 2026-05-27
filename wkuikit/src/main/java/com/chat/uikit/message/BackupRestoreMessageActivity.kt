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

package com.chat.uikit.message

import android.view.View
import android.widget.TextView
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKFileUtils.IWriteText
import com.chat.base.utils.WKReader
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.uikit.R
import com.chat.uikit.databinding.ActBackupMessageLayoutBinding
import com.chat.uikit.message.MsgModel.IRecovery
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKMsg
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File

class BackupRestoreMessageActivity : WKBaseActivity<ActBackupMessageLayoutBinding>() {
    private var handleType: Int = 1 // 1：备份 2：恢复
    override fun getViewBinding(): ActBackupMessageLayoutBinding {
        return ActBackupMessageLayoutBinding.inflate(layoutInflater)
    }

    override fun initPresenter() {
        handleType = intent.getIntExtra("handle_type", 1)
    }

    override fun setTitle(titleTv: TextView?) {
        if (handleType == 1) {
            titleTv!!.setText(R.string.backup_message)
        } else titleTv!!.setText(R.string.recovery_message)
    }

    override fun initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.startBtn) {
            wkVBinding.loading.visibility = View.VISIBLE
            wkVBinding.startBtn.alpha = 0.2f
            wkVBinding.startBtn.isEnabled = false
            if (handleType == 1) {
                Observable.create { e ->
                    val list1 = WKIM.getInstance().msgManager.all
                    e.onNext(list1)
                }.observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<List<WKMsg>> {
                        override fun onSubscribe(d: Disposable) {}
                        override fun onNext(list: List<WKMsg>) {
                            backup(list)
                        }

                        override fun onError(e: Throwable) {}
                        override fun onComplete() {}
                    })

            } else {
                recovery()
            }
        }

    }

    private fun backup(list: List<WKMsg>) {
        if (WKReader.isNotEmpty(list)) {
            val jsonArray = JSONArray()
            for (msg in list) {
                if (msg.isDeleted == 1) continue
                val json = JSONObject()
                json["channel_id"] = msg.channelID
                json["channel_type"] = msg.channelType
                json["message_id"] = msg.messageID
                json["message_seq"] = msg.messageSeq
                json["client_msg_no"] = msg.clientMsgNO
                json["from_uid"] = msg.fromUID
                json["payload"] = msg.content
                json["timestamp"] = msg.timestamp
                jsonArray.add(json)
            }
            val uid = WKConfig.getInstance().uid
            val path = WKConstants.messageBackupDir + uid + ".json"
            val file = File(path)
            if (file.exists()) file.delete()
            WKFileUtils.getInstance()
                .writeTxtToFile(jsonArray.toString(), path, object : IWriteText {
                    override fun onFail() {
                        showToast(getString(R.string.toast_backup_save_error))
                    }

                    override fun onSuccess() {
                        MsgModel.getInstance().backupMsg(
                            path
                        ) { code, msg ->
                            if (code != HttpResponseCode.success.toInt()) {
                                showToast(msg)
                            } else {
                                showToast(R.string.str_success)
                            }
                            wkVBinding.startBtn.alpha = 1.0f
                            wkVBinding.startBtn.isEnabled = true
                            wkVBinding.loading.visibility = View.GONE
                        }
                    }
                })
        }

    }

    private fun recovery() {
        MsgModel.getInstance().recovery(object : IRecovery {
            override fun onSuccess(path: String?) {
                finish()
            }

            override fun onFail() {
                showToast(R.string.recovery_msg_fail)
                wkVBinding.startBtn.alpha = 1.0f
                wkVBinding.startBtn.isEnabled = true
                wkVBinding.loading.visibility = View.GONE
            }
        })
    }
}