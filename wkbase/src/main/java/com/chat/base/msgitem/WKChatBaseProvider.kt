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

package com.chat.base.msgitem

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.MeasureSpec
import android.view.View.OnTouchListener
import android.view.View.TRANSLATION_X
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.chat.base.views.BubbleLayout
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.R
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.foldable.PaneMetrics
import com.chat.base.endpoint.entity.CanReactionMenu
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChatItemPopupMenu
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.endpoint.entity.MsgConfig
import com.chat.base.endpoint.entity.MsgReactionMenu
import com.chat.base.endpoint.entity.PrivacyMessageMenu
import com.chat.base.endpoint.entity.ReadMsgDetailMenu
import com.chat.base.endpoint.entity.ShowMsgReactionMenu
import com.chat.base.endpoint.entity.WithdrawMsgMenu
import com.chat.base.entity.PopupMenuItem
import com.chat.base.external.ExternalSourceResolver
import com.chat.base.msg.ChatAdapter
import com.chat.base.realname.RealnameBadgeResolver
import com.chat.base.ui.Theme
import com.chat.base.ui.components.ActionBarMenuSubItem
import com.chat.base.ui.components.ActionBarPopupWindow
import com.chat.base.ui.components.ActionBarPopupWindow.ActionBarPopupWindowLayout
import com.chat.base.ui.components.AvatarView
import com.chat.base.ui.components.ChatScrimPopupContainerLayout
import com.chat.base.ui.components.CheckBox
import com.chat.base.ui.components.PopupSwipeBackLayout
import com.chat.base.ui.components.ReactionsContainerLayout
import com.chat.base.ui.components.ReactionsContainerLayout.ReactionsContainerDelegate
import com.chat.base.ui.components.SecretDeleteTimer
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.StringUtils
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.ChatItemView
import com.google.android.material.snackbar.Snackbar
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.message.type.WKSendMsgResult
import com.xinbida.wukongim.msgmodel.WKVoiceContent
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import java.util.Objects
import kotlin.math.abs
import kotlin.math.max
import androidx.core.view.isVisible


private class RoundedBotBadgeSpan(
    private val textSize: Float,
    private val radius: Float,
    private val paddingH: Float,
    private val paddingV: Float
) : ReplacementSpan() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = this@RoundedBotBadgeSpan.textSize
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun getSize(
        paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        val textWidth = textPaint.measureText(text, start, end)
        return (textWidth + paddingH * 2).toInt()
    }

    override fun draw(
        canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val textWidth = textPaint.measureText(text, start, end)
        val badgeWidth = textWidth + paddingH * 2
        val badgeHeight = textPaint.textSize + paddingV * 2
        val badgeTop = y + paint.ascent() + (paint.descent() - paint.ascent() - badgeHeight) / 2
        val rect = RectF(x, badgeTop, x + badgeWidth, badgeTop + badgeHeight)

        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0xFF6366F1.toInt(), 0xFF8B5CF6.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val textX = x + paddingH
        val textY = badgeTop + paddingV - textPaint.ascent()
        canvas.drawText(text, start, end, textX, textY, textPaint)
    }
}

abstract class WKChatBaseProvider : BaseItemProvider<WKUIChatMsgItemEntity>() {

