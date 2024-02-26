package com.chat.base.msgitem

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.View.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.R
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.*
import com.chat.base.entity.PopupMenuItem
import com.chat.base.msg.ChatAdapter
import com.chat.base.ui.Theme
import com.chat.base.ui.components.*
import com.chat.base.ui.components.ActionBarPopupWindow.ActionBarPopupWindowLayout
import com.chat.base.ui.components.ReactionsContainerLayout.ReactionsContainerDelegate
import com.chat.base.utils.*
import com.chat.base.views.ChatItemView
import com.google.android.material.snackbar.Snackbar
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKMsgSetting
import com.xinbida.wukongim.message.type.WKSendMsgResult
import com.xinbida.wukongim.msgmodel.WKVoiceContent
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView
import java.util.*
import kotlin.math.abs
import kotlin.math.max

abstract class WKChatBaseProvider : BaseItemProvider<WKUIChatMsgItemEntity>() {

    override val layoutId: Int
        get() = R.layout.chat_item_base_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity, payloads: List<Any>) {
        super.convert(helper, item, payloads)
        val msgItemEntity = payloads[0] as WKUIChatMsgItemEntity
        if (msgItemEntity.isRefreshReaction && helper.getViewOrNull<AvatarView>(R.id.avatarView) != null) {
            msgItemEntity.isRefreshReaction = false
            val from = getMsgFromType(msgItemEntity.wkMsg)
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
    }

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        showData(helper, item)
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

            // 提示本条消息
            if (msgItemEntity.isShowTips) {
                val animator =
                    ObjectAnimator.ofFloat(viewGroupLayout, "translationX", 0f, 50f, -50f, 0f)
                animator.duration = 800
                animator.repeatCount = 1
                animator.start()
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        msgItemEntity.isShowTips = false
                        getAdapter()!!.notifyItemChanged(baseViewHolder.bindingAdapterPosition)
                    }
                })
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
                            msgItemEntity.wkMsg.baseContentMsgModel as WKVoiceContent
                        max(voiceContent.timeTrad, msgItemEntity.wkMsg.flameSecond)
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
            setData(baseViewHolder.bindingAdapterPosition, baseView, msgItemEntity, from)
            if (baseViewHolder.getViewOrNull<View>(R.id.receivedNameTv) != null && msgItemEntity.wkMsg.type != WKContentType.WK_TEXT && msgItemEntity.wkMsg.type != WKContentType.typing && msgItemEntity.wkMsg.type != WKContentType.richText) {
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

    protected open fun isShowAvatar(nowMsg: WKMsg?, nextMsg: WKMsg?): Boolean {
        var isShowAvatar = false
        var nowUID = ""
        var nextUID = ""
        if (nowMsg != null && !TextUtils.isEmpty(nowMsg.fromUID)
            && nowMsg.remoteExtra.revoke == 0 && !WKContentType.isSystemMsg(nowMsg.type)
        ) {
            nowUID = nowMsg.fromUID
        }
        if (nextMsg != null && !TextUtils.isEmpty(nextMsg.fromUID)
            && nextMsg.remoteExtra.revoke == 0 && !WKContentType.isSystemMsg(nextMsg.type)
        ) {
            nextUID = nextMsg.fromUID
        }
        if (nowUID != nextUID) {
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
        val bgType: WKMsgBgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        if (uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.GROUP) {
            var showName: String? = ""
            receivedNameTv.tag = uiChatMsgItemEntity.wkMsg.fromUID
            if (uiChatMsgItemEntity.wkMsg.from != null && !TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.from.channelRemark)) {
                showName = uiChatMsgItemEntity.wkMsg.from.channelRemark
            }
            if (TextUtils.isEmpty(showName)) {
                if (uiChatMsgItemEntity.wkMsg.memberOfFrom != null) {
                    showName = uiChatMsgItemEntity.wkMsg.memberOfFrom.remark
                    if (TextUtils.isEmpty(showName))
                        showName =
                            if (TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.memberOfFrom.memberRemark)) uiChatMsgItemEntity.wkMsg.memberOfFrom.memberName else uiChatMsgItemEntity.wkMsg.memberOfFrom.memberRemark
                } else {
                    if (uiChatMsgItemEntity.wkMsg.from != null) {
                        showName = uiChatMsgItemEntity.wkMsg.from.channelName
                    }
                }
            }
            val os = getMsgFromOS(uiChatMsgItemEntity.wkMsg.clientMsgNO)
            if (receivedNameTv.tag is String && receivedNameTv.tag == uiChatMsgItemEntity.wkMsg.fromUID) {
                if (uiChatMsgItemEntity.wkMsg.type == WKContentType.typing) {
                    receivedNameTv.text = showName
                } else {
                    receivedNameTv.text = String.format("%s/%s", showName, os)
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
                    receivedNameTv.visibility = VISIBLE
                } else receivedNameTv.visibility = GONE
            } else {
                receivedNameTv.visibility = GONE
            }
        } else receivedNameTv.visibility = GONE

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
            if (checkBox.visibility == VISIBLE) {
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
        if (uiChatMsgItemEntity.wkMsg.reactionList != null && uiChatMsgItemEntity.wkMsg.reactionList.size > 0) {
            // 向下的距离是回应数据的高度+阴影高度-回应向上的距离
            layoutParams.bottomMargin = AndroidUtilities.dp(24f)
        } else layoutParams.bottomMargin = 0

        layoutParams.gravity =
            if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START or Gravity.BOTTOM else Gravity.END or Gravity.BOTTOM
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
                        uiChatMsgItemEntity.nextMsg
                    )
                ) VISIBLE else GONE
        }

        if (uiChatMsgItemEntity.wkMsg != null && avatarView.visibility == VISIBLE) {
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
            || uiChatMsgItemEntity.wkMsg.type == WKContentType.typing
        ) {
            isBubble = true
        }
        val itemProvider =
            WKMsgItemViewManager.getInstance().getItemProvider(uiChatMsgItemEntity.wkMsg.type)
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

    open fun setMsgTimeAndStatus(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        parentView: View,
        fromType: WKChatIteMsgFromType,
    ) {
        val mMsg = uiChatMsgItemEntity.wkMsg
        val isPlayAnimation = uiChatMsgItemEntity.isUpdateStatus
        val msgTimeTv = parentView.findViewById<TextView>(R.id.msgTimeTv)
        val editedTv = parentView.findViewById<TextView>(R.id.editedTv)
        val statusIV = parentView.findViewById<RLottieImageView>(R.id.statusIV)
        if (msgTimeTv == null || mMsg == null) return
        var msgTime = mMsg.timestamp
        if (mMsg.remoteExtra != null && mMsg.remoteExtra.editedAt != 0L) {
            msgTime = mMsg.remoteExtra.editedAt
            editedTv.visibility = VISIBLE
        } else {
            editedTv.visibility = GONE
        }
        val timeSpace = WKTimeUtils.getInstance().getTimeSpace(msgTime * 1000)
        val time = WKTimeUtils.getInstance().time2HourStr(msgTime * 1000)
        if (!WKTimeUtils.getInstance().is24Hour) msgTimeTv.text =
            String.format("%s %s", timeSpace, time) else msgTimeTv.text =
            String.format("%s", time)
        val isShowNormalColor: Boolean
        val drawable: RLottieDrawable
        var autoRepeat = false
        if (mMsg.type == WKContentType.WK_IMAGE || mMsg.type == WKContentType.WK_GIF || mMsg.type == WKContentType.WK_VIDEO || mMsg.type == WKContentType.WK_VECTOR_STICKER || mMsg.type == WKContentType.WK_EMOJI_STICKER || mMsg.type == WKContentType.WK_LOCATION) {
            isShowNormalColor = false
            msgTimeTv.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            isShowNormalColor = true
            msgTimeTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
        }
        if (mMsg.remoteExtra.needUpload == 1) mMsg.status = WKSendMsgResult.send_loading
        if (fromType == WKChatIteMsgFromType.SEND) {
            if (mMsg.setting.receipt == 1 && mMsg.remoteExtra.readedCount > 0) {
                drawable = RLottieDrawable(
                    context,
                    R.raw.ticks_double,
                    "ticks_double",
                    AndroidUtilities.dp(18f),
                    AndroidUtilities.dp(18f)
                )
            } else {
                when (mMsg.status) {
                    WKSendMsgResult.send_success -> {
                        drawable = RLottieDrawable(
                            context,
                            R.raw.ticks_single,
                            "ticks_single",
                            AndroidUtilities.dp(18f),
                            AndroidUtilities.dp(18f)
                        )
                    }

                    WKSendMsgResult.send_loading -> {
                        autoRepeat = true
                        drawable = RLottieDrawable(
                            context,
                            R.raw.msg_sending,
                            "msg_sending",
                            AndroidUtilities.dp(18f),
                            AndroidUtilities.dp(18f)
                        )
                    }

                    else -> {
                        drawable = RLottieDrawable(
                            context,
                            R.raw.error,
                            "error",
                            AndroidUtilities.dp(18f),
                            AndroidUtilities.dp(18f)
                        )
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
            if (mMsg.status <= WKSendMsgResult.send_success) {
                statusIV.colorFilter =
                    PorterDuffColorFilter(
                        ContextCompat.getColor(
                            context,
                            if (isShowNormalColor) R.color.color999 else R.color.white
                        ), PorterDuff.Mode.MULTIPLY
                    )
            } else {
                statusIV.colorFilter =
                    PorterDuffColorFilter(
                        ContextCompat.getColor(
                            context, R.color.white
                        ), PorterDuff.Mode.MULTIPLY
                    )
            }
            statusIV.setAutoRepeat(autoRepeat)
            statusIV.setAnimation(drawable)
            if (autoRepeat || isPlayAnimation) {
                statusIV.playAnimation()
            } else drawable.currentFrame = drawable.framesCount - 1
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
    protected open fun addLongClick(clickView: View, mMsg: WKMsg) {
        val mMsgConfig: MsgConfig = getMsgConfig(mMsg.type)
        var isShowReaction = false
        val `object` = EndpointManager.getInstance()
            .invoke("is_show_reaction", CanReactionMenu(mMsg, mMsgConfig))
        if (`object` != null) {
            isShowReaction = `object` as Boolean
        }
        if (mMsg.flame == 1) isShowReaction = false
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
            showChatPopup(mMsg, clickView, location[0], finalIsShowReaction, getPopupList(mMsg))
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
            MsgConfig(
                false,
                false,
                false,
                false,
                false
            )
        }
        return mMsgConfig
    }

    var scrimPopupWindow: ActionBarPopupWindow? = null

    protected fun getPopupList(mMsg: WKMsg): List<PopupMenuItem> {
        //防止重复添加
        val list: MutableList<PopupMenuItem> = ArrayList()
        var isAddDelete = true
        val mMsgConfig = getMsgConfig(mMsg.type)
        if (mMsgConfig.isCanWithdraw && canWithdraw(mMsg)) {
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
            if (list.size > 0) {
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
                                                val setting = WKMsgSetting()
                                                setting.receipt = mChannel.receipt
//                                                setting.signal = 0
                                                WKIM.getInstance().msgManager.sendMessage(
                                                    msgContent,
                                                    setting,
                                                    mChannel.channelID,
                                                    mChannel.channelType
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

        if (menus.size > 0 && mMsg.flame == 0) {
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
                                i++
                            }
                            getAdapter()!!.notifyItemRangeChanged(0, getAdapter()!!.data.size)
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
                            EndpointManager.getInstance().invoke("str_delete_msg", mMsg)
                            WKIM.getInstance().msgManager.deleteWithClientMsgNO(mMsg.clientMsgNO)
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
                Log.e("发布了", "-->")
                EndpointManager.getInstance().invoke("chat_popup_item", subItem)
            }
            subItem.setOnClickListener {
                scrimPopupWindow!!.dismiss()
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
                scrimPopupWindow!!.dismiss(true)
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
            reactionsLayout!!.setTransitionProgress(0f)
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
                            finalReactionsLayout!!.startEnterAnimation()
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
        val maxWidth =
            if (AndroidUtilities.isPORTRAIT) AndroidUtilities.getScreenWidth() else AndroidUtilities.getScreenHeight()
        val width: Int
        val checkBoxMargin = 30
        var flameWidth = 0
        if ((msgItemEntity.wkMsg.flame == 1 && msgItemEntity.wkMsg.flameSecond > 0) && msgItemEntity.wkMsg.type != WKContentType.WK_IMAGE
            && msgItemEntity.wkMsg.type != WKContentType.WK_VIDEO
        ) {
            flameWidth = 30
        }
        width =
            if (fromType == WKChatIteMsgFromType.SEND || msgItemEntity.wkMsg.channelType == WKChannelType.PERSONAL) {
                maxWidth - AndroidUtilities.dp((70 + checkBoxMargin).toFloat() + flameWidth)
            } else {
                maxWidth - AndroidUtilities.dp((70 + 40 + checkBoxMargin).toFloat() + flameWidth)
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
        var top: Int
        var bottom: Int
        val currentFromUID: String? = getAdapter()!!.data[position].wkMsg.fromUID
        var nextFromUID: String? = ""
        var previousFromUID: String? = ""
        if (position + 1 <= getAdapter()!!.data.size - 1) {
            nextFromUID = getAdapter()!!.data[position + 1].wkMsg.fromUID
        }
        if (position - 1 > 0) {
            previousFromUID = getAdapter()!!.data[position - 1].wkMsg.fromUID
        }
        if (TextUtils.isEmpty(currentFromUID)) {
            top = AndroidUtilities.dp(4f)
            bottom = AndroidUtilities.dp(4f)
        } else {
            top = if (!TextUtils.isEmpty(previousFromUID) && previousFromUID == currentFromUID) {
                AndroidUtilities.dp(1.5f)
            } else {
                AndroidUtilities.dp(4f)
            }
            bottom = if (!TextUtils.isEmpty(nextFromUID) && nextFromUID == currentFromUID) {
                AndroidUtilities.dp(1.5f)
            } else {
                AndroidUtilities.dp(4f)
            }
        }
        if (position == getAdapter()!!.data.size - 1) {
            bottom = AndroidUtilities.dp(10f)
        }
        if (position == 0) {
            top = AndroidUtilities.dp(10f)
        }
        viewGroupLayout.setPadding(0, top, 0, bottom)
    }

    private fun getMsgFromOS(clientMsgNo: String): String {
        return if (clientMsgNo.endsWith("1")) {
            "Android"
        } else if (clientMsgNo.endsWith("2")) {
            "IOS"
        } else if (clientMsgNo.endsWith("3")) {
            "Web"
        } else {
            "PC"
        }
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        super.onViewAttachedToWindow(holder)
        val chatAdapter = getAdapter() as ChatAdapter
        chatAdapter.conversationContext.onMsgViewed(
            chatAdapter.data[holder.bindingAdapterPosition].wkMsg,
            holder.bindingAdapterPosition - chatAdapter.headerLayoutCount
        )
    }

}