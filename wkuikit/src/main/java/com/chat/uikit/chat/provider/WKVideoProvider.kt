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

import android.app.Activity
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.PlayVideoMenu
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKMsgBgType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.ud.WKProgressManager
import com.chat.base.ui.Theme
import com.chat.base.ui.components.FilterImageView
import com.chat.base.ui.components.SecretDeleteTimer
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.LayoutHelper
import com.chat.base.views.CircularProgressView
import com.chat.base.views.blurview.ShapeBlurView
import com.chat.uikit.R
import com.xinbida.wukongim.message.type.WKMsgContentType
import com.xinbida.wukongim.msgmodel.WKVideoContent
import java.io.File

class WKVideoProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_video, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val videoContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKVideoContent
        if (videoContent == null) {
            contentLayout.removeAllViews()
            return
        }
        val coverImageView = parentView.findViewById<FilterImageView>(R.id.coverImageView)
        val blurView = parentView.findViewById<ShapeBlurView>(R.id.blurView)
        setCorners(from, uiChatMsgItemEntity, coverImageView, blurView)

        val progressTv = parentView.findViewById<TextView>(R.id.progressTv)
        val progressView = parentView.findViewById<CircularProgressView>(R.id.progressView)
        progressView.setProgColor(Theme.colorAccount)
        val playIv = parentView.findViewById<ImageView>(R.id.playIv)
        val durationTv = parentView.findViewById<TextView>(R.id.durationTv)
        val videoLayout = parentView.findViewById<View>(R.id.videoLayout)
        val otherLayout = parentView.findViewById<FrameLayout>(R.id.otherLayout)
        val deleteTimer = SecretDeleteTimer(context)

        otherLayout.removeAllViews()
        otherLayout.addView(deleteTimer, LayoutHelper.createFrame(35, 35, Gravity.CENTER))
        contentLayout.gravity =
            if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START else Gravity.END

        val layoutParams = coverImageView.layoutParams as FrameLayout.LayoutParams
        val blurViewLayoutParams = blurView.layoutParams as FrameLayout.LayoutParams
        val ints = ImageUtils.getInstance()
            .getImageWidthAndHeightToTalk(videoContent.width, videoContent.height)

        blurView.visibility = if (uiChatMsgItemEntity.wkMsg.flame == 1) View.VISIBLE else View.GONE
        if (uiChatMsgItemEntity.wkMsg.flame == 1) {
            otherLayout.visibility = View.VISIBLE
            playIv.visibility = View.GONE
            deleteTimer.setSize(35)
            if (uiChatMsgItemEntity.wkMsg.viewedAt > 0 && uiChatMsgItemEntity.wkMsg.flameSecond > 0) {
                deleteTimer.setDestroyTime(
                    uiChatMsgItemEntity.wkMsg.clientMsgNO,
                    uiChatMsgItemEntity.wkMsg.flameSecond,
                    uiChatMsgItemEntity.wkMsg.viewedAt,
                    false
                )
            }
        } else {
            otherLayout.visibility = View.GONE
            playIv.visibility = View.VISIBLE
        }

        // show cover image
        val coverUrl = getCoverURL(videoContent)
        GlideUtils.getInstance().showImg(context, coverUrl, ints[0], ints[1], coverImageView)

        // show duration
        if (videoContent.second > 0) {
            durationTv.visibility = View.VISIBLE
            durationTv.text = formatDuration(videoContent.second)
        } else {
            durationTv.visibility = View.GONE
        }

        val layoutParams1 = videoLayout.layoutParams as LinearLayout.LayoutParams
        if (uiChatMsgItemEntity.wkMsg.flame == 1) {
            layoutParams.height = AndroidUtilities.dp(150f)
            layoutParams.width = AndroidUtilities.dp(150f)
            blurViewLayoutParams.height = AndroidUtilities.dp(150f)
            blurViewLayoutParams.width = AndroidUtilities.dp(150f)
            layoutParams1.height = AndroidUtilities.dp(150f)
            layoutParams1.width = AndroidUtilities.dp(150f)
        } else {
            layoutParams.height = ints[1]
            layoutParams.width = ints[0]
            blurViewLayoutParams.height = ints[1]
            blurViewLayoutParams.width = ints[0]
            layoutParams1.height = ints[1]
            layoutParams1.width = ints[0]
        }
        coverImageView.layoutParams = layoutParams
        blurView.layoutParams = blurViewLayoutParams
        videoLayout.layoutParams = layoutParams1

        // upload progress
        if (TextUtils.isEmpty(videoContent.url)) {
            WKProgressManager.instance.registerProgress(uiChatMsgItemEntity.wkMsg.clientSeq,
                object : WKProgressManager.IProgress {
                    override fun onProgress(tag: Any?, progress: Int) {
                        if (tag is Long && tag == uiChatMsgItemEntity.wkMsg.clientSeq) {
                            progressView.progress = progress
                            progressTv.text = String.format("%s%%", progress)
                            if (progress >= 100) {
                                progressTv.visibility = View.GONE
                                progressView.visibility = View.GONE
                                playIv.visibility = View.VISIBLE
                            } else {
                                progressView.visibility = View.VISIBLE
                                progressTv.visibility = View.VISIBLE
                                playIv.visibility = View.GONE
                            }
                        }
                    }

                    override fun onSuccess(tag: Any?, path: String?) {
                        progressTv.visibility = View.GONE
                        progressView.visibility = View.GONE
                        playIv.visibility = View.VISIBLE
                        if (tag != null) {
                            WKProgressManager.instance.unregisterProgress(tag)
                        }
                    }

                    override fun onFail(tag: Any?, msg: String?) {}
                })
        }

        addLongClick(coverImageView, uiChatMsgItemEntity)
        coverImageView.setOnClickListener {
            onVideoClick(uiChatMsgItemEntity, coverImageView, coverUrl)
        }
    }

    override val itemViewType: Int
        get() = WKMsgContentType.WK_VIDEO

    private fun onVideoClick(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        coverImageView: ImageView,
        coverUrl: String
    ) {
        val videoContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKVideoContent ?: return
        val videoUrl = if (!TextUtils.isEmpty(videoContent.localPath)) {
            val file = File(videoContent.localPath)
            if (file.exists()) videoContent.localPath else WKApiConfig.getShowUrl(videoContent.url)
        } else {
            WKApiConfig.getShowUrl(videoContent.url)
        }

        val activity = context as? AppCompatActivity ?: return
        EndpointManager.getInstance().invoke(
            "play_video",
            PlayVideoMenu(activity, coverImageView, "", videoUrl, coverUrl)
        )
    }

    private fun getCoverURL(videoContent: WKVideoContent): String {
        if (!TextUtils.isEmpty(videoContent.coverLocalPath)) {
            val file = File(videoContent.coverLocalPath)
            if (file.exists() && file.length() > 0L) {
                return file.absolutePath
            }
        }
        if (!TextUtils.isEmpty(videoContent.cover)) {
            return WKApiConfig.getShowUrl(videoContent.cover)
        }
        return ""
    }

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val coverImageView = parentView.findViewById<FilterImageView>(R.id.coverImageView)
        addLongClick(coverImageView, uiChatMsgItemEntity)
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val coverImageView = parentView.findViewById<FilterImageView>(R.id.coverImageView)
        val blurView = parentView.findViewById<ShapeBlurView>(R.id.blurView)
        if (coverImageView != null && blurView != null) {
            setCorners(from, uiChatMsgItemEntity, coverImageView, blurView)
        }
    }

    private fun setCorners(
        from: WKChatIteMsgFromType,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        imageView: FilterImageView,
        blurView: ShapeBlurView
    ) {
        imageView.strokeWidth = 0f
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        if (bgType == WKMsgBgType.center) {
            if (from == WKChatIteMsgFromType.SEND) {
                imageView.setCorners(10, 5, 10, 5)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(),
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat()
                )
            } else {
                imageView.setCorners(5, 10, 5, 10)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(),
                    AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat()
                )
            }
        } else if (bgType == WKMsgBgType.top) {
            if (from == WKChatIteMsgFromType.SEND) {
                imageView.setCorners(10, 10, 10, 5)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(),
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat()
                )
            } else {
                imageView.setCorners(10, 10, 5, 10)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(),
                    AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat()
                )
            }
        } else if (bgType == WKMsgBgType.bottom) {
            if (from == WKChatIteMsgFromType.SEND) {
                imageView.setCorners(10, 5, 10, 10)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(),
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat()
                )
            } else {
                imageView.setCorners(5, 10, 10, 10)
                blurView.setCornerRadius(
                    AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(),
                    AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat()
                )
            }
        } else {
            imageView.setAllCorners(10)
            blurView.setCornerRadius(
                AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(),
                AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat()
            )
        }
    }
}