    override val layoutId: Int
        get() = R.layout.chat_item_base_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity, payloads: List<Any>) {
        super.convert(helper, item, payloads)
        val msgItemEntity = payloads[0] as WKUIChatMsgItemEntity
        val from = getMsgFromType(msgItemEntity.wkMsg)

        if (msgItemEntity.isRefreshReaction && helper.getViewOrNull<AvatarView>(R.id.avatarView) != null) {
            msgItemEntity.isRefreshReaction = false
            val avatarView = helper.getView<AvatarView>(R.id.avatarView)
            setAvatarLayoutParams(msgItemEntity, from, avatarView)
            EndpointManager.getInstance().invoke(
                "show_msg_reaction", ShowMsgReactionMenu(
                    helper.getView(R.id.reactionsView),
                    from,
                    (Objects.requireNonNull(getAdapter()) as ChatAdapter),
                    msgItemEntity.wkMsg.reactionList
                )
            )
        }
        if (msgItemEntity.isRefreshAvatarAndName && helper.getViewOrNull<AvatarView>(R.id.avatarView) != null) {
            val avatarView = helper.getView<AvatarView>(R.id.avatarView)
            setAvatar(msgItemEntity, avatarView)
            if (helper.getViewOrNull<View>(R.id.receivedNameTv) != null && msgItemEntity.wkMsg.type != WKContentType.typing && msgItemEntity.wkMsg.type != WKContentType.richText) {
                setFromName(msgItemEntity, from, helper.getView(R.id.receivedNameTv))
            } else {
                if (helper.getViewOrNull<View>(R.id.wkBaseContentLayout) != null) {
                    val baseView = helper.getView<LinearLayout>(R.id.wkBaseContentLayout)
                    resetFromName(helper.bindingAdapterPosition, baseView, msgItemEntity, from)

                }
            }
            msgItemEntity.isRefreshReaction = false
        }
        if (helper.getViewOrNull<CheckBox>(R.id.checkBox) != null) {
            setCheckBox(
                msgItemEntity,
                from,
                helper.getView(R.id.checkBox),
                helper.getView(R.id.viewContentLayout)
            )
            if (helper.getViewOrNull<View>(R.id.viewGroupLayout) != null) {
                val viewGroupLayout = helper.getView<ChatItemView>(R.id.viewGroupLayout)
                viewGroupLayout.setTouchData(
                    !msgItemEntity.isChoose
                ) { setChoose(helper, msgItemEntity) }
            }
        }
    }

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        showData(helper, item)
    }

    open fun refreshReply(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {

    }

    protected abstract fun getChatViewItem(
        parentView: ViewGroup,
        from: WKChatIteMsgFromType
    ): View?

    protected abstract fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    )

    fun refreshData(
        adapterPosition: Int,
        parentView: View,
        content: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        setData(adapterPosition, parentView, content, from)
    }

    open fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {

    }

    open fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    open fun resetFromName(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    open fun getMsgFromType(wkMsg: WKMsg?): WKChatIteMsgFromType {
        val from: WKChatIteMsgFromType = if (wkMsg != null) {
            if (!TextUtils.isEmpty(wkMsg.fromUID)
                && wkMsg.fromUID == WKConfig.getInstance().uid
            ) {
                WKChatIteMsgFromType.SEND //自己发送的
            } else {
                WKChatIteMsgFromType.RECEIVED //他人发的
            }
        } else {
            WKChatIteMsgFromType.SYSTEM //系统
        }
        return from
    }

    private fun showData(
        baseViewHolder: BaseViewHolder,
        msgItemEntity: WKUIChatMsgItemEntity
    ) {
        if (baseViewHolder.getViewOrNull<View>(R.id.viewGroupLayout) != null) {
            val viewGroupLayout = baseViewHolder.getView<ChatItemView>(R.id.viewGroupLayout)
            // 提示本条消息：优先检查 adapter 级别的 pendingHighlight（不受 setNewInstance 影响）
            val chatAdapter = getAdapter() as? ChatAdapter
            val matchedHighlight = chatAdapter?.consumeHighlightIfMatch(
                msgItemEntity.wkMsg?.orderSeq ?: 0
            ) ?: 0
            if (msgItemEntity.isShowTips || matchedHighlight != 0L) {
                val colorAnimation = ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    ContextCompat.getColor(context, R.color.tip_message_cell_bg),
                    ContextCompat.getColor(context, R.color.transparent)
                )
                colorAnimation.setDuration(2500)
                colorAnimation.addUpdateListener { animator ->
                    viewGroupLayout.setBackgroundColor(animator.animatedValue as Int)
                }
                colorAnimation.start()
                msgItemEntity.isShowTips = false
            }
            setItemPadding(baseViewHolder.bindingAdapterPosition, viewGroupLayout)
            viewGroupLayout.setOnClickListener {
                setChoose(
                    baseViewHolder,
                    msgItemEntity
                )
            }
            viewGroupLayout.setTouchData(
                !msgItemEntity.isChoose
            ) { setChoose(baseViewHolder, msgItemEntity) }
        }
        if (baseViewHolder.getViewOrNull<View>(R.id.wkBaseContentLayout) != null) {
            val fullContentLayout = baseViewHolder.getView<LinearLayout>(R.id.fullContentLayout)
            val baseView = baseViewHolder.getView<LinearLayout>(R.id.wkBaseContentLayout)
            val avatarView = baseViewHolder.getView<AvatarView>(R.id.avatarView)
            val from = getMsgFromType(msgItemEntity.wkMsg)

            // deleteTimer.invalidate()
//            val deleteTimerLP = deleteTimer.layoutParams as RelativeLayout.LayoutParams
            baseView.removeAllViews()
            baseView.addView(getChatViewItem(baseView, from))
            baseViewHolder.getView<View>(R.id.viewContentLayout).setOnClickListener {
                val chatAdapter = getAdapter() as ChatAdapter
                chatAdapter.conversationContext.hideSoftKeyboard()
            }
            setFullLayoutParams(msgItemEntity, from, fullContentLayout)
            setAvatarLayoutParams(msgItemEntity, from, avatarView)
            resetCellBackground(baseView, msgItemEntity, from)
            resetCellListener(
                baseViewHolder.bindingAdapterPosition,
                baseView,
                msgItemEntity,
                from
            )

            if (baseViewHolder.getViewOrNull<CheckBox>(R.id.checkBox) != null) {
                setCheckBox(
                    msgItemEntity,
                    from,
                    baseViewHolder.getView(R.id.checkBox),
                    baseViewHolder.getView(R.id.viewContentLayout)
                )
            }

            if (isAddFlameView(msgItemEntity)) {
                val deleteTimer = SecretDeleteTimer(context)
                deleteTimer.setSize(25)
                val flameSecond: Int =
                    if (msgItemEntity.wkMsg.type == WKContentType.WK_VOICE) {
                        val voiceContent =
                            msgItemEntity.wkMsg.baseContentMsgModel as? WKVoiceContent
                        max(voiceContent?.timeTrad ?: 0, msgItemEntity.wkMsg.flameSecond)
                    } else {
                        msgItemEntity.wkMsg.flameSecond
                    }

                deleteTimer.setDestroyTime(
                    msgItemEntity.wkMsg.clientMsgNO,
                    flameSecond,
                    msgItemEntity.wkMsg.viewedAt,
                    false
                )
                if (from == WKChatIteMsgFromType.RECEIVED) {
                    baseView.addView(
                        deleteTimer,
                        LayoutHelper.createLinear(
                            25,
                            25,
                            Gravity.CENTER or Gravity.BOTTOM,
                            5,
                            0,
                            0,
                            0
                        )
                    )
                } else {
                    baseView.addView(
                        deleteTimer,
                        0,
                        LayoutHelper.createLinear(
                            25,
                            25,
                            Gravity.CENTER or Gravity.BOTTOM,
                            0,
                            0,
                            5,
                            0
                        )
                    )
                }
                if (msgItemEntity.wkMsg.viewed == 0) {
                    deleteTimer.visibility = INVISIBLE
                } else deleteTimer.visibility = VISIBLE
            }

            if (msgItemEntity.isShowPinnedMessage) {
                val openMessageFrameLayout = FrameLayout(context)
                openMessageFrameLayout.background =
                    ContextCompat.getDrawable(context, R.drawable.shape_corner_rectangle)
                val openMessageImageView = AppCompatImageView(context)
                openMessageImageView.setImageResource(R.mipmap.filled_open_message)
                openMessageFrameLayout.addView(
                    openMessageImageView,
                    LayoutHelper.createFrame(25, 25, Gravity.CENTER)
                )
                if (from == WKChatIteMsgFromType.RECEIVED) {
                    baseView.addView(
                        openMessageFrameLayout,
                        LayoutHelper.createLinear(
                            30,
                            30,
                            Gravity.CENTER or Gravity.BOTTOM,
                            5,
                            0,
                            0,
                            0
                        )
                    )
                } else {
                    baseView.addView(
                        openMessageFrameLayout,
                        0,
                        LayoutHelper.createLinear(
                            30,
                            30,
                            Gravity.CENTER or Gravity.BOTTOM,
                            0,
                            0,
                            5,
                            0
                        )
                    )

                }
                openMessageFrameLayout.setOnClickListener {
                    EndpointManager.getInstance()
                        .invoke("tip_msg_in_chat", msgItemEntity.wkMsg.clientMsgNO)
                    val chatAdapter = getAdapter() as ChatAdapter
                    chatAdapter.conversationContext.closeActivity()
                }
            }


            setData(baseViewHolder.bindingAdapterPosition, baseView, msgItemEntity, from)
            // 群聊/子区覆盖气泡为微信风格（私聊保持原始风格不变）
            if (msgItemEntity.wkMsg.channelType == WKChannelType.GROUP
                || msgItemEntity.wkMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
                val bgType = getMsgBgType(msgItemEntity.previousMsg, msgItemEntity.wkMsg, msgItemEntity.nextMsg)
                applyGroupChatBubbleStyle(baseView, bgType, from)
            }
            if (baseViewHolder.getViewOrNull<View>(R.id.receivedNameTv) != null && msgItemEntity.wkMsg.type != WKContentType.typing && msgItemEntity.wkMsg.type != WKContentType.richText) {
                setFromName(msgItemEntity, from, baseViewHolder.getView(R.id.receivedNameTv))
            }
            setMsgTimeAndStatus(
                msgItemEntity,
                baseView,
                from
            )
            EndpointManager.getInstance().invoke(
                "show_msg_reaction", ShowMsgReactionMenu(
                    baseViewHolder.getView(R.id.reactionsView),
                    from,
                    (Objects.requireNonNull(getAdapter()) as ChatAdapter),
                    msgItemEntity.wkMsg.reactionList
                )
            )
            msgItemEntity.isUpdateStatus = false
        }
    }

    /**
     * 群聊气泡微信风格覆盖：圆角矩形 + 独立小箭头
     * 原始代码箭头在 bottom（最后一条），微信风格箭头应在 top（第一条）
     * 在 setData 之后调用，根据 bgType 重新设定箭头位置
     */
    private fun applyGroupChatBubbleStyle(view: View, bgType: WKMsgBgType, from: WKChatIteMsgFromType): Boolean {
        if (view is BubbleLayout) {
            val normalRadius = 10
            val arrowLength = AndroidUtilities.dp(6f)
            val arrowCurve = AndroidUtilities.dp(2f)
            val hasArrow = bgType == WKMsgBgType.top || bgType == WKMsgBgType.single

            if (hasArrow) {
                // 第一条/独立消息：设置微信风格箭头在顶部
                view.setArrowAtTop(true)
                view.setLookWidth(8)
                view.setLookLength(arrowLength)
                view.setLTR(normalRadius)
                view.setRTR(normalRadius)
                view.setLDR(normalRadius)
                view.setRDR(normalRadius)
                if (from == WKChatIteMsgFromType.RECEIVED) {
                    view.setLook(BubbleLayout.Look.LEFT)
                    view.setArrowDownRightRadius(0)
                    view.setArrowTopRightRadius(arrowCurve)
                    view.setArrowTopLeftRadius(arrowCurve)
                    view.setArrowDownLeftRadius(arrowCurve)
                } else {
                    view.setLook(BubbleLayout.Look.RIGHT)
                    view.setArrowDownLeftRadius(0)
                    view.setArrowTopLeftRadius(arrowCurve)
                    view.setArrowTopRightRadius(arrowCurve)
                    view.setArrowDownRightRadius(arrowCurve)
                }
                val lp = view.layoutParams
                if (lp is LinearLayout.LayoutParams) {
                    lp.leftMargin = 0
                    lp.rightMargin = 0
                }
            } else {
                // center/bottom：无箭头，纯圆角矩形
                view.setLook(BubbleLayout.Look.TOP)
                view.setLookLength(0)
                view.setArrowAtTop(false)
                view.setArrowDownRightRadius(0)
                view.setArrowDownLeftRadius(0)
                view.setLTR(normalRadius)
                view.setRTR(normalRadius)
                view.setLDR(normalRadius)
                view.setRDR(normalRadius)
                val lp = view.layoutParams
                if (lp is LinearLayout.LayoutParams) {
                    if (from == WKChatIteMsgFromType.RECEIVED) {
                        lp.leftMargin = AndroidUtilities.dp(6f)
                        lp.rightMargin = 0
                    } else {
                        lp.rightMargin = AndroidUtilities.dp(6f)
                        lp.leftMargin = 0
                    }
                }
            }
            view.initPadding()
            return true // 每个消息只有一个 BubbleLayout，找到即返回
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (applyGroupChatBubbleStyle(view.getChildAt(i), bgType, from)) return true
            }
        }
        return false
    }

    // 获取消息显示背景类型
    protected open fun getMsgBgType(
        previousMsg: WKMsg?,
        nowMsg: WKMsg,
        nextMsg: WKMsg?
    ): WKMsgBgType {
        val bgType: WKMsgBgType
        var previousBubble = false
        var nextBubble = false
        val previousIsSystem = previousMsg != null && WKContentType.isSystemMsg(previousMsg.type)
        val nextIsSystem = nextMsg != null && WKContentType.isSystemMsg(nextMsg.type)
        if (previousMsg != null && previousMsg.remoteExtra.revoke == 0 && previousMsg.isDeleted == 0 && !previousIsSystem
            && !TextUtils.isEmpty(previousMsg.fromUID)
            && previousMsg.fromUID == nowMsg.fromUID
        ) {
            previousBubble = true
        }
        if (nextMsg != null && nextMsg.remoteExtra.revoke == 0 && nextMsg.isDeleted == 0 && !nextIsSystem
            && !TextUtils.isEmpty(nextMsg.fromUID)
            && nextMsg.fromUID == nowMsg.fromUID
        ) {
            nextBubble = true
        }
        bgType = if (previousBubble) {
            if (nextBubble) {
                WKMsgBgType.center
            } else {
                WKMsgBgType.bottom
            }
        } else {
            if (nextBubble) {
                WKMsgBgType.top
            } else WKMsgBgType.single
        }
        return bgType
    }

    protected open fun isShowAvatar(nowMsg: WKMsg?, previousMsg: WKMsg?): Boolean {
        var isShowAvatar = false
        var nowUID = ""
        var prevUID = ""
        if (nowMsg != null && !TextUtils.isEmpty(nowMsg.fromUID)
            && nowMsg.remoteExtra.revoke == 0 && !WKContentType.isSystemMsg(nowMsg.type)
        ) {
            nowUID = nowMsg.fromUID
        }
        if (previousMsg != null && !TextUtils.isEmpty(previousMsg.fromUID)
            && previousMsg.type != WKContentType.screenshot
            && previousMsg.remoteExtra.revoke == 0 && !WKContentType.isSystemMsg(previousMsg.type)
        ) {
            prevUID = previousMsg.fromUID
        }
        if (nowUID != prevUID) {
            isShowAvatar = true
        }
        return isShowAvatar
    }

    private fun setChoose(
        baseViewHolder: BaseViewHolder,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ) {
        if (uiChatMsgItemEntity.wkMsg.flame == 1) {
            uiChatMsgItemEntity.isChoose = false
        }
        if (uiChatMsgItemEntity.isChoose) {
            var count = 0
            var i = 0
            val size = getAdapter()!!.itemCount
            while (i < size) {
                if (getAdapter()!!.data[i].isChecked) {
                    count++
                }
                i++
            }
            if (count == 100) {
                WKToastUtils.getInstance()
                    .showToastNormal(context.getString(R.string.max_choose_msg_count))
                return
            }
            uiChatMsgItemEntity.isChecked = !uiChatMsgItemEntity.isChecked
            val checkBox = baseViewHolder.getView<CheckBox>(R.id.checkBox)
            checkBox.setChecked(uiChatMsgItemEntity.isChecked, true)
            if (uiChatMsgItemEntity.isChecked) {
                count++
            } else count--
            (getAdapter() as ChatAdapter?)!!.showTitleRightText(count.toString())
        }
    }

    protected fun setFromName(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType, receivedNameTv: TextView
    ) {
        //  · 实名徽章 Phase A：作者名行容器 + 迷你蓝勾。
        // row 容器在 chat_item_base_layout.xml 中包裹 receivedNameTv + realnameBadgeIv，
        // 仅当 parent.id == R.id.receivedNameRow 时才把整个 row 作为 visibility target，
        // 旧布局 / 非 row 容器（e.g. WKTypingProvider 的 chat_typing_layout.xml:24，
        // receivedTextNameTv.parent 是 BubbleLayout contentLayout，把它 GONE
        // 会让整个 typing 指示器消失）直接退化到切 TextView 本身 ——  P0-1。
        val parentView = receivedNameTv.parent as? View
        val nameRow = parentView?.takeIf { it.id == R.id.receivedNameRow }
        val realnameBadgeIv = nameRow?.findViewById<View>(R.id.realnameBadgeIv)
        val nameVisibilityTarget: View = nameRow ?: receivedNameTv
        val bgType: WKMsgBgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        if (uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.GROUP
            || uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
            var showName: String? = ""
            receivedNameTv.tag = uiChatMsgItemEntity.wkMsg.fromUID
            if (uiChatMsgItemEntity.wkMsg.from != null && !TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.from.channelRemark)) {
                showName = uiChatMsgItemEntity.wkMsg.from.channelRemark
            }
            if (TextUtils.isEmpty(showName)) {
                if (uiChatMsgItemEntity.wkMsg.memberOfFrom != null) {
                    showName = uiChatMsgItemEntity.wkMsg.memberOfFrom.remark
                    if (TextUtils.isEmpty(showName)) {
                        showName = uiChatMsgItemEntity.wkMsg.memberOfFrom.memberRemark
                    }
                }
                if (TextUtils.isEmpty(showName) && uiChatMsgItemEntity.wkMsg.from != null) {
                    showName = uiChatMsgItemEntity.wkMsg.from.channelName
                }
                if (TextUtils.isEmpty(showName) && uiChatMsgItemEntity.wkMsg.memberOfFrom != null) {
                    showName = uiChatMsgItemEntity.wkMsg.memberOfFrom.memberName
                }
            }
            val os = getMsgOS(uiChatMsgItemEntity.wkMsg.clientMsgNO)
            if (receivedNameTv.tag is String && receivedNameTv.tag == uiChatMsgItemEntity.wkMsg.fromUID) {
                val nameText = if (uiChatMsgItemEntity.wkMsg.type == WKContentType.typing) {
                    showName ?: ""
                } else {
                    String.format("%s/%s", showName, os)
                }

                val externalSpaceName = resolveExternalSpaceSuffix(uiChatMsgItemEntity.wkMsg)
                val isBot = uiChatMsgItemEntity.wkMsg.from != null && uiChatMsgItemEntity.wkMsg.from.robot == 1
                if (isBot) {
                    val density = receivedNameTv.resources.displayMetrics.density
                    val builder = SpannableStringBuilder(nameText)
                    builder.append("  ")
                    val badgeText = "AI"
                    val badgeStart = builder.length
                    builder.append(badgeText)
                    builder.setSpan(
                        RoundedBotBadgeSpan(
                            textSize = 9f * density,
                            radius = 3f * density,
                            paddingH = 4f * density,
                            paddingV = 1.5f * density
                        ),
                        badgeStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (!externalSpaceName.isNullOrEmpty()) {
                        appendExternalSpaceSuffix(builder, externalSpaceName)
                    }
                    receivedNameTv.text = builder
                } else if (!externalSpaceName.isNullOrEmpty()) {
                    val builder = SpannableStringBuilder(nameText)
                    appendExternalSpaceSuffix(builder, externalSpaceName)
                    receivedNameTv.text = builder
                } else {
                    receivedNameTv.text = nameText
                }
            }


            if (!TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.fromUID)) {
                val colors =
                    WKBaseApplication.getInstance().context.resources.getIntArray(R.array.name_colors)
                val index =
                    abs(uiChatMsgItemEntity.wkMsg.fromUID.hashCode()) % colors.size
                receivedNameTv.setTextColor(colors[index])
            }
            if (from == WKChatIteMsgFromType.RECEIVED) {
                val showNickName = uiChatMsgItemEntity.showNickName
                if (showNickName && (bgType == WKMsgBgType.single || bgType == WKMsgBgType.top)) {
                    nameVisibilityTarget.visibility = VISIBLE
                } else nameVisibilityTarget.visibility = GONE
            } else {
                nameVisibilityTarget.visibility = GONE
            }
            //  · 实名徽章可见性：只在群聊 RECEIVED 且名字行可见时再决定。
            // tri-state 优先级（ P0-2）：
            //   1. memberOfFrom.extraMap 有显式 true/false → 直接用，不 fallback
            //      （修 memberOfFrom=false 被 from=stale-true 覆盖导致「已取消实名」
            //       用户错打勾的 bug）；
            //   2. memberOfFrom 缺失 key → fallback 到 from.remoteExtraMap（用户
            //      profile 级兜底，仅当 member 侧没有显式答案时才用）；
            //   3. 两侧都没有 → false（不渲染负向标识）。
            // 名字行 GONE 时 badge 强制 GONE（避免名字行隐藏 ImageView 残留布局高度）。
            if (realnameBadgeIv != null) {
                val verified = RealnameBadgeResolver.isVerifiedTriState(uiChatMsgItemEntity.wkMsg.memberOfFrom)
                    ?: RealnameBadgeResolver.isVerifiedTriState(uiChatMsgItemEntity.wkMsg.from)
                    ?: false
                realnameBadgeIv.visibility =
                    if (nameVisibilityTarget.visibility == VISIBLE && verified) VISIBLE else GONE
            }
        } else {
            nameVisibilityTarget.visibility = GONE
            realnameBadgeIv?.visibility = GONE
        }

    }

    /**
     *  + : resolve the viewer-relative "@SpaceName" suffix for a
     * message bubble nickname. Returns null when the message is not external
     * for the current viewer, or when required fields are missing (bubble
     * renders nickname only).
     *
     * <p>Priority order (aligned with web PR#997/#1013/#1069):
     * <ol>
     *   <li>[ExternalSourceResolver] on the message itself
     *       (msg.localExtraMap home_space_id / is_external / source_space_name
     *       → channel.remoteExtraMap fallback).</li>
     *   <li> new fallback: the sender's cached [WKChannelMember] in
     *       this group — {@code extraMap} carries home_space_id / is_external /
     *       source_space_name after  /  data-layer pass-through.
     *       This plugs the gap when messages synced before  have empty
     *       msg-level external fields but the member cache has them (or vice
     *       versa after a stale incremental sync).</li>
     * </ol>
     *
     * <p>Keeping the fallback in this wrapper (rather than inside
     * [ExternalSourceResolver]) keeps the msg-level resolver algorithm
     * untouched — we only broaden the data entry.
     */
    protected fun resolveExternalSpaceSuffix(wkMsg: WKMsg?): String? {
        if (wkMsg == null) return null
        val viewerHomeSpaceId = WKSharedPreferencesUtil.getInstance()
            .getSPWithUID("current_space_id") ?: ""
        val msgLevel = ExternalSourceResolver.resolveSourceSpaceName(wkMsg, viewerHomeSpaceId)
        if (!msgLevel.isNullOrEmpty()) return msgLevel
        return resolveFromSenderMemberCache(wkMsg, viewerHomeSpaceId)
    }

    /**
     *  · fallback data source: look up the sender's cached
     * [WKChannelMember] in the same group and feed its extras through the
     * viewer-relative resolver. Scoped to group chats (matches
     * [ExternalSourceResolver]'s guard), null-safe and try/catch-wrapped
     * so unit tests that don't bootstrap WKIM never explode.
     */
    private fun resolveFromSenderMemberCache(
        wkMsg: WKMsg,
        viewerHomeSpaceId: String,
    ): String? {
        if (wkMsg.channelType != WKChannelType.GROUP) return null
        val channelId = wkMsg.channelID ?: return null
        if (channelId.isEmpty()) return null
        val fromUid = wkMsg.fromUID ?: return null
        if (fromUid.isEmpty()) return null
        if (!wkMsg.topicID.isNullOrEmpty()) return null
        return try {
            val member = WKIM.getInstance().channelMembersManager
                .getMember(channelId, WKChannelType.GROUP, fromUid) ?: return null
            val rawExtras = member.extraMap ?: return null
            @Suppress("UNCHECKED_CAST")
            val extras = rawExtras as Map<String, Any?>
            val resolution = com.chat.base.external.ExternalViewerResolver
                .resolveFromExtras(extras, viewerHomeSpaceId)
            if (resolution.isExternal && resolution.sourceSpaceName.isNotEmpty()) {
                resolution.sourceSpaceName
            } else {
                null
            }
        } catch (ignored: Throwable) {
            null
        }
    }

    /**
     * Append " @SpaceName" to the nickname spannable using a muted gray style so
     * the Space label does not visually compete with the nickname color.
     */
    protected fun appendExternalSpaceSuffix(
        builder: SpannableStringBuilder,
        spaceName: String
    ) {
        val start = builder.length
        builder.append(" @").append(spaceName)
        builder.setSpan(
            android.text.style.ForegroundColorSpan(0xFF8B5CF6.toInt()),
            start,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun setCheckBox(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType,
        checkBox: CheckBox,
        viewContentLayout: View
    ) {
        if (uiChatMsgItemEntity.isChoose) {
            val cbAnimator: Animator =
                ObjectAnimator.ofFloat(checkBox, TRANSLATION_X, 120f)
            val animator: Animator = ObjectAnimator.ofFloat(
                viewContentLayout,
                TRANSLATION_X,
                if (from == WKChatIteMsgFromType.RECEIVED) 120f else 0f
            )
            val animatorSet = AnimatorSet()
            animatorSet.play(animator).with(cbAnimator)
            animatorSet.duration = 200
            animatorSet.interpolator = DecelerateInterpolator()
            animatorSet.start()
        } else {
            if (checkBox.isVisible) {
                val cbAnimator: Animator =
                    ObjectAnimator.ofFloat(checkBox, TRANSLATION_X, 0f)
                val animator: Animator =
                    ObjectAnimator.ofFloat(viewContentLayout, TRANSLATION_X, 0f)
                val animatorSet = AnimatorSet()
                animatorSet.duration = 250
                animatorSet.play(animator).with(cbAnimator)
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.start()
            }
        }
        checkBox.setResId(context, R.mipmap.round_check2)
        checkBox.setDrawBackground(true)
        checkBox.setHasBorder(true)
        checkBox.setBorderColor(ContextCompat.getColor(context, R.color.white))
        checkBox.setSize(24)
        checkBox.setStrokeWidth(AndroidUtilities.dp(2f))
        //            checkBox.setCheckOffset(AndroidUtilities.dp(2));
        checkBox.setColor(
            Theme.colorAccount,
            ContextCompat.getColor(context, R.color.white)
        )
        if (uiChatMsgItemEntity.wkMsg.flame == 1) checkBox.visibility = INVISIBLE
        else
            checkBox.visibility = VISIBLE
        checkBox.setChecked(uiChatMsgItemEntity.isChecked, true)
    }

    fun setAvatarLayoutParams(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType, avatarView: AvatarView
    ) {
        avatarView.setSize(40f)
        val layoutParams = avatarView.layoutParams as FrameLayout.LayoutParams
        layoutParams.bottomMargin = 0

        layoutParams.gravity =
            if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START or Gravity.TOP else Gravity.END or Gravity.TOP
//        if (from == WKChatIteMsgFromType.RECEIVED) {
//            layoutParams.leftMargin = AndroidUtilities.dp(10f)
//            layoutParams.rightMargin = AndroidUtilities.dp(10f)
//        }
        avatarView.layoutParams = layoutParams
        avatarView.setOnClickListener {
            val adapter = getAdapter() as ChatAdapter
            adapter.conversationContext.onChatAvatarClick(uiChatMsgItemEntity.wkMsg.fromUID, false)
        }
        avatarView.setOnLongClickListener {
            val adapter = getAdapter() as ChatAdapter
            adapter.conversationContext.onChatAvatarClick(uiChatMsgItemEntity.wkMsg.fromUID, true)
            true
        }
        // 控制头像是否显示
        if (uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.PERSONAL) {
            avatarView.visibility = GONE
        } else {
            if (from == WKChatIteMsgFromType.SEND) {
                avatarView.visibility = GONE
            } else avatarView.visibility =
                if (isShowAvatar(
                        uiChatMsgItemEntity.wkMsg,
                        uiChatMsgItemEntity.previousMsg
                    )
                ) VISIBLE else GONE
        }

        if (uiChatMsgItemEntity.wkMsg != null && avatarView.isVisible) {
            setAvatar(uiChatMsgItemEntity, avatarView)
        }
    }

    fun setFullLayoutParams(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType,
        fullContentLayout: LinearLayout
    ) {
        val fullContentLayoutParams = fullContentLayout.layoutParams as FrameLayout.LayoutParams
        var isBubble = false
        val list: List<Boolean>? = EndpointManager.getInstance()
            .invokes(EndpointCategory.chatShowBubble, uiChatMsgItemEntity.wkMsg.type)
        if (!list.isNullOrEmpty()) {
            for (b in list) {
                if (b) {
                    isBubble = true
                    break
                }
            }
        }
        if (uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_TEXT
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_CARD
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_VOICE
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_MULTIPLE_FORWARD
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.unknown_msg
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.WK_CONTENT_FORMAT_ERROR
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.typing
        ) {
            isBubble = true
        }
        val itemProvider = if (!uiChatMsgItemEntity.isShowPinnedMessage)
            WKMsgItemViewManager.getInstance()
                .getItemProvider(uiChatMsgItemEntity.wkMsg.type) else WKMsgItemViewManager.getInstance()
            .getPinnedItemProvider(uiChatMsgItemEntity.wkMsg.type)
        if (itemProvider == null) {
            isBubble = true
        }
        var margin = 10f
        if (isBubble) margin = 0f
        if (from == WKChatIteMsgFromType.SEND) {
            fullContentLayoutParams.gravity = Gravity.END
            fullContentLayoutParams.rightMargin = AndroidUtilities.dp(margin)
            fullContentLayoutParams.leftMargin = AndroidUtilities.dp(55f)
        } else {
            fullContentLayoutParams.gravity = Gravity.START
            if (uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.PERSONAL) {
                fullContentLayoutParams.rightMargin = AndroidUtilities.dp(55f)
                fullContentLayoutParams.leftMargin = AndroidUtilities.dp(margin)
            } else {
                fullContentLayoutParams.leftMargin = AndroidUtilities.dp(50f + margin)
                fullContentLayoutParams.rightMargin = AndroidUtilities.dp(55f)
            }
        }
        fullContentLayout.layoutParams = fullContentLayoutParams
    }

    open fun setAvatar(uiChatMsgItemEntity: WKUIChatMsgItemEntity, avatarView: AvatarView) {

        if (uiChatMsgItemEntity.wkMsg.from != null) {
            avatarView.showAvatar(uiChatMsgItemEntity.wkMsg.from)
        } else {
            WKIM.getInstance().channelManager.fetchChannelInfo(
                uiChatMsgItemEntity.wkMsg.fromUID,
                WKChannelType.PERSONAL
            )
            avatarView.showAvatar(
                uiChatMsgItemEntity.wkMsg.fromUID,
                WKChannelType.PERSONAL,
                false
            )
        }

    }

    open fun setMsgTimeAndStatus(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        parentView: View,
        fromType: WKChatIteMsgFromType,
    ) {
        val mMsg = uiChatMsgItemEntity.wkMsg
        val isPlayAnimation = uiChatMsgItemEntity.isUpdateStatus
        val msgTimeTv = parentView.findViewById<TextView>(R.id.msgTimeTv)
        val editedTv = parentView.findViewById<TextView>(R.id.editedTv)
        val statusIV = parentView.findViewById<LottieAnimationView>(R.id.statusIV)
        val pinIV = parentView.findViewById<AppCompatImageView>(R.id.pinIV)
        if (msgTimeTv == null || mMsg == null) return
        var msgTime = mMsg.timestamp
        if (mMsg.remoteExtra != null) {
            if (mMsg.remoteExtra.editedAt != 0L) {
                msgTime = mMsg.remoteExtra.editedAt
                editedTv.visibility = VISIBLE
            } else {
                editedTv.visibility = GONE
            }
        } else {
            editedTv.visibility = GONE
        }
        pinIV.visibility = if (uiChatMsgItemEntity.isPinned == 1) VISIBLE else GONE
        msgTimeTv.text = WKTimeUtils.getInstance().getMsgTimeStr(msgTime * 1000)
        val isShowNormalColor: Boolean
        var animRawRes: Int = R.raw.ticks_single
        var autoRepeat = false
        if (mMsg.type == WKContentType.WK_IMAGE || mMsg.type == WKContentType.WK_GIF || mMsg.type == WKContentType.WK_VIDEO || mMsg.type == WKContentType.WK_VECTOR_STICKER || mMsg.type == WKContentType.WK_EMOJI_STICKER || mMsg.type == WKContentType.WK_LOCATION) {
            isShowNormalColor = false
            msgTimeTv.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            isShowNormalColor = true
            msgTimeTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
        }
        pinIV.colorFilter =
            PorterDuffColorFilter(
                ContextCompat.getColor(
                    context,
                    if (isShowNormalColor) R.color.color999 else R.color.white
                ), PorterDuff.Mode.MULTIPLY
            )
        if (mMsg.remoteExtra.needUpload == 1) mMsg.status = WKSendMsgResult.send_loading
        if (fromType == WKChatIteMsgFromType.SEND) {
            if (mMsg.setting.receipt == 1 && mMsg.remoteExtra.readedCount > 0) {
                animRawRes = R.raw.ticks_double
            } else {
                when (mMsg.status) {
                    WKSendMsgResult.send_success -> {
                        animRawRes = R.raw.ticks_single
                    }

                    WKSendMsgResult.send_loading -> {
                        autoRepeat = true
                        animRawRes = R.raw.msg_sending
                    }

                    else -> {
                        animRawRes = R.raw.error
                        statusIV.setOnClickListener {

                            if (mMsg.status == WKSendMsgResult.send_success) return@setOnClickListener
                            if (!canResendMsg(mMsg.channelID, mMsg.channelType)) {
                                WKToastUtils.getInstance()
                                    .showToastNormal(context.getString(R.string.forbidden_can_not_resend))
                                return@setOnClickListener
                            }
                            var content = context.getString(R.string.str_resend_msg_tips)
                            when (mMsg.status) {
                                WKSendMsgResult.send_fail -> {
                                    content = context.getString(R.string.str_resend_msg_tips)
                                }

                                WKSendMsgResult.no_relation -> {
                                    content = context.getString(R.string.no_relation_group)
                                }

                                WKSendMsgResult.black_list -> {
                                    content =
                                        context.getString(if (mMsg.channelType == WKChannelType.GROUP) R.string.blacklist_group else R.string.blacklist_user)
                                }

                                WKSendMsgResult.not_on_white_list -> {
                                    content = context.getString(R.string.no_relation_user)
                                }
                            }
                            WKDialogUtils.getInstance().showDialog(
                                context,
                                context.getString(R.string.msg_send_fail),
                                content,
                                true,
                                "",
                                context.getString(R.string.msg_send_fail_resend),
                                0,
                                Theme.colorAccount,
                            ) { index: Int ->
                                if (index == 1) {
                                    val mMsg1 =
                                        WKMsg()
                                    mMsg1.channelID = mMsg.channelID
                                    mMsg1.channelType = mMsg.channelType
                                    mMsg1.setting = mMsg.setting
                                    mMsg1.header = mMsg.header
                                    mMsg1.type = mMsg.type
                                    mMsg1.content = mMsg.content
                                    mMsg1.baseContentMsgModel = mMsg.baseContentMsgModel
                                    mMsg1.fromUID = WKConfig.getInstance().uid
                                    WKIM.getInstance().msgManager.sendMessage(mMsg1)
                                    WKIM.getInstance().msgManager
                                        .deleteWithClientMsgNO(mMsg.clientMsgNO)
                                }
                            }
                        }
                    }
                }
            }
            statusIV.repeatCount = if (autoRepeat) LottieDrawable.INFINITE else 0
            statusIV.setAnimation(animRawRes)
            if (mMsg.status <= WKSendMsgResult.send_success || mMsg.status == WKSendMsgResult.send_loading) {
                val tintColor = if (mMsg.status <= WKSendMsgResult.send_success) {
                    ContextCompat.getColor(
                        context,
                        if (isShowNormalColor) R.color.color999 else R.color.white
                    )
                } else {
                    ContextCompat.getColor(context, R.color.color999)
                }
                statusIV.addValueCallback(
                    KeyPath("**"),
                    LottieProperty.COLOR_FILTER,
                    LottieValueCallback(SimpleColorFilter(tintColor))
                )
            } else {
                statusIV.addValueCallback(
                    KeyPath("**"),
                    LottieProperty.COLOR_FILTER,
                    LottieValueCallback(null)
                )
            }
            if (autoRepeat || isPlayAnimation) {
                statusIV.playAnimation()
            } else {
                statusIV.cancelAnimation()
                statusIV.progress = 1f
            }
        } else {
            statusIV.visibility = GONE
        }
        uiChatMsgItemEntity.isUpdateStatus = false
    }

    /**
     * 添加view的长按事件
     *
     * @param clickView 需要长按的控件
     */
    @SuppressLint("ClickableViewAccessibility")
    protected open fun addLongClick(clickView: View, uiChatMsgItemEntity: WKUIChatMsgItemEntity) {
        if (uiChatMsgItemEntity.isShowPinnedMessage) return
        val mMsgConfig: MsgConfig = getMsgConfig(uiChatMsgItemEntity.wkMsg.type)
        var isShowReaction = false
        val `object` = EndpointManager.getInstance()
            .invoke("is_show_reaction", CanReactionMenu(uiChatMsgItemEntity.wkMsg, mMsgConfig))
        if (`object` != null) {
            isShowReaction = `object` as Boolean
        }
        if (uiChatMsgItemEntity.wkMsg.flame == 1) isShowReaction = false
        val finalIsShowReaction = isShowReaction
        val location = arrayOf(FloatArray(2))
        clickView.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                location[0] = floatArrayOf(event.rawX, event.rawY)
            }
            false
        }
        clickView.setOnLongClickListener {
            EndpointManager.getInstance().invoke("stop_reaction_animation", null)
            showChatPopup(
                uiChatMsgItemEntity.wkMsg,
                clickView,
                location[0],
                finalIsShowReaction,
                getPopupList(uiChatMsgItemEntity.wkMsg)
            )
            true
        }

    }

    /**
     * 是否能撤回
     * 发送成功且在2分钟内的消息
     *
     * @param mMsg 消息
     * @return boolean
     */
    private fun canWithdraw(mMsg: WKMsg): Boolean {
        var isManager = false
        if (mMsg.channelType == WKChannelType.GROUP) {
            val member = WKIM.getInstance().channelMembersManager.getMember(
                mMsg.channelID,
                mMsg.channelType,
                WKConfig.getInstance().uid
            )
            if (member != null && member.role != WKChannelMemberRole.normal) {
                isManager = true
            }
        }
        var revokeSecond = WKConfig.getInstance().appConfig.revoke_second
        if (revokeSecond == -1 && (mMsg.fromUID == WKConfig.getInstance().uid || isManager)) {
            return true
        }
        if (revokeSecond == 0) revokeSecond = 120
        return (WKTimeUtils.getInstance().currentSeconds - mMsg.timestamp < revokeSecond
                && mMsg.fromUID == WKConfig.getInstance().uid && mMsg.status == WKSendMsgResult.send_success) || (isManager && mMsg.status == WKSendMsgResult.send_success)
    }

    open fun getMsgConfig(msgType: Int): MsgConfig {
        val mMsgConfig: MsgConfig = if (EndpointManager.getInstance()
                .invoke(EndpointCategory.msgConfig + msgType, null) != null
        ) {
            EndpointManager.getInstance()
                .invoke(EndpointCategory.msgConfig + msgType, null) as MsgConfig
        } else {
            MsgConfig(false)
        }
        return mMsgConfig
    }

    var scrimPopupWindow: ActionBarPopupWindow? = null

    protected fun getPopupList(mMsg: WKMsg): List<PopupMenuItem> {
        var isRegisterMsgPrivacyModule = false
        val obj = EndpointManager.getInstance().invoke("is_register_msg_privacy_module", null)
        if (obj != null && obj is PrivacyMessageMenu) {
            isRegisterMsgPrivacyModule = true
        }
        //防止重复添加
        val list: MutableList<PopupMenuItem> = ArrayList()
        var isAddDelete = true
        val mMsgConfig = getMsgConfig(mMsg.type)
        if (mMsgConfig.isCanWithdraw && canWithdraw(mMsg) && !isRegisterMsgPrivacyModule) {
            isAddDelete = false
            list.add(
                0,
                PopupMenuItem(context.getString(R.string.base_withdraw), R.mipmap.msg_withdraw,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {
                            var msgId = mMsg.messageID
                            if (TextUtils.isEmpty(msgId) || msgId == "0") {
                                msgId = mMsg.clientMsgNO
                            }
                            //撤回消息
                            if (!TextUtils.isEmpty(msgId)) {
                                EndpointManager.getInstance().invoke(
                                    "chat_withdraw_msg",
                                    WithdrawMsgMenu(
                                        msgId,
                                        mMsg.channelID,
                                        mMsg.clientMsgNO,
                                        mMsg.channelType
                                    )
                                )
                            }

                        }
                    })
            )
        }
        if (mMsgConfig.isCanForward && mMsg.flame == 0) {
            var index = 0
            if (list.isNotEmpty()) {
                index = 1
            }
            list.add(
                index,
                PopupMenuItem(context.getString(R.string.base_forward), R.mipmap.msg_forward,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {

                            var mMessageContent =
                                mMsg.baseContentMsgModel
                            if (mMsg.remoteExtra != null && mMsg.remoteExtra.contentEditMsgModel != null) {
                                mMessageContent = mMsg.remoteExtra.contentEditMsgModel
                            }
                            val chooseChatMenu =
                                ChooseChatMenu(
                                    ChatChooseContacts { channelList: List<WKChannel>? ->
                                        if (!channelList.isNullOrEmpty()) {
                                            for (mChannel in channelList) {
                                                var msgContent =
                                                    mMsg.baseContentMsgModel
                                                if (mMsg.remoteExtra != null && mMsg.remoteExtra.contentEditMsgModel != null) {
                                                    msgContent =
                                                        mMsg.remoteExtra.contentEditMsgModel
                                                }
                                                msgContent.mentionAll = 0
                                                msgContent.mentionInfo = null
                                                val option = WKSendOptions()
                                                option.setting.receipt = mChannel.receipt
                                                WKIM.getInstance().msgManager.sendWithOptions(
                                                    msgContent,
                                                    mChannel, option
                                                )
                                            }
                                            val viewGroup =
                                                (context as Activity).findViewById<View>(android.R.id.content)
                                                    .rootView as ViewGroup
                                            Snackbar.make(
                                                viewGroup,
                                                context.getString(R.string.str_forward),
                                                1000
                                            )
                                                .setAction(
                                                    ""
                                                ) { }
                                                .show()
                                        }
                                    },
                                    mMessageContent
                                )
                            EndpointManager.getInstance()
                                .invoke(EndpointSID.showChooseChatView, chooseChatMenu)
                        }
                    })
            )
        }
        val menus = EndpointManager.getInstance()
            .invokes<ChatItemPopupMenu>(EndpointCategory.wkChatPopupItem, mMsg)

        if (menus != null && menus.isNotEmpty() && mMsg.flame == 0) {
            for (menu in menus) {
                val popupMenu =
                    PopupMenuItem(menu.text, menu.imageResource,
                        object : PopupMenuItem.IClick {
                            override fun onClick() {
                                menu.iPopupItemClick.onClick(
                                    mMsg,
                                    (Objects.requireNonNull(
                                        getAdapter()
                                    ) as ChatAdapter).conversationContext
                                )
                            }
                        })
                popupMenu.subText = menu.subText
                popupMenu.tag = menu.tag
                if (menu != null) list.add(
                    popupMenu
                )
            }
        }
        var addIndex = list.size
        val result = EndpointManager.getInstance().invoke("auto_delete", mMsg)
        if (result != null) {
            addIndex = list.size - 1
        }
        if (mMsgConfig.isCanMultipleChoice && mMsg.flame == 0) {
            list.add(
                addIndex,
                PopupMenuItem(
                    context.getString(R.string.multiple_choice),
                    R.mipmap.msg_select,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {
                            var i = 0
                            val size = getAdapter()!!.data.size
                            while (i < size) {
                                getAdapter()!!.data[i].isChoose = true
                                if (getAdapter()!!.data[i].wkMsg.clientMsgNO == mMsg.clientMsgNO) {
                                    getAdapter()!!.data[i].isChecked = true
                                }
                                getAdapter()!!.notifyItemChanged(
                                    i,
                                    getAdapter()!!.data[i]
                                )
                                i++
                            }

                            //    getAdapter()!!.notifyItemRangeChanged(0, getAdapter()!!.data.size)
                            (Objects.requireNonNull(
                                getAdapter()
                            ) as ChatAdapter).showTitleRightText("1")
                            (getAdapter() as ChatAdapter?)!!.showMultipleChoice()

                        }
                    })
            )
            addIndex++
        }
        //发送成功的消息才能回复
        if (mMsgConfig.isCanReply && mMsg.status == WKSendMsgResult.send_success && mMsg.flame == 0) {
            list.add(
                addIndex,
                PopupMenuItem(context.getString(R.string.msg_reply), R.mipmap.msg_reply,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {
                            (Objects.requireNonNull(
                                getAdapter()
                            ) as ChatAdapter).replyMsg(mMsg)
                        }
                    })
            )
            addIndex++
        }
        //撤回和删除不能同时存在
        if (isAddDelete && mMsg.flame == 0 && result == null) {
            list.add(
                addIndex,
                PopupMenuItem(
                    context.getString(R.string.base_delete),
                    R.mipmap.msg_delete, object : PopupMenuItem.IClick {
                        override fun onClick() {
                            var singleDelete = false
                            if (mMsg.status != WKSendMsgResult.send_success) {
                                singleDelete = true
                            } else {
                                if (mMsg.channelType == WKChannelType.GROUP) {
                                    val loginUID = WKConfig.getInstance().uid
                                    val member = WKIM.getInstance().channelMembersManager.getMember(
                                        mMsg.channelID,
                                        mMsg.channelType,
                                        loginUID
                                    )
                                    if (member == null || (member.role == WKChannelMemberRole.normal && (!TextUtils.isEmpty(
                                            mMsg.fromUID
                                        ) && mMsg.fromUID != loginUID)
                                                )
                                    ) {
                                        singleDelete = true
                                    }
                                }
                            }
                            if (obj != null && obj is PrivacyMessageMenu && !singleDelete) {
                                val checkBoxText: String
                                if (mMsg.channelType == WKChannelType.GROUP) {
                                    checkBoxText =
                                        context.getString(R.string.str_delete_message_for_all)
                                } else {
                                    var showName = ""
                                    val channel = WKIM.getInstance().channelManager.getChannel(
                                        mMsg.channelID,
                                        mMsg.channelType
                                    )
                                    if (channel != null) {
                                        showName =
                                            if (TextUtils.isEmpty(channel.channelRemark)) channel.channelName else channel.channelRemark
                                    }
                                    checkBoxText = String.format(
                                        context.getString(R.string.str_delete_message_also_to),
                                        showName
                                    )
                                }
                                WKDialogUtils.getInstance().showCheckBoxDialog(
                                    context,
                                    context.getString(R.string.str_delete_message),
                                    context.getString(R.string.str_delete_message_tip),
                                    checkBoxText,
                                    true,
                                    "",
                                    context.getString(R.string.base_delete),
                                    0,
                                    ContextCompat.getColor(context, R.color.red)
                                ) { index, isChecked ->
                                    if (index == 1) {
                                        if (isChecked) {
                                            obj.iClick.onDelete(mMsg)
                                        } else {
                                            EndpointManager.getInstance()
                                                .invoke("str_delete_msg", mMsg)
                                            WKIM.getInstance().msgManager.deleteWithClientMsgNO(
                                                mMsg.clientMsgNO
                                            )
                                        }
                                    }
                                }
                            } else {
                                WKDialogUtils.getInstance().showDialog(
                                    context,
                                    context.getString(R.string.str_delete_message),
                                    context.getString(R.string.str_delete_message_tip),
                                    true,
                                    "",
                                    context.getString(R.string.base_delete),
                                    0,
                                    ContextCompat.getColor(context, R.color.red)
                                ) { index ->
                                    if (index == 1) {
                                        EndpointManager.getInstance().invoke("str_delete_msg", mMsg)
                                        WKIM.getInstance().msgManager.deleteWithClientMsgNO(mMsg.clientMsgNO)
                                    }
                                }

                            }
                        }
                    })
            )
        }
        return list
    }

    private val rect = RectF()

    @SuppressLint("ClickableViewAccessibility")
    protected fun showChatPopup(
        mMsg: WKMsg,
        v: View,
        local: FloatArray,
        isShowReaction: Boolean,
        list: List<PopupMenuItem>
    ) {
        val mMsgConfig: MsgConfig = getMsgConfig(mMsg.type)
        if (mMsg.flame == 1 && (!mMsgConfig.isCanWithdraw || !canWithdraw(mMsg))) {
            return
        }

        val scrimPopupContainerLayout: ChatScrimPopupContainerLayout =
            object : ChatScrimPopupContainerLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0 && scrimPopupWindow != null) {
                        scrimPopupWindow!!.dismiss(true)
                    }
                    return super.dispatchKeyEvent(event)
                }

                override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                    val b = super.dispatchTouchEvent(ev)
                    if (ev.action == MotionEvent.ACTION_DOWN && !b && scrimPopupWindow != null) {
                        scrimPopupWindow!!.dismiss(true)
                    }
                    return b
                }
            }
        scrimPopupContainerLayout.setOnTouchListener(object : OnTouchListener {
            private val pos = IntArray(2)
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (scrimPopupWindow != null && scrimPopupWindow!!.isShowing) {
                        val contentView = scrimPopupWindow!!.contentView
                        contentView.getLocationInWindow(pos)
                        rect.set(
                            pos[0].toFloat(),
                            pos[1].toFloat(),
                            (pos[0] + contentView.measuredWidth).toFloat(),
                            (pos[1] + contentView.measuredHeight).toFloat()
                        )
                        if (!rect.contains(event.x.toInt().toFloat(), event.y.toInt().toFloat())) {
                            scrimPopupWindow!!.dismiss(true)
                        }
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    scrimPopupWindow!!.dismiss(true)
                }
                return false
            }
        })
        val popupLayout = ActionBarPopupWindowLayout(
            context,
            R.mipmap.popup_fixed_alert,
            ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK
        )
        val `object` = EndpointManager.getInstance().invoke("show_receipt", mMsg)
        if (`object` != null) {
            val isShowReceipt = `object` as Boolean
            if (isShowReceipt) {
                val str = String.format(
                    context.getString(R.string.msg_read_count),
                    mMsg.remoteExtra.readedCount
                )
                val subItem1 = ActionBarMenuSubItem(context, false, false, false)
                subItem1.setTextAndIcon(str, R.mipmap.msg_seen)
                subItem1.setTag(R.id.width_tag, 240)
                subItem1.setMultiline()
                subItem1.setRightIcon(R.mipmap.msg_arrowright)
                popupLayout.addView(subItem1)

                subItem1.setOnClickListener {
                    scrimPopupWindow!!.dismiss()
                    EndpointManager.getInstance().invoke("chat_activity_touch", null)
                    EndpointManager.getInstance().invoke(
                        "show_msg_read_detail",
                        ReadMsgDetailMenu(
                            mMsg.messageID,
                            (Objects.requireNonNull(
                                getAdapter()
                            ) as ChatAdapter).conversationContext
                        )
                    )
                }
                val subItem2 = ActionBarMenuSubItem(context, false, false, false)
                subItem2.setItemHeight(10)
                subItem2.setBackgroundColor(ContextCompat.getColor(context, R.color.homeColor))
                popupLayout.addView(
                    subItem2,
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 10)
                )
            }
        }
        var i = 0
        val size = list.size
        while (i < size) {
            val item = list[i]
            val subItem = ActionBarMenuSubItem(context, false, false, i == list.size - 1)
            subItem.setTextAndIcon(item.text, item.iconResourceID)
            subItem.setTag(R.id.width_tag, 240)
            subItem.setMultiline()
            if (!TextUtils.isEmpty(item.subText)) {
                subItem.setSubtext(item.subText)
            }
            if (!TextUtils.isEmpty(item.tag) && item.tag == "auto_delete") {
                EndpointManager.getInstance().invoke("chat_popup_item", subItem)
            }
            subItem.setOnClickListener {
                scrimPopupWindow?.dismiss()
                item.iClick.onClick()
                EndpointManager.getInstance().invoke("chat_activity_touch", null)
            }
            popupLayout.addView(subItem)
            i++
        }
        popupLayout.backgroundColor = ContextCompat.getColor(context, R.color.screen_bg)
        popupLayout.minimumWidth = AndroidUtilities.dp(200f)
        var reactionsLayout: ReactionsContainerLayout? = null
        val pad = 22
        val sPad = 24
        if (isShowReaction) {
            reactionsLayout = ReactionsContainerLayout(context)
            reactionsLayout.setPadding(
                AndroidUtilities.dp(4f) + if (AndroidUtilities.isRTL) 0 else sPad,
                AndroidUtilities.dp(4f),
                AndroidUtilities.dp(4f) + if (AndroidUtilities.isRTL) sPad else 0,
                AndroidUtilities.dp(pad.toFloat())
            )
            reactionsLayout.setDelegate(ReactionsContainerDelegate { _: View?, reaction: String?, _: Boolean, location: IntArray? ->
                scrimPopupWindow?.dismiss(true)
                EndpointManager.getInstance().invoke(
                    "wk_msg_reaction",
                    MsgReactionMenu(mMsg, reaction, getAdapter() as ChatAdapter?, location)
                )
            })
        }

