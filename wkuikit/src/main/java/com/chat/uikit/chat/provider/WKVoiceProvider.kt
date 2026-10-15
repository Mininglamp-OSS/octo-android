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

package com.chat.uikit.chat.provider

import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.ud.WKDownloader
import com.chat.base.net.ud.WKProgressManager
import com.chat.base.ui.Theme
import com.chat.base.ui.components.SecretDeleteTimer
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKCacheKeys
import com.chat.base.utils.WKCommonUtils
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.message.MsgModel
import com.chat.uikit.view.CircleProgress
import com.chat.uikit.view.WKPlayVoiceUtils
import com.chat.uikit.view.WKPlayVoiceUtils.IPlayListener
import com.chat.uikit.view.WaveformView
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.message.type.WKMsgContentType
import com.xinbida.wukongim.msgmodel.WKVoiceContent
import java.io.File
import kotlin.math.max

class WKVoiceProvider : WKChatBaseProvider() {
    private var lastClientMsgNo: String? = null

    companion object {
        private const val TAG = "WKVoiceProvider"
    }

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_voice, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)

        val voiceTimeTv = parentView.findViewById<TextView>(R.id.voiceTimeTv)
        val voiceWaveform = parentView.findViewById<WaveformView>(R.id.voiceWaveform)
        val playBtn = parentView.findViewById<CircleProgress>(R.id.playBtn)
        playBtn.setProgressColor(Theme.colorAccount)

        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        if (from == WKChatIteMsgFromType.SEND) {
            contentLayout.gravity = Gravity.END
            voiceTimeTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
            playBtn.setShadowColor(ContextCompat.getColor(context, R.color.white))
        } else {
            contentLayout.gravity = Gravity.START
            voiceTimeTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
            playBtn.setShadowColor(ContextCompat.getColor(context, R.color.homeColor))
        }

        playBtn.setBindId(uiChatMsgItemEntity.wkMsg.clientMsgNO)
        if (WKPlayVoiceUtils.getInstance().playKey != uiChatMsgItemEntity.wkMsg.clientMsgNO) {
            if (from == WKChatIteMsgFromType.SEND) {
                voiceWaveform.isFresh = false
            } else voiceWaveform.isFresh = uiChatMsgItemEntity.wkMsg.voiceStatus == 0
        }
        val voiceContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKVoiceContent
        if (voiceContent == null) {
            voiceTimeTv.text = context.getString(R.string.base_unknow_msg)
            return
        }
        voiceWaveform.layoutParams.width =
            getVoiceWidth(voiceContent.timeTrad, uiChatMsgItemEntity.wkMsg.flame)
        if (!TextUtils.isEmpty(voiceContent.waveform)) {
            val bytes = WKCommonUtils.getInstance().base64Decode(voiceContent.waveform)
            voiceWaveform.setWaveform(bytes)
        }
        if (TextUtils.isEmpty(voiceContent.localPath)) {
            playBtn.enableDownload()
        } else {
            if (!uiChatMsgItemEntity.isPlaying) {
                playBtn.setPlay()
            } else playBtn.setPause()
        }
        val showTime: String
        var mStr: String
        var sStr: String
        if (voiceContent.timeTrad >= 60) {
            val m = voiceContent.timeTrad / 60
            val s = voiceContent.timeTrad % 60
            mStr = m.toString()
            sStr = s.toString()
            if (m < 10) {
                mStr = "0$m"
            }
            if (s < 10) {
                sStr = "0$s"
            }
        } else {
            mStr = "00"
            sStr = if (voiceContent.timeTrad < 10) {
                "0" + voiceContent.timeTrad
            } else voiceContent.timeTrad.toString()
        }
        showTime = String.format("%s:%s", mStr, sStr)
        voiceTimeTv.text = showTime
        playBtn.setOnClickListener {
            lastClientMsgNo = uiChatMsgItemEntity.wkMsg.clientMsgNO
            if (TextUtils.isEmpty(voiceContent.localPath)) {
                stopPlay()
                val fileDir =
                    WKConstants.voiceDir + uiChatMsgItemEntity.wkMsg.channelType + "/" + uiChatMsgItemEntity.wkMsg.channelID + "/"
                WKFileUtils.getInstance().createFileDir(fileDir)

                // 缓存身份 = (频道类型, 频道ID, clientMsgNO)，与普通文件同构（B-3 修复）。
                // 磁盘名由 SHA-256(身份) 派生，不同 clientMsgNO 必不同路径。WKPlayVoiceUtils
                // 的 playVoice 接受 (voicePath, playKey) 分离传参——voicePath 送
                // MediaPlayer.setDataSource，playKey 做播放回调标识——因此磁盘 basename
                // 无需等于 clientMsgNO。
                val identity = WKCacheKeys.voiceIdentity(
                    uiChatMsgItemEntity.wkMsg.channelType,
                    uiChatMsgItemEntity.wkMsg.channelID,
                    uiChatMsgItemEntity.wkMsg.clientMsgNO
                )
                val basename = WKCacheKeys.basenameForIdentity(identity)
                val filePath = fileDir + basename + ".amr"
                val file = File(filePath)
                if (file.exists()) {
                    playBtn.setPlay()

                    WKPlayVoiceUtils.getInstance()
                        .playVoice(filePath, uiChatMsgItemEntity.wkMsg.clientMsgNO)
                    updateViewed(uiChatMsgItemEntity, parentView, from)
                } else {
                    if (TextUtils.isEmpty(voiceContent.url)){
                        return@setOnClickListener
                    }
                    playBtn.enableLoading(1)
                    WKDownloader.instance.download(
                        WKApiConfig.getShowUrl(voiceContent.url),
                        filePath,
                        object : WKProgressManager.IProgress {
                            override fun onProgress(tag: Any?, progress: Int) {
                                playBtn.enableLoading(progress)
                            }

                            override fun onSuccess(tag: Any?, path: String?) {
                                if (!TextUtils.isEmpty(filePath)) {
                                    voiceContent.localPath = filePath
                                    uiChatMsgItemEntity.wkMsg.voiceStatus = 1
                                    uiChatMsgItemEntity.wkMsg.baseContentMsgModel = voiceContent
                                    // merge 单键回写，不能用 updateContentAndRefresh(model)：
                                    // WKVoiceContent.encodeMsg 只输出 localPath/timeTrad/url/
                                    // waveform，整列覆盖会抹掉顶层的 space_id / mention /
                                    // reply / entities / robot_id，导致 Space 隔离与 @ 提及
                                    // 状态静默失效。
                                    val merged = WKIM.getInstance().msgManager.updateContentLocalPath(
                                        uiChatMsgItemEntity.wkMsg.clientMsgNO,
                                        filePath,
                                        false
                                    )
                                    if (!merged) {
                                        // false = DB 无该行或 content JSON 已损坏；
                                        // 内存态 localPath 已赋值，仅缺持久化，不影响本次播放。
                                        Log.w(TAG, "updateContentLocalPath merged nothing, clientMsgNO=${uiChatMsgItemEntity.wkMsg.clientMsgNO}")
                                    }
                                    WKIM.getInstance().msgManager.updateVoiceReadStatus(
                                        uiChatMsgItemEntity.wkMsg.clientMsgNO,
                                        1,
                                        false
                                    )
                                    updateViewed(uiChatMsgItemEntity, parentView, from)
                                    MsgModel.getInstance().updateVoiceStatus(
                                        uiChatMsgItemEntity.wkMsg.messageID,
                                        uiChatMsgItemEntity.wkMsg.channelID,
                                        uiChatMsgItemEntity.wkMsg.channelType,
                                        uiChatMsgItemEntity.wkMsg.messageSeq
                                    )
                                    if (!TextUtils.isEmpty(lastClientMsgNo) && lastClientMsgNo == uiChatMsgItemEntity.wkMsg.clientMsgNO) {
                                        playBtn.setPlay()
                                        //  voiceWaveform.setFresh(uiChatMsgItemEntity.wkMsg.voiceStatus == 0);
                                        WKPlayVoiceUtils.getInstance()
                                            .playVoice(
                                                filePath,
                                                uiChatMsgItemEntity.wkMsg.clientMsgNO
                                            )
                                    }
                                }

                            }

                            override fun onFail(tag: Any?, msg: String?) {
                                WKToastUtils.getInstance()
                                    .showToastNormal(context.getString(R.string.voice_download_fail))
                            }

                        })
                }
            } else {
                // localPath 非空分支 = 历史已下载的语音。
                //
                // merge-base 的 onSuccess 持久化的是 fileDir + <裸 clientMsgNO> + ".amr"
                // （未经任何清洗），`m:a.amr` 与 `m?a.amr` 在文件系统上本就不同名，
                // 读它不产生 B-3 的错配 —— 故**不强制迁移**，避免升级后已下载语音全部重下。
                //
                // 唯一需要排除的是本分支中间提交用 safePathSegment 写出的有损名
                // （`m:a` 与 `m?a` 同塌 `m_a.amr`，会互相命中）。该形状的识别依据：
                // basename 既不是 64 位 hex（新派生名），也不等于裸 clientMsgNO（旧名）。
                // 命中则丢弃该 localPath，回落到下载链路按新身份重下。
                val lpName = File(voiceContent.localPath).name
                val rawName = uiChatMsgItemEntity.wkMsg.clientMsgNO + ".amr"
                val lossyGeneration = lpName != rawName &&
                        !lpName.matches(Regex("[0-9a-f]{64}\\.amr"))
                if (lossyGeneration) {
                    Log.i(TAG, "discard lossy-generation voice localPath: $lpName")
                    voiceContent.localPath = ""
                    stopPlay()
                    playBtn.setPlay()
                    return@setOnClickListener
                }
                if (WKPlayVoiceUtils.getInstance().isPlaying) {
                    if (WKPlayVoiceUtils.getInstance()
                            .oldPlayKey == uiChatMsgItemEntity.wkMsg.clientMsgNO
                    ) {
                        WKPlayVoiceUtils.getInstance().onPause()
                        playBtn.setPlay()
                    } else {

                        stopPlay()
                        updateViewed(uiChatMsgItemEntity, parentView, from)
                        WKPlayVoiceUtils.getInstance()
                            .playVoice(
                                voiceContent.localPath,
                                uiChatMsgItemEntity.wkMsg.clientMsgNO
                            )
                    }
                } else {
                    val file = File(voiceContent.localPath)
                    if (file.exists()) {
                        updateViewed(uiChatMsgItemEntity, parentView, from)
                        WKPlayVoiceUtils.getInstance()
                            .playVoice(
                                voiceContent.localPath,
                                uiChatMsgItemEntity.wkMsg.clientMsgNO
                            )
                    } else {
                        stopPlay()
                    }
                }
            }
        }
        val oldListener = playBtn.getTag(R.id.playBtn) as? IPlayListener
        if (oldListener != null) {
            WKPlayVoiceUtils.getInstance().removePlayListener(oldListener)
        }
        val playListener = object : IPlayListener {
            override fun onCompletion(key: String) {
                if (key == uiChatMsgItemEntity.wkMsg.clientMsgNO) {
                    voiceWaveform.setProgress(0f)
                    playBtn.setPlay()
                    uiChatMsgItemEntity.isPlaying = false
                    voiceWaveform.isFresh = false
                    playNext(key)
                }
            }

            override fun onProgress(key: String, pg: Float) {
                if (key == uiChatMsgItemEntity.wkMsg.clientMsgNO) {
                    voiceWaveform.setProgress(pg)
                    playBtn.setPause()
                    uiChatMsgItemEntity.isPlaying = true
                }
            }

            override fun onStop(key: String) {
                if (key == uiChatMsgItemEntity.wkMsg.clientMsgNO) {
                    voiceWaveform.setProgress(0f)
                    playBtn.setPlay()
                    uiChatMsgItemEntity.isPlaying = false
                    voiceWaveform.isFresh = false
                }
            }
        }
        playBtn.setTag(R.id.playBtn, playListener)
        WKPlayVoiceUtils.getInstance().setPlayListener(playListener)
    }

    override val itemViewType: Int
        get() = WKMsgContentType.WK_VOICE


    private fun stopPlay() {
        WKPlayVoiceUtils.getInstance().stopPlay()
        var i = 0
        val size = getAdapter()!!.data.size
        while (i < size) {
            if (getAdapter()!!.data[i].wkMsg != null
                && getAdapter()!!.data[i].wkMsg.clientMsgNO == WKPlayVoiceUtils.getInstance().oldPlayKey
            ) {
                getAdapter()!!.data[i].isPlaying = false
                val waveformView =
                    getAdapter()!!.getViewByPosition(i, R.id.voiceWaveform) as WaveformView?
                val tempPlayBtn =
                    getAdapter()!!.getViewByPosition(i, R.id.playBtn) as CircleProgress?
                waveformView?.setProgress(0f)
                tempPlayBtn?.setPlay()
                break
            }
            i++
        }
    }

    private fun playNext(clientMsgNO: String) {
        val list: List<WKUIChatMsgItemEntity> = getAdapter()!!.data
        if (list.isNotEmpty()) {
            for (i in list.indices) {
                val mMsg = list[i].wkMsg
                if (mMsg != null && mMsg.type == WKContentType.WK_VOICE && mMsg.clientMsgNO != clientMsgNO && mMsg.voiceStatus == 0 && !TextUtils.isEmpty(
                        mMsg.fromUID
                    )
                    && mMsg.fromUID != WKConfig.getInstance().uid
                ) {
                    val tempPlayBtn =
                        getAdapter()!!.getViewByPosition(i, R.id.playBtn) as CircleProgress?
                    tempPlayBtn?.performClick()
                    break
                }
            }
        }
    }

    private fun getVoiceWidth(timeTrad: Int, flame: Int): Int {
        var showWidth = 0
        val minWidth = AndroidUtilities.dp(150f)
        if (timeTrad <= 10) {
            showWidth = minWidth
        } else if (timeTrad <= 20) {
            showWidth = (minWidth * 1.1).toInt()
        } else if (timeTrad <= 30) {
            showWidth = (minWidth * 1.2).toInt()
        } else if (timeTrad <= 40) {
            showWidth = (minWidth * 1.3).toInt()
        } else if (timeTrad <= 50) {
            showWidth = (minWidth * 1.4).toInt()
        } else if (timeTrad <= 60) {
            showWidth = (minWidth * 1.5).toInt()
        }
        if (flame == 1) {
            showWidth -= AndroidUtilities.dp(45f)
        }
        return showWidth
    }

    fun updateViewed(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        parentView: View,
        from: WKChatIteMsgFromType
    ) {
        if (uiChatMsgItemEntity.wkMsg.flame == 1 && uiChatMsgItemEntity.wkMsg.viewed == 0) {
            uiChatMsgItemEntity.wkMsg.viewed = 1
            uiChatMsgItemEntity.wkMsg.viewedAt =
                WKTimeUtils.getInstance().currentMills
            WKIM.getInstance().msgManager.updateViewedAt(
                1,
                uiChatMsgItemEntity.wkMsg.viewedAt,
                uiChatMsgItemEntity.wkMsg.clientMsgNO
            )
            val parentLayout = parentView as LinearLayout
            var deleteTimer: SecretDeleteTimer? = null
            if (uiChatMsgItemEntity.wkMsg.flameSecond > 0 && parentLayout.childCount > 1) {
                if (from == WKChatIteMsgFromType.RECEIVED) {
                    deleteTimer =
                        parentLayout.getChildAt(1) as SecretDeleteTimer
                } else if (from == WKChatIteMsgFromType.SEND) {
                    deleteTimer =
                        parentLayout.getChildAt(0) as SecretDeleteTimer
                }
                if (deleteTimer != null) {
                    deleteTimer.visibility = View.VISIBLE
                    val flameSecond: Int =
                        if (uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_VOICE) {
                            val voiceContent =
                                uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKVoiceContent
                            max(voiceContent?.timeTrad ?: 0, uiChatMsgItemEntity.wkMsg.flameSecond)
                        } else {
                            uiChatMsgItemEntity.wkMsg.flameSecond
                        }
                    deleteTimer.setDestroyTime(
                        uiChatMsgItemEntity.wkMsg.clientMsgNO,
                        flameSecond,
                        uiChatMsgItemEntity.wkMsg.viewedAt,
                        false
                    )
                }
            }
        }
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        val voiceLayout = parentView.findViewById<BubbleLayout>(R.id.voiceLayout)
        voiceLayout.setAll(bgType, from, WKContentType.WK_VOICE)

    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val voiceLayout = parentView.findViewById<BubbleLayout>(R.id.voiceLayout)
        val playBtn = parentView.findViewById<CircleProgress>(R.id.playBtn)
        addLongClick(voiceLayout, uiChatMsgItemEntity)
        addLongClick(playBtn, uiChatMsgItemEntity)
    }
}