//        Rect backgroundPaddings = new Rect();
//        Drawable shadowDrawable2 = ContextCompat.getDrawable(getContext(), R.mipmap.popup_fixed_alert).mutate();
//        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.layoutColor), PorterDuff.Mode.MULTIPLY));
//        shadowDrawable2.getPadding(backgroundPaddings);
//        scrimPopupContainerLayout.setBackground(shadowDrawable2);
        if (isShowReaction) {
            val params = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                52 + pad,
                Gravity.START,
                0,
                0,
                0,
                0
            )
            scrimPopupContainerLayout.addView(reactionsLayout, params)
            scrimPopupContainerLayout.setReactionsLayout(reactionsLayout)
            reactionsLayout?.setTransitionProgress(0f)
        }
        scrimPopupContainerLayout.clipChildren = false
        val fl = FrameLayout(context)
        //        fl.setBackground(shadowDrawable2);
        fl.addView(
            popupLayout,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
        )
        scrimPopupContainerLayout.addView(
            fl,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START,
                16,
                if (isShowReaction) -18 else 0,
                36,
                0
            )
        )
        scrimPopupContainerLayout.applyViewBottom(fl)
        scrimPopupContainerLayout.setPopupWindowLayout(popupLayout)
        if (popupLayout.swipeBack != null) {
            val finalReactionsLayout = reactionsLayout
            if (isShowReaction) {
                popupLayout.swipeBack!!
                    .addOnSwipeBackProgressListener { _: PopupSwipeBackLayout?, toProgress: Float, progress: Float ->
                        if (toProgress == 0f) {
                            finalReactionsLayout?.startEnterAnimation()
                        } else if (toProgress == 1f) finalReactionsLayout!!.alpha = 1f - progress
                    }
            }
        }
        scrimPopupWindow = object : ActionBarPopupWindow(
            scrimPopupContainerLayout,
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT
        ) {
            override fun dismiss() {
                super.dismiss()
                if (scrimPopupWindow !== this) {
                    return
                }
                scrimPopupWindow = null
            }
        }
        scrimPopupWindow!!.setPauseNotifications(true)
        scrimPopupWindow!!.setDismissAnimationDuration(220)
        scrimPopupWindow!!.isOutsideTouchable = true
        scrimPopupWindow!!.isClippingEnabled = true
        scrimPopupWindow!!.animationStyle = R.style.PopupContextAnimation
        scrimPopupWindow!!.isFocusable = true
        scrimPopupContainerLayout.measure(
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(
                AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST
            )
        )
        scrimPopupWindow!!.inputMethodMode = ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED
        scrimPopupWindow!!.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        scrimPopupWindow!!.contentView.isFocusableInTouchMode = true
        popupLayout.setFitItems(false)
        val x = local[0]
        val y = local[1]
        val adapter = getAdapter() as ChatAdapter
        val recyclerViewLayout = adapter.conversationContext.recyclerViewLayout
        var popupX =
            v.left + x.toInt() - scrimPopupContainerLayout.measuredWidth + -AndroidUtilities.dp(28f)
        if (popupX < AndroidUtilities.dp(6f)) {
            popupX = AndroidUtilities.dp(6f)
        } else if (popupX > recyclerViewLayout.measuredWidth - AndroidUtilities.dp(
                6f
            ) - scrimPopupContainerLayout.measuredWidth
        ) {
            popupX =
                recyclerViewLayout.measuredWidth - AndroidUtilities.dp(6f) - scrimPopupContainerLayout.measuredWidth
        }
        var totalHeight = AndroidUtilities.getScreenHeight()
        val height = scrimPopupContainerLayout.measuredHeight + AndroidUtilities.dp(48f)
        val keyboardHeight = WKConstants.getKeyboardHeight()
        if (keyboardHeight > AndroidUtilities.dp(20f)) {
            totalHeight += keyboardHeight
        }
        var popupY: Int
        if (height < totalHeight) {
            popupY = (recyclerViewLayout.y + v.top + y).toInt()
            if (height > AndroidUtilities.dp(240f)) {
                popupY += AndroidUtilities.dp(240f) - height / 10 * 7
            }
            if (popupY < recyclerViewLayout.y + AndroidUtilities.dp(24f)) {
                popupY = (recyclerViewLayout.y + AndroidUtilities.dp(24f)).toInt()
            } else if (popupY > totalHeight - height - AndroidUtilities.dp(8f)) {
                popupY = totalHeight - height - AndroidUtilities.dp(8f)
            }
        } else {
            popupY = 0
        }
        val finalPopupX = popupX
        val finalPopupY = popupY
        val finalReactionsLayout1 = reactionsLayout
        val showMenu = Runnable {
            if (scrimPopupWindow == null) {
                return@Runnable
            }
            scrimPopupWindow!!.showAtLocation(
                recyclerViewLayout, Gravity.START or Gravity.TOP, finalPopupX, finalPopupY
            )
            if (isShowReaction) finalReactionsLayout1!!.startEnterAnimation()
        }
        showMenu.run()
    }


    // 消息item显示最大宽度
    protected open fun getViewWidth(
        fromType: WKChatIteMsgFromType,
        msgItemEntity: WKUIChatMsgItemEntity
    ): Int {
        //  · phone→unfold 右侧消息宽度不自适应修复：
        // 旧实现读 AndroidUtilities.getScreenWidth()（device displayMetrics.widthPixels），
        // 在 Activity Embedding 下返回整机宽度（两栏总和）而非 ChatActivity 所在 pane 的
        // 可见宽度。phone 启动时 pane = 整机（窄）；unfold 后 pane 变成 secondary（约 480dp），
        // 整机宽度却变成 ~800dp。re-bind 后 layoutParams.width 基于整机宽度计算→溢出 pane，
        // 与 phone 态保留的窄值形成"宽度不跟随 pane 变化"观感。
        //
        // 改成 read-at-use 的 PaneMetrics.widthPx(context)：Embedding 关闭时 fallback 到
        // displayMetrics.widthPixels，与旧行为一致；Embedding 开启时返回当前 Activity
        // 可见窗口宽度，正是我们需要的自适应值。
        //
        // 横屏分支保留：Embedding 下 pane bounds 的 width 已经是当前方向的"视觉水平"，
        // 无需再在 landscape 用 screenHeight 兜 hack；但保留原分支以避免 configuration
        // race 时 PaneMetrics 尚未拿到最新 metrics（极端 fallback）。
        val paneWidth = PaneMetrics.widthPx(context)
        val maxWidth =
            if (AndroidUtilities.isPORTRAIT) paneWidth
            else maxOf(paneWidth, context.resources.displayMetrics.heightPixels)
        val width: Int
        val checkBoxMargin = 34
        var flameWidth = 0
        var pinnedWidth = 0
        if ((msgItemEntity.wkMsg.flame == 1 && msgItemEntity.wkMsg.flameSecond > 0) && msgItemEntity.wkMsg.type != WKContentType.WK_IMAGE
            && msgItemEntity.wkMsg.type != WKContentType.WK_VIDEO
        ) {
            flameWidth = 30
        }
        if (msgItemEntity.isShowPinnedMessage) {
            pinnedWidth = 35
        }
        width =
            if (fromType == WKChatIteMsgFromType.SEND || msgItemEntity.wkMsg.channelType == WKChannelType.PERSONAL) {
                maxWidth - AndroidUtilities.dp((70 + checkBoxMargin).toFloat() + flameWidth + pinnedWidth)
            } else {
                maxWidth - AndroidUtilities.dp((70 + 40 + checkBoxMargin).toFloat() + flameWidth + pinnedWidth)
            }
        return width
    }

    protected open fun getShowContent(contentJson: String): String? {
        return StringUtils.getShowContent(context, contentJson)
    }

    private fun isAddFlameView(msgItemEntity: WKUIChatMsgItemEntity): Boolean {
        return !(msgItemEntity.wkMsg.flame == 0 || WKContentType.isSystemMsg(msgItemEntity.wkMsg.type) || WKContentType.isLocalMsg(
            msgItemEntity.wkMsg.type
        ) || (msgItemEntity.wkMsg.flame == 1 && msgItemEntity.wkMsg.flameSecond == 0)
                || msgItemEntity.wkMsg.type == WKContentType.WK_IMAGE
                || msgItemEntity.wkMsg.type == WKContentType.WK_VIDEO)
    }

    private fun canResendMsg(channelID: String, channelType: Byte): Boolean {
        if (channelType == WKChannelType.PERSONAL) return true
        val mChannel =
            WKIM.getInstance().channelManager.getChannel(channelID, channelType)
        val member = WKIM.getInstance().channelMembersManager.getMember(
            channelID,
            channelType,
            WKConfig.getInstance().uid
        )
        if (member != null) {
            if (mChannel != null && mChannel.forbidden == 1) {
                if (member.role == WKChannelMemberRole.admin) {
                    return true
                }
                if (member.role == WKChannelMemberRole.manager) {
                    return member.forbiddenExpirationTime <= 0L
                }
                return false
            }
            if (member.forbiddenExpirationTime > 0L) {
                return false
            }
        }
        return true
    }

    fun setItemPadding(position: Int, viewGroupLayout: ChatItemView) {
        val adapter = getAdapter() ?: return
        val dataIndex = position - adapter.headerLayoutCount
        if (dataIndex < 0 || dataIndex >= adapter.data.size) return
        var top: Int
        var bottom: Int
        val currentFromUID: String? = adapter.data[dataIndex].wkMsg.fromUID
        var nextFromUID: String? = ""
        var previousFromUID: String? = ""
        if (dataIndex + 1 <= adapter.data.size - 1) {
            nextFromUID = adapter.data[dataIndex + 1].wkMsg.fromUID
        }
        if (dataIndex - 1 >= 0) {
            previousFromUID = adapter.data[dataIndex - 1].wkMsg.fromUID
        }
        if (TextUtils.isEmpty(currentFromUID)) {
            top = AndroidUtilities.dp(4f)
            bottom = AndroidUtilities.dp(4f)
        } else {
            top = if (!TextUtils.isEmpty(previousFromUID) && previousFromUID == currentFromUID) {
                AndroidUtilities.dp(1f)
            } else {
                AndroidUtilities.dp(4f)
            }
            bottom = if (!TextUtils.isEmpty(nextFromUID) && nextFromUID == currentFromUID) {
                AndroidUtilities.dp(1f)
            } else {
                AndroidUtilities.dp(4f)
            }
        }
        if (dataIndex == adapter.data.size - 1) {
            bottom = AndroidUtilities.dp(10f)
        }
        if (dataIndex == 0) {
            top = AndroidUtilities.dp(10f)
        }
        viewGroupLayout.setPadding(0, top, 0, bottom)
    }

    private fun getMsgOS(clientMsgNo: String): String {
        return if (clientMsgNo.endsWith("1")) {
            "Android"
        } else if (clientMsgNo.endsWith("2")) {
            "iOS"
        } else if (clientMsgNo.endsWith("3")) {
            "Web"
        } else if (clientMsgNo.endsWith("5")) {
            "Flutter"
        } else {
            "PC"
        }
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        super.onViewAttachedToWindow(holder)
        val chatAdapter = getAdapter() as? ChatAdapter ?: return
        val dataIndex = holder.bindingAdapterPosition - chatAdapter.headerLayoutCount
        if (dataIndex < 0 || dataIndex >= chatAdapter.data.size) return
        chatAdapter.conversationContext.onMsgViewed(
            chatAdapter.data[dataIndex].wkMsg,
            dataIndex
        )
    }

}