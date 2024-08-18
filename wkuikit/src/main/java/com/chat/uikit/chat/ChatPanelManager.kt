package com.chat.uikit.chat

import android.Manifest
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.emoji.EmojiAdapter
import com.chat.base.emoji.EmojiManager
import com.chat.base.emoji.MoonUtil
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChatToolBarMenu
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.endpoint.entity.InitInputPanelMenu
import com.chat.base.endpoint.entity.SearchChatEditStickerMenu
import com.chat.base.endpoint.entity.SendTextMenu
import com.chat.base.entity.BottomSheetItem
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKChannelMemberRole
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.ui.components.ContactEditText
import com.chat.base.ui.components.SeekBarView
import com.chat.base.ui.components.SwitchView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.StringUtils
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.base.views.CommonAnim
import com.chat.base.views.FullyGridLayoutManager
import com.chat.base.views.NoEventRecycleView
import com.chat.uikit.R
import com.chat.uikit.chat.adapter.WKChatToolBarAdapter
import com.chat.uikit.chat.manager.SendMsgEntity
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent
import com.chat.uikit.contacts.service.FriendModel
import com.chat.uikit.group.GroupMemberEntity
import com.chat.uikit.group.RemindMemberAdapter
import com.chat.uikit.group.service.GroupModel
import com.chat.uikit.message.MsgModel
import com.chat.uikit.robot.RobotGIFAdapter
import com.chat.uikit.robot.RobotMenuAdapter
import com.chat.uikit.robot.entity.WKRobotEntity
import com.chat.uikit.robot.entity.WKRobotGIFEntity
import com.chat.uikit.robot.entity.WKRobotInlineQueryResult
import com.chat.uikit.robot.entity.WKRobotMenuEntity
import com.chat.uikit.robot.service.WKRobotModel
import com.chat.uikit.user.UserDetailActivity
import com.chat.uikit.utils.mentionDisplay
import com.effective.android.panel.PanelSwitchHelper
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelExtras
import com.xinbida.wukongim.entity.WKChannelMember
import com.xinbida.wukongim.entity.WKChannelStatus
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMentionInfo
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.msgmodel.WKMessageContent
import com.xinbida.wukongim.msgmodel.WKMsgEntity
import com.xinbida.wukongim.msgmodel.WKTextContent
import org.json.JSONObject
import java.util.Locale
import java.util.Objects
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min


class ChatPanelManager(
    val helper: PanelSwitchHelper,
    val parentView: View,
    private val moreLayout: FrameLayout,
    private val followScrollLayout: FrameLayout,
    val iConversationContext: IConversationContext,
    val resetTitleViewListener: () -> Unit,
    val showNewImageListener: (path: String) -> Unit,
) {
    private var isShowSendBtn: Boolean = false
    private var flame = 0
    private var lastInputTime: Long = 0
    private var inlineQueryOffset: String = ""
    private var searchKey: String = ""
    private var username: String = ""
    private val maxLength = 300

    private val menuView: View = parentView.findViewById(R.id.menuView)
    private val menuLayout: View = parentView.findViewById(R.id.menuLayout)
    private val editText: ContactEditText = parentView.findViewById(R.id.editText)
    private val hitTv: AppCompatTextView = parentView.findViewById(R.id.hitTv)
    private val sendIV: AppCompatImageView = parentView.findViewById(R.id.sendIV)
    private val markdownIv: AppCompatImageView = parentView.findViewById(R.id.markdownIv)
    private val flameIV: AppCompatImageView = parentView.findViewById(R.id.flameIV)
    private val menuIv: AppCompatImageView = parentView.findViewById(R.id.menuIv)
    private val panelView: FrameLayout = parentView.findViewById(R.id.panelView)
    private val chatView: LinearLayout = parentView.findViewById(R.id.chatView)
    private val chatTopLayout: FrameLayout = parentView.findViewById(R.id.chatTopLayout)
    private var flameLayout: LinearLayout? = null

    // 相册有新图
    private var newImageLayout: LinearLayout? = null

    // 回复 | 编辑
    private var chatTopView: LinearLayout? = null

    // 多选
    private var multipleChoiceView: LinearLayout? = null

    // 封禁
    private var banView: FrameLayout? = null

    // 禁言
    private var forbiddenView: FrameLayout? = null

    // 工具栏
    private var toolBarAdapter: WKChatToolBarAdapter? = null
    private val toolbarRecyclerView: RecyclerView =
        parentView.findViewById(R.id.toolbarRecyclerView)

    // 艾特
    private var remindRecycleView: NoEventRecycleView? = null
    private var remindHeaderView: View? = null
    private var remindMemberAdapter: RemindMemberAdapter? = null

    // gif
    private var robotGifRecyclerView: NoEventRecycleView? = null
    private var robotGIFAdapter: RobotGIFAdapter? = null
    private var robotGifHeaderView: View? = null

    // menu
    private var menuRecyclerView: NoEventRecycleView? = null
    private var menuHeaderView: View? = null
    private var robotMenuAdapter: RobotMenuAdapter? = null

    init {
        this.menuView.background = Theme.getBackground(Theme.colorAccount, 30f)
        editText.filters = arrayOf<InputFilter>(StringUtils.getInputFilter(maxLength))
        editText.setMaxLength(maxLength)
        initListener()
        initRemind()
        initRobotGIF()
        initRobotMenu()
        initTool()
        initMultipleChoiceView()
        initBanView()
        initForbiddenView()
        initChatTopView()
        initFlame()
        initNewImageView()
        EndpointManager.getInstance().invoke(
            "initInputPanel",
            InitInputPanelMenu(
                parentView,
                iConversationContext,
                followScrollLayout
            )
        )
    }

    fun updateForwardView(num: Int) {
        val forwardView = multipleChoiceView?.findViewWithTag<View>("forwardView")
        val deleteIv = multipleChoiceView?.findViewWithTag<AppCompatImageView>("deleteIv")
        val forwardIv = multipleChoiceView?.findViewWithTag<AppCompatImageView>("forwardIv")
        val forwardTv = multipleChoiceView?.findViewWithTag<AppCompatTextView>("forwardTv")
        val deleteTv = multipleChoiceView?.findViewWithTag<AppCompatTextView>("deleteTv")
        if (num > 0) {
            forwardView?.isEnabled = true
            deleteTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.colorDark
                )
            )
            forwardTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.colorDark
                )
            )
            deleteIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
            forwardIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
        } else {
            forwardView?.isEnabled = false
            deleteTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.color999
                )
            )
            forwardTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.color999
                )
            )
            deleteIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
            forwardIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
        }
    }

    fun isCanBack(): Boolean {
        if (newImageLayout?.visibility == View.VISIBLE) {
            newImageLayout?.visibility = View.GONE
            return false
        }
        if (helper.isPanelState()) {
            resetToolBar()
            helper.resetState()
            return false
        }
        return true
    }

    fun showMultipleChoice() {
        chatView.visibility = View.GONE
        isDisableToolBar(true)
        helper.resetState()
        CommonAnim.getInstance().showBottom2Top(multipleChoiceView)
    }

    fun hideMultipleChoice() {
        multipleChoiceView?.visibility = View.GONE
//        chatView.visibility=View.VISIBLE
        showOrHideForbiddenView()
        isDisableToolBar(false)
        CommonAnim.getInstance().showBottom2Top(chatView)
    }


    // 显示封禁
    fun showBan() {
        banView?.visibility = View.VISIBLE
        forbiddenView?.visibility = View.GONE
        chatView.visibility = View.GONE
        isDisableToolBar(true)
    }

    //隐藏封禁
    fun hideBan() {
        if (banView?.visibility == View.GONE) return
        banView?.visibility = View.GONE
        chatView.visibility = View.VISIBLE
        isDisableToolBar(false)
    }

    fun setEditContent(text: String) {
        val curPosition: Int = editText.selectionStart
        val sb = StringBuilder(
            Objects.requireNonNull(editText.text).toString()
        )
        sb.insert(curPosition, text)
        editText.setText(sb.toString())
        editText.setText(
            MoonUtil.getEmotionContent(
                iConversationContext.chatActivity,
                editText,
                sb.toString()
            )
        )
        editText.setSelection(curPosition + text.length)
    }

    private fun showForbiddenView() {
        helper.resetState()
        forbiddenView?.visibility = View.VISIBLE
        chatView.visibility = View.GONE
        toolbarRecyclerView.visibility = View.GONE
        banView?.visibility = View.GONE
        val forbiddenTV =
            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
        forbiddenTV?.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
    }

    private fun hideForbiddenView() {
        if (forbiddenView?.visibility == View.GONE) return
        forbiddenView?.visibility = View.GONE
        chatView.visibility = View.VISIBLE
        toolbarRecyclerView.visibility = View.VISIBLE
        val forbiddenTV =
            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
        forbiddenTV?.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
    }

    private fun isDisableToolBar(isDisable: Boolean) {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.data[index].isDisable = isDisable
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)

    }

    fun getEditText(): ContactEditText {
        return this.editText
    }

    fun showReplyLayout(mMsg: WKMsg) {
        var showName: String? = ""
        if (mMsg.from != null) {
            showName = mMsg.from.channelName
        } else {
            val channel = WKIM.getInstance().channelManager.getChannel(
                mMsg.fromUID,
                WKChannelType.PERSONAL
            )
            if (channel != null) {
                showName =
                    if (TextUtils.isEmpty(channel.channelRemark)) channel.channelName else channel.channelRemark
            }
        }
        val topLeftIv = chatTopView?.findViewWithTag<AppCompatImageView>("topLeftIv")
        val topTitleTv = chatTopView?.findViewWithTag<AppCompatTextView>("topTitleTv")
        val contentTv = chatTopView?.findViewWithTag<AppCompatTextView>("contentTv")
        topLeftIv?.setImageResource(R.mipmap.msg_panel_reply)
        topTitleTv?.text = showName
        val content =
            if (mMsg.remoteExtra != null && mMsg.remoteExtra.contentEditMsgModel != null) {
                mMsg.remoteExtra.contentEditMsgModel.displayContent
            } else {
                mMsg.baseContentMsgModel.displayContent
            }
        contentTv?.text = content
//        MoonUtil.identifyFaceExpression(
//            iConversationContext!!.chatActivity,
//            replyDisplayTv,
//            mMsg.baseContentMsgModel.getDisplayContent(),
//            MoonUtil.DEF_SCALE
//        )
        if (chatTopView?.visibility == View.GONE) {
            CommonAnim.getInstance().animateOpen(
                chatTopView,
                0,
                AndroidUtilities.dp(55f)
            ) {
                iConversationContext.chatRecyclerViewScrollToEnd()

//                UIUtil.requestFocus(editText)
//                UIUtil.showSoftInput(iConversationContext.chatActivity, editText)
                helper.toKeyboardState()
                // editText.performClick()
//                SoftKeyboardUtils.getInstance().showInput(iConversationContext.chatActivity,editText)
            }
        }

    }

    fun showEditLayout(mMsg: WKMsg) {
        val textModel = mMsg.baseContentMsgModel as WKTextContent
        var content = textModel.displayContent
        if (!TextUtils.isEmpty(mMsg.remoteExtra.contentEdit)) {
            val json = JSONObject(mMsg.remoteExtra.contentEdit)
            content = json.optString("content")
        }

        val topLeftIv = chatTopView?.findViewWithTag<AppCompatImageView>("topLeftIv")
        val topTitleTv = chatTopView?.findViewWithTag<AppCompatTextView>("topTitleTv")
        val contentTv = chatTopView?.findViewWithTag<AppCompatTextView>("contentTv")
        topTitleTv?.text = iConversationContext.chatActivity.getString(R.string.edit_msg)
        contentTv?.text = content
        editText.setText(content)
        editText.setSelection(content.length)
        if (chatTopView?.visibility == View.GONE) {
            CommonAnim.getInstance().animateOpen(
                chatTopView,
                0,
                AndroidUtilities.dp(55f)
            ) {
                iConversationContext.chatRecyclerViewScrollToEnd()
                helper.toKeyboardState()
            }
        }
        topLeftIv?.setImageResource(R.mipmap.msg_edit)
    }

    fun initRefreshListener() {
        WKIM.getInstance().channelMembersManager.addOnRefreshChannelMemberInfo(
            "InputPanel"
        ) { mChannelMember, _ ->
            if (mChannelMember != null
                && mChannelMember.channelID.equals(iConversationContext.chatChannelInfo.channelID)
                && mChannelMember.channelType == iConversationContext.chatChannelInfo.channelType
                && iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP
            ) {
                //禁言
                if (mChannelMember.memberUID == WKConfig.getInstance().uid) {
                    showOrHideForbiddenView()
                }
            }
        }
        WKIM.getInstance().channelManager.addOnRefreshChannelInfo(
            "InputPanel"
        ) { mChannel, _ ->
            if (mChannel.channelType == iConversationContext.chatChannelInfo.channelType && mChannel.channelID.equals(
                    iConversationContext.chatChannelInfo.channelID
                )
            ) {
                showOrHideForbiddenView()
                // 封禁群
                if (mChannel.status == WKChannelStatus.statusDisabled) {
                    showBan()
                } else {
                    hideBan()
                }
                flame = mChannel.flame
                CommonAnim.getInstance().showOrHide(flameIV, flame == 1, true)
                markdownIv.visibility = if (flame == 1) View.GONE else View.VISIBLE
                showFlame(mChannel.flameSecond)
            }
        }
    }

    private var timer: Timer? = null
    private fun showForbiddenTimer(totalTime: Long) {
        if (timer != null)
            return
        timer = Timer()
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                val nowTime = WKTimeUtils.getInstance().currentSeconds
                val day = (totalTime - nowTime) / (60 * 60 * 24)
                val hour = (totalTime - nowTime - day * 60 * 60 * 24) / (60 * 60)
                val min = (totalTime - nowTime - day * 60 * 60 * 24 - hour * 3600) / 60
                val second = (totalTime - nowTime) % 60
                if (nowTime >= totalTime) {
                    AndroidUtilities.runOnUIThread {
                        val channel = iConversationContext.chatChannelInfo
                        if (channel.forbidden == 1) {
                            showOrHideForbiddenView()
                        } else {
                            hideForbiddenView()
                        }
                    }
                    timer!!.cancel()
                    timer = null
                } else {
                    var dayStr = "00"
                    if (day > 0) {
                        dayStr = if (day < 10) {
                            "0$day"
                        } else "$day"
                    }
                    var hourStr = "00"
                    if (hour > 0) {
                        hourStr = if (hour < 10) {
                            "0$hour"
                        } else "$hour"
                    }
                    var minStr = "00"
                    if (min > 0) {
                        minStr = if (min < 10) {
                            "0$min"
                        } else "$min"
                    }
                    var secondStr = "00"
                    if (second > 0) {
                        secondStr = if (second < 10) {
                            "0$second"
                        } else "$second"
                    }
                    val content: String
                    if (day > 0) {
                        content = String.format(
                            iConversationContext.chatActivity.getString(R.string.forbidden_detail_day),
                            dayStr,
                            hourStr,
                            minStr,
                            secondStr
                        )
                    } else {
                        if (hour > 0) {
                            content = String.format(
                                iConversationContext.chatActivity.getString(R.string.forbidden_detail_hour),
                                hourStr,
                                minStr,
                                secondStr
                            )
                        } else {
                            content = if (min > 0) {
                                String.format(
                                    iConversationContext.chatActivity.getString(R.string.forbidden_detail_minute),
                                    minStr,
                                    secondStr
                                )
                            } else {
                                String.format(
                                    iConversationContext.chatActivity.getString(R.string.forbidden_detail_second),
                                    secondStr
                                )
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread {
                        val forbiddenTV =
                            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
                        forbiddenTV?.text = content
                    }
                }
            }
        }
        timer!!.schedule(timerTask, 0, 1000)

    }

    fun showOrHideForbiddenView() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.CUSTOMER_SERVICE) {
            hideBan()
            return
        }
        val mChannel = WKIM.getInstance().channelManager.getChannel(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        val mChannelMember = WKIM.getInstance().channelMembersManager.getMember(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType,
            WKConfig.getInstance().uid
        )
        if (mChannelMember != null) {
            if (mChannelMember.role == WKChannelMemberRole.admin) {
                hideForbiddenView()
            } else {
                if (mChannel != null && mChannel.forbidden == 1) {
                    if (mChannelMember.role == WKChannelMemberRole.manager) {
                        if (mChannelMember.forbiddenExpirationTime == 0L)
                            hideForbiddenView()
                        else {
                            // 显示成员禁言
                            showForbiddenWithMemberView(mChannelMember.forbiddenExpirationTime)
                        }
                    } else {
                        // 显示全员禁言
                        showForbiddenView()
                    }
                } else {
                    if (mChannelMember.forbiddenExpirationTime > 0) {
                        // 显示成员禁言
                        showForbiddenWithMemberView(mChannelMember.forbiddenExpirationTime)
                    } else {
                        hideForbiddenView()
                    }
                }
            }
        }

    }


    private fun showForbiddenWithMemberView(time: Long) {
        showForbiddenView()
        val nowTime = WKTimeUtils.getInstance().currentSeconds
        val day = (time - nowTime) / (3600 * 24)
        val hour = (time - nowTime) / 3600
        val min = (time - nowTime) / 60
        var showText = String.format(
            iConversationContext.chatActivity.getString(R.string.forbidden_to_minute),
            1
        )
        if (day > 0)
            showText = String.format(
                iConversationContext.chatActivity.getString(R.string.forbidden_to_day),
                day
            )
        else {
            if (hour > 0) {
                showText = String.format(
                    iConversationContext.chatActivity.getString(R.string.forbidden_to_hour),
                    hour
                )
            } else {
                if (min > 0) {
                    showText = String.format(
                        iConversationContext.chatActivity.getString(R.string.forbidden_to_minute),
                        min
                    )
                }
            }
        }
        showForbiddenTimer(time)
        AndroidUtilities.runOnUIThread {
            val forbiddenTV =
                forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
            forbiddenTV?.text = showText
        }
    }

    fun chatAvatarClick(uid: String, isLongClick: Boolean) {
        if (isLongClick) {
            if (uid == WKConfig.getInstance().uid) return
            if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                val loginMember = WKIM.getInstance().channelMembersManager.getMember(
                    iConversationContext.chatChannelInfo.channelID,
                    iConversationContext.chatChannelInfo.channelType,
                    WKConfig.getInstance().uid
                )
                if (loginMember != null) {
                    if ((iConversationContext.chatChannelInfo.forbidden == 1 && loginMember.role == WKChannelMemberRole.normal) || loginMember.forbiddenExpirationTime > 0) {
                        return
                    }
                }
                val member =
                    WKIM.getInstance().channelMembersManager.getMember(
                        iConversationContext.chatChannelInfo.channelID,
                        iConversationContext.chatChannelInfo.channelType,
                        uid
                    )
                if (member != null) {

                    addSpan(member.memberName, member.memberUID)
                } else {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        uid,
                        WKChannelType.PERSONAL
                    )
                    if (channel != null) {
                        addSpan(channel.channelName, channel.channelID)
                    }
                }
            }

        } else {
            if (iConversationContext.chatChannelInfo.channelType != WKChannelType.CUSTOMER_SERVICE) {
                //点击事件
                val intent =
                    Intent(
                        iConversationContext.chatActivity,
                        UserDetailActivity::class.java
                    )
                intent.putExtra("uid", uid)
                if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                    intent.putExtra("groupID", iConversationContext.chatChannelInfo.channelID)
                }
                iConversationContext.chatActivity.startActivity(intent)
            }

        }
    }

    fun onDestroy() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
        EndpointManager.getInstance().remove("emoji_click")
        WKIM.getInstance().robotManager.removeRefreshRobotMenu(iConversationContext.chatChannelInfo.channelID)
        WKIM.getInstance().channelManager.removeRefreshChannelInfo("InputPanel")
        WKIM.getInstance().channelMembersManager.removeRefreshChannelMemberInfo("InputPanel")
    }


    private fun initFlame() {
        flame = iConversationContext.chatChannelInfo.flame
        initFlameView()
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        if (flame == 1) {
            flameIV.visibility = View.VISIBLE
            CommonAnim.getInstance().showOrHide(flameIV, true, true)
            markdownIv.visibility = View.GONE
        } else
            markdownIv.visibility = View.VISIBLE
        seekBarView?.setDelegate(object : SeekBarView.SeekBarViewDelegate {
            override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                if (stop)
                    setProgress(progress)
            }

            override fun onSeekBarPressed(pressed: Boolean) {
            }
        })
        flameIV.setOnClickListener {
            if (flameLayout?.visibility == View.GONE) {
                CommonAnim.getInstance().animateOpen(
                    flameLayout,
                    0,
                    AndroidUtilities.dp(65f)
                )
                //    CommonAnim.getInstance().showBottom2Top(flameLayout)
            } else {
                CommonAnim.getInstance().animateClose(flameLayout)
            }
        }
        showFlame(iConversationContext.chatChannelInfo.flameSecond)
    }

    private fun showFlame(flameSecond: Int) {
        val burnSwitchView = flameLayout?.findViewWithTag<SwitchView>("switchView")
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        val burnTimeTv = flameLayout?.findViewWithTag<AppCompatTextView>("burnTimeTv")
        burnSwitchView?.isChecked = flame == 1
        if (flame == 0 && flameLayout?.visibility == View.VISIBLE) {
            CommonAnim.getInstance().animateClose(flameLayout)
        }
        var content: String? = ""
        when (flameSecond) {
            0 -> {
                content = iConversationContext.chatActivity.getString(R.string.burn_time_0)
                seekBarView?.setProgress(0f, true)
            }

            10 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_10)
                seekBarView?.setProgress(10 / 180f, true)
            }

            20 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_20)
                seekBarView?.setProgress(20 / 180f, true)
            }

            30 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_30)
                seekBarView?.setProgress(30 / 180f, true)
            }

            60 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_60)
                seekBarView?.setProgress(60 / 180f, true)
            }

            120 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_120)
                seekBarView?.setProgress(120 / 180f, true)
            }

            180 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_180)
                seekBarView?.setProgress(180 / 180f, true)
            }
        }
        if (flameSecond == 0) {
            burnTimeTv?.text = content
        } else burnTimeTv?.text = String.format(
            iConversationContext.chatActivity.getString(R.string.burn_time_desc),
            content
        )
    }

    private fun setProgress(progress: Float) {
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        val burnTimeTv = flameLayout?.findViewWithTag<AppCompatTextView>("burnTimeTv")
        val seekPg = progress * 180
        val newProgress: Int
        val content: String
        if (seekPg < 5) {
            newProgress = 0
            content = iConversationContext.chatActivity.getString(R.string.burn_time_0)
            seekBarView?.setProgress(0f, true)
        } else if (seekPg in 5.0..15.0) {
            newProgress = 10
            content = iConversationContext.chatActivity.getString(R.string.time_10)
        } else if (seekPg > 15 && seekPg <= 25) {
            newProgress = 20
            content = iConversationContext.chatActivity.getString(R.string.time_20)
        } else if (seekPg > 25 && seekPg <= 35) {
            newProgress = 30
            content = iConversationContext.chatActivity.getString(R.string.time_30)
        } else if (seekPg > 35 && seekPg <= 90) {
            newProgress = 60
            content = iConversationContext.chatActivity.getString(R.string.time_60)
        } else if (seekPg > 90 && seekPg <= 150) {
            newProgress = 120
            content = iConversationContext.chatActivity.getString(R.string.time_120)
        } else {
            newProgress = 180
            content = iConversationContext.chatActivity.getString(R.string.time_180)
        }
        if (newProgress == 0) {
            burnTimeTv?.text = content
        } else burnTimeTv?.text = String.format(
            iConversationContext.chatActivity.getString(R.string.burn_time_desc),
            content
        )
        seekBarView?.setProgress(newProgress.toFloat() / 180, true)
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(
                iConversationContext.chatChannelInfo.channelID, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (code != HttpResponseCode.success.toInt()) {
                    WKToastUtils.getInstance().showToast(msg)
                }
            }
        } else {
            GroupModel.getInstance().updateGroupSetting(
                iConversationContext.chatChannelInfo.channelID, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (code != HttpResponseCode.success.toInt()) {
                    WKToastUtils.getInstance().showToastNormal(msg)
                }
            }
        }
    }


    private fun initTool() {
        toolBarAdapter = WKChatToolBarAdapter()
        toolBarAdapter?.animationEnable = false
        toolbarRecyclerView.adapter = toolBarAdapter
        toolbarRecyclerView.layoutManager =
            LinearLayoutManager(
                iConversationContext.chatActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        //去除刷新条目闪动动画
        (Objects.requireNonNull(toolbarRecyclerView.itemAnimator) as DefaultItemAnimator).supportsChangeAnimations =
            false
        val toolBarList = EndpointManager.getInstance()
            .invokes<ChatToolBarMenu>(EndpointCategory.wkChatToolBar, iConversationContext)
        val tempToolBarList: MutableList<ChatToolBarMenu> = ArrayList()
        var isAddEmojiLayout = true
        for (menu in toolBarList) {
            if (menu != null) {
                if (menu.sid.equals("chat_toolbar_sticker")) {
                    isAddEmojiLayout = false
                }
                tempToolBarList.add(menu)
            }
        }
        if (isAddEmojiLayout) {
            val emojiToolBar = ChatToolBarMenu(
                "emojiToolBar",
                R.mipmap.icon_chat_toolbar_emoji,
                R.mipmap.icon_chat_toolbar_emoji,
                getEmojiLayout()
            ) { _, _ -> }
            tempToolBarList.add(0, emojiToolBar)
        }
        toolBarAdapter?.setList(tempToolBarList)
        toolBarAdapter?.addChildClickViewIds(R.id.imageView)
        toolBarAdapter?.setOnItemChildClickListener { adapter1: BaseQuickAdapter<*, *>, view: View, position: Int ->
            if (view.id == R.id.imageView) {
                SingleClickUtil.determineTriggerSingleClick(view, 500) {
                    val mChatToolBarMenu =
                        adapter1.getItem(position) as ChatToolBarMenu?
                            ?: return@determineTriggerSingleClick
                    if (mChatToolBarMenu.isDisable) return@determineTriggerSingleClick
                    // 如果点击的是@
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_remind") {
                        val index = editText.selectionStart
                        if (index != Objects.requireNonNull(editText.text)
                                .toString().length
                        ) {
                            editText.text.insert(
                                editText.selectionStart,
                                "@"
                            )
                        } else {
                            editText.append("@")
                        }
                        return@determineTriggerSingleClick
                    }
                    //如果点击的是更多
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_more") {
                        val path = ImageUtils.getInstance().newestPhoto
                        val oldPath =
                            WKSharedPreferencesUtil.getInstance().getSP("new_img_path")
                        if (!TextUtils.isEmpty(path) && TextUtils.isEmpty(oldPath)
                            || !TextUtils.isEmpty(path) && !TextUtils.isEmpty(oldPath) && oldPath != path
                        ) {
                            Handler(Looper.myLooper()!!).postDelayed({
                                showNewImgDialog(path)
                            }, 300)
                        }
                    }
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_voice") {
                        checkPermission(
                            iConversationContext.chatActivity,
                            mChatToolBarMenu,
                            position,
                            toolBarAdapter!!
                        )
                        return@determineTriggerSingleClick
                    }
                    toolBarClick(mChatToolBarMenu, position, toolBarAdapter!!)
                }
            }
        }
    }

    private fun initRobotMenu() {
        robotMenuAdapter = RobotMenuAdapter()
        this.menuRecyclerView =
            NoEventRecycleView(iConversationContext.chatActivity)
        this.menuRecyclerView!!.visibility = View.GONE
        this.menuHeaderView = View(iConversationContext.chatActivity)
        this.menuHeaderView!!.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.transparent
            )
        )
        this.menuRecyclerView?.setView(parentView, this.menuHeaderView)
        robotMenuAdapter?.addHeaderView(this.menuHeaderView!!)
        this.followScrollLayout.addView(this.menuRecyclerView)
        val menus = WKRobotModel.getInstance().getRobotMenus(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        menuRecyclerView!!.adapter = robotMenuAdapter
        menuRecyclerView!!.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        menuRecyclerView!!.addOnScrollListener(menuRecyclerView!!.onScrollListener)
        if (menus.size > 0) {
            robotMenuAdapter!!.setList(menus)
            CommonAnim.getInstance().showLeft2Right(menuView)
        }

        resetMenuHeader()

        menuLayout.setOnClickListener {
            menuLayout.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )

            if (robotMenuAdapter!!.data.size == 0) {
                val tempMenu: List<WKRobotMenuEntity> =
                    WKRobotModel.getInstance().getRobotMenus(
                        iConversationContext.chatChannelInfo.channelID,
                        iConversationContext.chatChannelInfo.channelType
                    )
                robotMenuAdapter!!.setList(tempMenu)
            }
            menuRecyclerView?.scrollToPosition(0)
            if (menuRecyclerView?.visibility == View.VISIBLE) {
                resetMenuIv()
                CommonAnim.getInstance().hideTop2Bottom(menuRecyclerView)
            } else {
                CommonAnim.getInstance().showBottom2Top(menuRecyclerView)
                showMenuIv()
            }
        }

        robotMenuAdapter!!.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val menu = robotMenuAdapter!!.data[position]
            if (menu != null) {
                menuLayout.performClick()
                val textContent = WKTextContent(menu.cmd)
                val list: MutableList<WKMsgEntity> =
                    ArrayList()
                val entity = WKMsgEntity()
                entity.length = menu.cmd.length
                entity.offset = 0
                entity.type = "bot_command"
                list.add(entity)
                textContent.entities = list

                val wkMsg = WKMsg()
                wkMsg.channelID = iConversationContext.chatChannelInfo.channelID
                wkMsg.channelType = iConversationContext.chatChannelInfo.channelType
                wkMsg.type = textContent.type
                wkMsg.baseContentMsgModel = textContent
                wkMsg.channelInfo = iConversationContext.chatChannelInfo
                wkMsg.robotID = menu.robot_id
                Log.e("ID是：", menu.robot_id)
                WKSendMsgUtils.getInstance().sendMessage(wkMsg)
            }
        }
        // 监听机器人刷新菜单
        WKIM.getInstance().robotManager.addOnRefreshRobotMenu(iConversationContext.chatChannelInfo.channelID) {
            checkRobotMenu(iConversationContext)
        }

    }

    private fun checkRobotMenu(iConversationContext: IConversationContext) {
        val robotMembers =
            WKIM.getInstance().channelMembersManager.getRobotMembers(
                iConversationContext.chatChannelInfo.channelID,
                iConversationContext.chatChannelInfo.channelType
            )
        if ((iConversationContext.chatChannelInfo.robot == 1 || robotMembers != null) && robotMembers.size > 0) {
            if (menuView.visibility == View.GONE) {
                CommonAnim.getInstance().showLeft2Right(menuView)
            }
//            if (menuRecyclerView!!.visibility == visibility && robotMenuAdapter!!.data.size == 0) {
            val menus = WKRobotModel.getInstance().getRobotMenus(
                iConversationContext.chatChannelInfo.channelID,
                iConversationContext.chatChannelInfo.channelType
            )
            robotMenuAdapter!!.setList(menus)
            resetMenuHeader()
//            }
        }

    }

    private fun resetMenuHeader() {
        parentView.post {
            var width = 40f
            if (robotMenuAdapter!!.data.size > 3) width = 48f
            menuHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(
                    min(
                        robotMenuAdapter!!.data.size,
                        3
                    ) * width
                )
            //  menuHeaderView!!.layoutParams.height -= WKConstants.getKeyboardHeight()
            this.menuRecyclerView?.setHeaderViewY(this.menuHeaderView!!.layoutParams.height.toFloat())
        }
    }

    private fun resetMenuIv() {
        CommonAnim.getInstance()
            .rotateImage(menuIv, 360f, 180f, R.mipmap.icon_menu)
    }

    private fun showMenuIv() {
        CommonAnim.getInstance().rotateImage(
            menuIv,
            180f,
            360f,
            R.mipmap.icon_menu_close
        )
    }

    private fun initRemind() {

        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.PERSONAL) return
        this.remindRecycleView =
            NoEventRecycleView(iConversationContext.chatActivity)
        this.remindHeaderView = View(iConversationContext.chatActivity)
        this.remindHeaderView!!.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.transparent
            )
        )
        remindRecycleView!!.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        remindRecycleView!!.setView(parentView, remindHeaderView)
        remindRecycleView!!.addOnScrollListener(remindRecycleView!!.onScrollListener)
        remindMemberAdapter = RemindMemberAdapter(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        remindRecycleView!!.adapter = remindMemberAdapter
        remindMemberAdapter!!.addHeaderView(remindHeaderView!!)
        remindMemberAdapter!!.onNormal()
//        remindRecycleView!!.addIScrollListener { _, _ ->
//            val layoutManager = remindRecycleView!!.layoutManager as LinearLayoutManager
//            val lastCompletelyVisibleItemPosition =
//                layoutManager.findLastCompletelyVisibleItemPosition()
//            if (lastCompletelyVisibleItemPosition == layoutManager.itemCount - 1) {
//                remindMemberAdapter!!.loadMore()
//            }
//        }
        this.followScrollLayout.addView(this.remindRecycleView)
        parentView.post {
            var height = 40f
            if (remindMemberAdapter!!.data.size > 3) height = 46f
            remindHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(
                    min(
                        remindMemberAdapter!!.data.size,
                        3
                    ) * height
                )
//            if (lastPanelType != PanelType.NONE) {
//                remindHeaderView!!.layoutParams.height -= WKConstant.getKeyboardHeight()
//            }
            this.remindRecycleView!!.setHeaderViewY(this.remindHeaderView!!.layoutParams.height.toFloat())
        }
        remindMemberAdapter!!.setOnItemClickListener { adapter, _, position ->
            val entity = adapter.data[position] as GroupMemberEntity?
            if (entity != null) {
                var memberEntity = entity.member
                if (memberEntity == null) {
                    memberEntity = WKChannelMember()
                    memberEntity.memberName =
                        iConversationContext.chatActivity.getString(R.string.all)
                    memberEntity.memberUID = "-1"
                }
                var showName = memberEntity.memberName
                val mChannel = WKIM.getInstance().channelManager.getChannel(
                    memberEntity.memberUID,
                    WKChannelType.PERSONAL
                )
                if (mChannel != null) {
                    showName = mChannel.channelName
                }
                var count = 1
                if (!TextUtils.isEmpty(remindMemberAdapter!!.searchKey)) {
                    count += remindMemberAdapter!!.searchKey.length
                }
                for (i in 0 until count) {
                    //模拟一次键盘删除点击
                    editText.dispatchKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL
                        )
                    )
                }
                //追加一个@提醒并弹出软键盘
                editText.requestFocus()
                addSpan(showName, memberEntity.memberUID)
            }
        }
        this.remindRecycleView!!.visibility = View.GONE

    }

    private fun initRobotGIF() {

        robotGifRecyclerView =
            NoEventRecycleView(iConversationContext.chatActivity)
        robotGifRecyclerView!!.addIScrollListener { _, _ ->
            val layoutManager = robotGifRecyclerView!!.layoutManager as LinearLayoutManager
            val lastCompletelyVisibleItemPosition =
                layoutManager.findLastCompletelyVisibleItemPosition()
            if (lastCompletelyVisibleItemPosition == layoutManager.itemCount - 1) {
                searchRobotGif(searchKey, username)
            }
        }
        robotGifHeaderView = View(iConversationContext.chatActivity)
        robotGifHeaderView!!.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.transparent
            )
        )
        robotGifRecyclerView!!.layoutManager = FullyGridLayoutManager(
            iConversationContext.chatActivity, 3
        )

        robotGifRecyclerView!!.addOnScrollListener(robotGifRecyclerView!!.onScrollListener)
        robotGIFAdapter = RobotGIFAdapter()
        robotGifRecyclerView!!.adapter = robotGIFAdapter
        robotGIFAdapter!!.addHeaderView(robotGifHeaderView!!)
        followScrollLayout.addView(robotGifRecyclerView)
        parentView.post {
            robotGifHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
        robotGifRecyclerView!!.setView(parentView, robotGifHeaderView)
        robotGIFAdapter!!.setOnItemClickListener { adapter, _, position ->
            val entity = adapter.data[position] as WKRobotGIFEntity
            if (entity.isNull) return@setOnItemClickListener
            hideRobotView()
            val stickerContent = WKGifContent()
            stickerContent.height = entity.height
            stickerContent.width = entity.width
            stickerContent.url = entity.url
            iConversationContext.sendMessage(stickerContent)
            editText.text = null
//            CommonAnim.getInstance().showOrHide(closeSearchLottieIV, false, true)
            CommonAnim.getInstance().showOrHide(sendIV, true, true)
            CommonAnim.getInstance().showOrHide(hitTv, false, true)
        }
        this.robotGifRecyclerView!!.visibility = View.GONE
    }

    private fun initListener() {
        panelView.setOnClickListener {

        }
        EndpointManager.getInstance().setMethod(
            "emoji_click"
        ) { `object` ->
            val emojiName = `object` as String
            if (TextUtils.isEmpty(emojiName)) {
                editText.dispatchKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL
                    )
                )
            } else {
                val curPosition = editText.selectionStart
                val sb = StringBuilder(
                    Objects.requireNonNull(editText.text).toString()
                )
                sb.insert(curPosition, emojiName)
                MoonUtil.addEmojiSpan(
                    editText,
                    emojiName,
                    iConversationContext.chatActivity
                )
                // 将光标设置到新增完表情的右侧
                val index = editText.text.toString().length
                editText.setSelection(
                    (curPosition + emojiName.length).coerceAtMost(index)
                )
            }
            null
        }
        SingleClickUtil.onSingleClick(markdownIv) {
            EndpointManager.getInstance().invoke("show_rich_edit", iConversationContext)
        }
        sendIV.setOnClickListener {
            var content = StringUtils.replaceBlank(editText.text.toString())
            if (!TextUtils.isEmpty(content)) {
                content = editText.text.toString()
                sendIV.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(
                        iConversationContext.chatActivity, R.color.popupTextColor
                    ), PorterDuff.Mode.MULTIPLY
                )
                val drawable = EmojiManager.getInstance()
                    .getDrawable(iConversationContext.chatActivity, content)
                if (drawable != null && iConversationContext.replyMsg == null) {
                    val `object` =
                        EndpointManager.getInstance().invoke(
                            "text_to_emoji_sticker",
                            SendTextMenu(
                                content,
                                iConversationContext
                            )
                        )

                    if (`object` != null) {
                        val result = `object` as Boolean
                        if (result) {
                            editText.text = null
                            lastInputTime = 0
                            return@setOnClickListener
                        }
                    }
                }
                val textMsgModel = WKTextContent(content)

                val list = editText.allUIDs
                if (list != null && list.size > 0) {
                    val mMentionInfo = WKMentionInfo()
                    val uidList: MutableList<String> = ArrayList()
                    var i = 0
                    val size = list.size
                    while (i < size) {
                        if (list[i].equals("-1", ignoreCase = true)) {
                            textMsgModel.mentionAll = 1 //remind all
                        } else {
                            uidList.add(list[i])
                        }
                        i++
                    }
                    mMentionInfo.uids = uidList
                    textMsgModel.mentionInfo = mMentionInfo
                }
                textMsgModel.entities = editText.allEntity

                iConversationContext.sendMessage(textMsgModel)
                editText.text = null
                lastInputTime = 0
                if (chatTopView?.visibility == View.VISIBLE) {
                    CommonAnim.getInstance().animateClose(chatTopView)
                }
            }
        }
        editText.addTextChangedListener(object : TextWatcher {
            var linesCount = 0

            // var lastHeight = AndroidUtilities.dp(35f)
            var start = 0
            var count = 0
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.count = count
                if (!TextUtils.isEmpty(s.toString())) {
                    val content = StringUtils.replaceBlank(s.toString())
//                    val content = s.toString().replace("\\s*|\r|\n|\t", "")
                    if (!isShowSendBtn && !TextUtils.isEmpty(content)) {
                        CommonAnim.getInstance().animImageView(sendIV)
                    }
                    isShowSendBtn = true
                    if (TextUtils.isEmpty(content)) {
                        sendIV.colorFilter = PorterDuffColorFilter(
                            ContextCompat.getColor(
                                iConversationContext.chatActivity, R.color.popupTextColor
                            ), PorterDuff.Mode.MULTIPLY
                        )
                    } else {
                        sendIV.colorFilter = PorterDuffColorFilter(
                            Theme.colorAccount, PorterDuff.Mode.MULTIPLY
                        )
                    }
                    CommonAnim.getInstance().showOrHide(markdownIv, false, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, false, true)
                    }
                } else {
                    CommonAnim.getInstance().showOrHide(markdownIv, true, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, true, true)
                    }
                    isShowSendBtn = false
                    sendIV.colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(
                            iConversationContext.chatActivity, R.color.popupTextColor
                        ), PorterDuff.Mode.MULTIPLY
                    )
                }
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                if (selectionEnd != selectionStart || selectionStart <= 0) {
                    hideRemindView()
                    return
                }

                var text = s.toString().substring(start, start + count)
                if (start + count == s.toString().length) {
                    if (count == 0 || TextUtils.isEmpty(text)) {
                        // 删除了字符串
//                        text = s.toString().substring(0, selectionStart)
                        if (s.toString().lastIndexOf("@") >= 0) {
                            val index = s.toString().lastIndexOf("@")
                            val remindText = s.toString().substring(index, s.toString().length)
                            if (!TextUtils.isEmpty(remindText)) text = remindText
                        }
                    } else {
                        if (s.toString().startsWith("@") && s.toString().contains(" ")) {
                            text = s.toString()
                        } else {
                            if (s.toString().lastIndexOf("@") >= 0) {
                                val index = s.toString().lastIndexOf("@")
                                val remindText = s.toString().substring(index, s.toString().length)
                                if (!TextUtils.isEmpty(remindText)) text = remindText
                            }
                        }
                    }
                } else {
                    val temp = s.toString().substring(0, start)
                    if (!TextUtils.isEmpty(temp) && temp.contains("@")) {
                        val index = temp.lastIndexOf("@")

                        if (count == 0) {
                            // 点击删除
                            val endIndex = editText.selectionEnd
                            val str = s.toString().substring(index, endIndex) + text
                            if (!TextUtils.isEmpty(str)) {
                                text = str
                            }
                        } else {
                            text = s.toString().substring(index, index + count) + text
                        }
                    }
                }
                //  text = s.toString().substring(0, selectionStart)
                if (!TextUtils.isEmpty(text) && (mentionDisplay(text) || text.startsWith("@"))) {
                    // 搜索成员
                    searchInputText(text)
                } else {
                    hideRemindView()
                    hideRobotView()
                    CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                    CommonAnim.getInstance()
//                        .showOrHide(closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(sendIV, true, true)
                }

                // 计算输入框高度
//                if (s.toString().isEmpty()) {
////                    editText.layoutParams.height = AndroidUtilities.dp(35f)
//                    linesCount = editText.lineCount
//                    return
//                }
//                if (editText.lineCount > 3) return
//                if (linesCount == 0) {
//                    linesCount = editText.lineCount
////                    editText.layoutParams.height = AndroidUtilities.dp(35f)
//                    return
//                }
//
//                val anim = ValueAnimator.ofInt(
//                            editText.layoutParams.height,
//                            AndroidUtilities.dp(35f)
//                        ).setDuration(150)
//                        anim.addUpdateListener { animation: ValueAnimator ->
//                            editText.layoutParams.height =
//                                animation.animatedValue as Int
//                            editText.requestLayout()
//                        }
                if (linesCount != editText.lineCount) {
                    linesCount = editText.lineCount
                    iConversationContext.chatRecyclerViewScrollToEnd()
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun afterTextChanged(s: Editable) {

                MoonUtil.replaceEmoticons(
                    iConversationContext.chatActivity,
                    s, start, count
                )
                if (s.toString().length <= 2 && !s.toString().startsWith("@")) {
                    //搜索表情
                    EndpointManager.getInstance()
                        .invoke("search_chat_edit_content",
                            SearchChatEditStickerMenu(
                                iConversationContext.chatActivity,
                                s.toString(),
                                parentView
                            ) { editText.text = null })
                } else {
                    EndpointManager.getInstance().invoke("hide_search_chat_edit_view", null)
                }

                //发送'正在输入'命令
                val nowTime = WKTimeUtils.getInstance().currentSeconds
                if (nowTime - lastInputTime >= 5 && !TextUtils.isEmpty(s)) {
                    var isSend = true
                    if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                        val mChannelMember =
                            WKIM.getInstance().channelMembersManager.getMember(
                                iConversationContext.chatChannelInfo.channelID,
                                iConversationContext.chatChannelInfo.channelType,
                                WKConfig.getInstance().uid
                            )
                        if (mChannelMember == null || mChannelMember.isDeleted == 1 || mChannelMember.status != 1) {
                            isSend = false
                        }
                    } else {
                        val channel = iConversationContext.chatChannelInfo
                        if (channel?.localExtra != null) {
                            var beDeleted = 0
                            var beBlacklist = 0
                            if (channel.localExtra.containsKey(WKChannelExtras.beBlacklist)) {
                                beBlacklist =
                                    channel.localExtra[WKChannelExtras.beBlacklist] as Int
                            }
                            if (channel.localExtra.containsKey(WKChannelExtras.beDeleted)) {
                                beDeleted =
                                    channel.localExtra[WKChannelExtras.beDeleted] as Int
                            }
                            if (beDeleted == 1 || beBlacklist == 1) isSend = false
                        }
                    }
                    if (isSend) {
                        MsgModel.getInstance().typing(
                            iConversationContext.chatChannelInfo.channelID,
                            iConversationContext.chatChannelInfo.channelType,
                        )
                    }
                    lastInputTime = WKTimeUtils.getInstance().currentSeconds
                }
            }
        })
    }

    private
    fun searchInputText(content: String) {
        var isSearchGroupMembers = true
        if (content.startsWith("@")) {
            val chars: CharArray = content.toCharArray()
            var index = 0
            var i = 0
            val size = chars.size
            while (i < size) {
                if (chars[i] == " "[0]) {
                    index = i
                    break
                }
                i++
            }
            var username: String = content
            if (index != 0) {
                username = content.substring(0, index + 1)
            }

            // 搜索机器人
            username = username.replace("@".toRegex(), "").replace(" ".toRegex(), "")
            if (!TextUtils.isEmpty(username)) {
//                if (!content.endsWith("@")) {
//                    isSearchGroupMembers = false
//                }
                val mRobot =
                    WKIM.getInstance().robotManager.getWithUsername(username.lowercase(Locale.getDefault()))
                if (mRobot != null && index != 0 && editText.text.toString()
                        .startsWith("@") && editText.text.toString()
                        .startsWith("@$username ")
                ) {
                    isSearchGroupMembers = false
                    hideRemindView()
                    inlineQueryOffset = ""
                    val searchKey: String =
                        content.substring(index, content.length).replace(" ".toRegex(), "")
                    if (!TextUtils.isEmpty(searchKey) && mRobot.username.lowercase(Locale.getDefault())
                            .equals(
                                "gif",
                                ignoreCase = true
                            )
                    ) {

                        CommonAnim.getInstance().showOrHide(hitTv, false, true)
                        inlineQueryOffset = ""
//                        if (TextUtils.isEmpty(searchKey)) {
//                            if (this.robotGifRecyclerView!!.visibility != View.GONE) {
//                                CommonAnim.getInstance().hideTop2Bottom(this.robotGifRecyclerView)
//                            }
//                        } else
                        searchRobotGif(searchKey, username)
                    } else {
                        val mTextPaint: TextPaint = editText.paint
                        val textWidth = mTextPaint.measureText(editText.text.toString())
                        val searchNameChars: CharArray = content.toCharArray()
                        var searchNameCharsIndex = 0
                        var count = 0
                        while (searchNameCharsIndex < searchNameChars.size) {
                            if (searchNameChars[searchNameCharsIndex] == " "[0]) {
                                count++
                                if (count > 1)
                                    break
                            }
                            searchNameCharsIndex++
                        }
                        if (count == 1) {
                            hitTv.hint = mRobot.placeholder
                            CommonAnim.getInstance().showOrHide(hitTv, true, true)
                            val lp = RelativeLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.topMargin = AndroidUtilities.dp(8f)
                            lp.leftMargin = textWidth.toInt() + AndroidUtilities.dp(10f)
                            hitTv.layoutParams = lp
                        } else {
                            CommonAnim.getInstance().showOrHide(hitTv, false, true)
                        }
                    }
                    CommonAnim.getInstance().showOrHide(sendIV, false, true)
//                    CommonAnim.getInstance().showOrHide(closeSearchLottieIV, true, true)

                } else {
                    CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                    CommonAnim.getInstance()
//                        .showOrHide(closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(sendIV, true, true)

                    val list: MutableList<WKRobotEntity> = ArrayList()
                    list.add(
                        WKRobotEntity(
                            "",
                            username,
                            0
                        )
                    )
                    WKRobotModel.getInstance().syncRobot(2, list)
                    hideRobotView()
                }
            } else {
                CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                CommonAnim.getInstance().showOrHide(closeSearchLottieIV, false, true)
                CommonAnim.getInstance().showOrHide(sendIV, true, true)
                hideRobotView()
            }
        }
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP && isSearchGroupMembers) {
            var remindSearchKey: String = content

            remindSearchKey = remindSearchKey.replace("@".toRegex(), "")
//            val keyword = mentionEnd(content)
            if (!TextUtils.isEmpty(remindSearchKey) && (content == "@" || content.endsWith("@"))) remindMemberAdapter!!.onNormal() else remindMemberAdapter!!.onSearch(
                remindSearchKey
            )
            remindRecycleView!!.scrollToPosition(0)
            val min =
                (remindMemberAdapter!!.itemCount - remindMemberAdapter!!.headerLayoutCount).coerceAtMost(
                    3
                )
            var height = 40f
            if (remindMemberAdapter!!.data.size > 3) height = 48f

            remindHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp((min * height))
            remindRecycleView!!.setHeaderViewY(remindHeaderView!!.layoutParams.height.toFloat())
            if (remindRecycleView!!.visibility == View.GONE) CommonAnim.getInstance()
                .showBottom2Top(remindRecycleView)
        }
    }


    private fun searchRobotGif(searchKey: String, username: String) {
        this.searchKey = searchKey
        this.username = username
        WKRobotModel.getInstance().inlineQuery(
            inlineQueryOffset,
            username,
            searchKey,
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        ) { _: Int, _: String?, result: WKRobotInlineQueryResult? ->
            if (TextUtils.isEmpty(inlineQueryOffset)) {
                robotGifRecyclerView!!.scrollToPosition(0)
                robotGifHeaderView!!.layoutParams.height =
                    parentView.top - AndroidUtilities.dp(100f)
                this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
            }
            if (result?.results != null && result.results.size > 0) {
                if (TextUtils.isEmpty(inlineQueryOffset)) robotGIFAdapter!!.setList(result.results) else robotGIFAdapter!!.addData(
                    result.results
                )
                resetData()
                inlineQueryOffset = result.next_offset
                if (this.robotGifRecyclerView!!.visibility != View.VISIBLE) {
                    CommonAnim.getInstance().showBottom2Top(this.robotGifRecyclerView)
                }
            }
        }
    }


    private fun resetData() {
        for (index in robotGIFAdapter!!.data.indices) {
            if (index < robotGIFAdapter!!.data.size && robotGIFAdapter!!.data[index].isNull) {
                robotGIFAdapter!!.removeAt(index)
            }
        }
        val num = robotGIFAdapter!!.data.size % 3
        if (num != 0) {
            var count = 3 - num
            while (count > 0) {
                val sticker = WKRobotGIFEntity()
                sticker.isNull = true
                robotGIFAdapter!!.addData(sticker)
                count--
            }
        }
    }

    fun hideRemindView() {
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP && remindRecycleView!!.visibility != View.GONE) {
            CommonAnim.getInstance().hideTop2Bottom(remindRecycleView!!)
        }
    }

    private fun hideRobotView() {
        if (robotGifRecyclerView!!.visibility != View.GONE) {
            CommonAnim.getInstance().hideTop2Bottom(robotGifRecyclerView!!)
//            initRobotGIF(iConversationContext!!)
            robotGifHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
    }


    fun resetToolBar() {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.getItem(index).isDisable =
                false
            toolBarAdapter!!.getItem(index).isSelected = false
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)
    }

    private fun getEmojiLayout(): View {
        val width = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(30f) * 8
        val normalList = EmojiManager.getInstance().getEmojiWithType("0_")
        val naturelList = EmojiManager.getInstance().getEmojiWithType("1_")
        val symbolsList = EmojiManager.getInstance().getEmojiWithType("2_")
        val list = ArrayList<String>()
        list.addAll(normalList)
        list.addAll(naturelList)
        list.addAll(symbolsList)
        val emojiLayout = LinearLayout(iConversationContext.chatActivity)
        val emojiAdapter = EmojiAdapter(list, width)
        val recyclerView = RecyclerView(iConversationContext.chatActivity)
        val emojiLayoutManager = GridLayoutManager(iConversationContext.chatActivity, 8)
        recyclerView.layoutManager = emojiLayoutManager
        recyclerView.adapter = emojiAdapter
        val height = WKConstants.getKeyboardHeight()
        emojiLayout.addView(
            recyclerView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                (height / AndroidUtilities.density).toInt()
            )
        )

        emojiAdapter.setOnItemClickListener { adapter, _, position ->
            val emojiName = adapter.getItem(position) as String
            val curPosition: Int = editText.selectionStart
            val sb = java.lang.StringBuilder(
                Objects.requireNonNull(editText.text).toString()
            )
            sb.insert(curPosition, emojiName)
            MoonUtil.addEmojiSpan(editText, emojiName, iConversationContext.chatActivity)
            editText.setSelection(curPosition + emojiName.length)
        }
        return emojiLayout
    }


    private fun checkPermission(
        activity: FragmentActivity, mChatToolBarMenu: ChatToolBarMenu,
        position: Int,
        adapter1: WKChatToolBarAdapter
    ) {
        val desc = String.format(
            activity.getString(R.string.microphone_permissions_des),
            activity.getString(R.string.app_name)
        )
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (result) {
                    toolBarClick(
                        mChatToolBarMenu,
                        position,
                        adapter1
                    )
                }
            }

            override fun clickResult(isCancel: Boolean) {}
        }, activity, desc, Manifest.permission.RECORD_AUDIO)
    }


    private fun toolBarClick(
        mChatToolBarMenu: ChatToolBarMenu,
        position: Int,
        adapter1: WKChatToolBarAdapter
    ) {
        //存在点击显示的view
        if (mChatToolBarMenu.bottomView != null) {
            if (mChatToolBarMenu.isSelected) {
                //已经选中就隐藏底部view弹起软键盘
                mChatToolBarMenu.isSelected = false
                SoftKeyboardUtils.getInstance().requestFocus(editText)
                SoftKeyboardUtils.getInstance()
                    .showSoftKeyBoard(iConversationContext.chatActivity, editText)
                helper.toKeyboardState()
                toolBarAdapter!!.notifyItemChanged(position)
            } else {
                var i = 0
                val size = toolBarAdapter!!.data.size
                while (i < size) {
                    toolBarAdapter!!.data[i].isSelected = false
                    i++
                }
                mChatToolBarMenu.isSelected = true
                adapter1.notifyItemRangeChanged(0, adapter1.data.size)
                if (!helper.isPanelState()) {
                    helper.toPanelState(R.id.emotionView)
                }
                moreLayout.removeAllViews()
                moreLayout.addView(
                    mChatToolBarMenu.bottomView,
                    LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.MATCH_PARENT.toFloat()
                    )
                )
                mChatToolBarMenu.bottomView.startAnimation(
                    loadAnimation(
                        iConversationContext
                    )
                )
                SoftKeyboardUtils.getInstance().loseFocus(editText)
                SoftKeyboardUtils.getInstance()
                    .hideInput(iConversationContext.chatActivity, editText)
            }
        }
        if (mChatToolBarMenu.iChatToolBarListener != null) mChatToolBarMenu.iChatToolBarListener.onChecked(
            true,
            iConversationContext
        )
    }

    private fun loadAnimation(iConversationContext: IConversationContext): Animation? {
        return AnimationUtils.loadAnimation(
            iConversationContext.chatActivity,
            R.anim.anim_add_child
        )
    }

    //相册有新的图片
    private fun showNewImgDialog(path: String) {
        WKSharedPreferencesUtil.getInstance().putSP("new_img_path", path)
        val imageView = newImageLayout?.findViewWithTag<AppCompatImageView>("imageView")
        GlideUtils.getInstance().showImg(iConversationContext.chatActivity, path, imageView)
        imageView?.setOnClickListener {
            showNewImageListener(path)
            newImageLayout?.visibility = View.GONE
        }
        newImageLayout?.visibility = View.VISIBLE
    }

    private fun initMultipleChoiceView() {
        multipleChoiceView = LinearLayout(iConversationContext.chatActivity)
        multipleChoiceView?.visibility = View.GONE
        multipleChoiceView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        panelView.addView(
            multipleChoiceView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55f)
        )
        val forwardView = LinearLayout(iConversationContext.chatActivity)
        forwardView.orientation = LinearLayout.VERTICAL
        val deleteView = LinearLayout(iConversationContext.chatActivity)
        deleteView.orientation = LinearLayout.VERTICAL
        multipleChoiceView?.addView(
            forwardView,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER)
        )
        multipleChoiceView?.addView(
            deleteView,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER)
        )

        val forwardIV = AppCompatImageView(iConversationContext.chatActivity)
        forwardIV.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.colorDark
            ), PorterDuff.Mode.MULTIPLY
        )
        forwardIV.setImageResource(R.mipmap.msg_forward)
        forwardView.addView(
            forwardIV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val forwardTV = AppCompatTextView(iConversationContext.chatActivity)
        forwardTV.text = iConversationContext.chatActivity.getString(R.string.base_forward)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_12)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        forwardTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)

        forwardTV.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.colorDark
            )
        )
        forwardView.addView(
            forwardTV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0,
                3,
                0,
                0
            )
        )

        // 删除
        val deleteIV = AppCompatImageView(iConversationContext.chatActivity)
        deleteIV.setImageResource(R.mipmap.msg_delete)
        deleteIV.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.colorDark
            ), PorterDuff.Mode.MULTIPLY
        )
        deleteView.addView(
            deleteIV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val deleteTV = AppCompatTextView(iConversationContext.chatActivity)
        deleteTV.text = iConversationContext.chatActivity.getString(R.string.delete)
        deleteTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        deleteTV.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.colorDark
            )
        )
        deleteView.addView(
            deleteTV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0,
                3,
                0,
                0
            )
        )


        forwardView.tag = "forwardView"
        deleteTV.tag = "deleteTv"
        deleteIV.tag = "deleteIv"
        forwardIV.tag = "forwardIv"
        forwardTV.tag = "forwardTv"

        forwardView.setOnClickListener {
            val chatAdapter = iConversationContext.chatAdapter
            val bottomSheetItemList = ArrayList<BottomSheetItem>()
            bottomSheetItemList.add(
                BottomSheetItem(
                    iConversationContext.chatActivity.getString(R.string.merge_forward),
                    R.mipmap.msg_share,
                    object : BottomSheetItem.IBottomSheetClick {
                        override fun onClick() {

                            //合并转发
                            val forwardContent =
                                WKMultiForwardContent()
                            forwardContent.channelType =
                                iConversationContext.chatChannelInfo.channelType
                            val list: MutableList<WKMsg> =
                                ArrayList()
                            forwardContent.userList = ArrayList()
                            var i = 0
                            val itemCount: Int = chatAdapter.itemCount
                            while (i < itemCount) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    list.add(chatAdapter.getItem(i).wkMsg)
                                    if (iConversationContext.chatChannelInfo.channelType == WKChannelType.PERSONAL) {
                                        var isAdd: Boolean
                                        if (forwardContent.userList.size == 0) {
                                            isAdd = true
                                        } else {
                                            isAdd = true
                                            for (j in forwardContent.userList.indices) {
                                                if ((!TextUtils.isEmpty(forwardContent.userList[j].channelID) && (forwardContent.userList[j].channelID == chatAdapter.getItem(
                                                        i
                                                    ).wkMsg.fromUID))
                                                ) {
                                                    isAdd = false
                                                    break
                                                }
                                            }
                                        }
                                        if (isAdd) {
                                            if (chatAdapter.getItem(i).wkMsg.from == null) {
                                                val mChannel = WKChannel()
                                                mChannel.channelID =
                                                    chatAdapter.getItem(i).wkMsg.fromUID
                                                chatAdapter.getItem(i).wkMsg.from = mChannel
                                            }
                                            forwardContent.userList.add(chatAdapter.getItem(i).wkMsg.from)
                                        }
                                    }
                                }
                                i++
                            }
                            forwardContent.msgList = list
                            EndpointManager.getInstance()
                                .invoke(
                                    EndpointSID.showChooseChatView,
                                    ChooseChatMenu(
                                        ChatChooseContacts { channelList: List<WKChannel>? ->
                                            if (!channelList.isNullOrEmpty()) {
                                                for (index in chatAdapter.data.indices) {
                                                    chatAdapter.getItem(index).isChoose = false
                                                    chatAdapter.getItem(index).isChecked = false
                                                }
                                                chatAdapter.notifyItemRangeChanged(
                                                    0,
                                                    chatAdapter.itemCount
                                                )


                                                for (mChannel: WKChannel in channelList) {
                                                    val option = WKSendOptions()
                                                    option.setting.receipt = mChannel.receipt
                                                    WKIM.getInstance().msgManager.sendWithOptions(
                                                        forwardContent,
                                                        mChannel,
                                                        option
                                                    )
                                                }
                                                WKToastUtils.getInstance()
                                                    .showToastNormal(
                                                        iConversationContext.chatActivity.getString(
                                                            R.string.is_forward
                                                        )
                                                    )

                                                for (index in toolBarAdapter!!.data.indices) {
                                                    toolBarAdapter!!.getItem(index).isDisable =
                                                        false
                                                }
                                                toolBarAdapter!!.notifyItemRangeChanged(
                                                    0,
                                                    toolBarAdapter!!.itemCount
                                                )
                                                multipleChoiceView?.visibility = View.GONE
                                                chatView.visibility = View.VISIBLE
                                                toolbarRecyclerView.visibility =
                                                    View.VISIBLE
                                                resetTitleViewListener()
                                            }
                                        },
                                        forwardContent
                                    )
                                )

                        }
                    })
            )
            bottomSheetItemList.add(
                BottomSheetItem(
                    iConversationContext.chatActivity.getString(R.string.item_forward),
                    R.mipmap.msg_forward,
                    object : BottomSheetItem.IBottomSheetClick {
                        override fun onClick() {

                            //逐条转发
                            val list: MutableList<WKMessageContent> =
                                ArrayList()
                            var i = 0
                            val itemCount: Int = chatAdapter.itemCount
                            while (i < itemCount) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    if ((chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_TEXT
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_IMAGE
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_GIF)
                                    ) list.add(chatAdapter.getItem(i).wkMsg.baseContentMsgModel) else {
                                        val textContent =
                                            WKTextContent(chatAdapter.getItem(i).wkMsg.baseContentMsgModel.displayContent)
                                        list.add(textContent)
                                    }
                                }
                                i++
                            }
                            if (list.size > 0) {
                                EndpointManager.getInstance()
                                    .invoke(
                                        EndpointSID.showChooseChatView,
                                        ChooseChatMenu(
                                            ChatChooseContacts { channelList: List<WKChannel>? ->
                                                val sendMsgEntityList: MutableList<SendMsgEntity> =
                                                    ArrayList()
                                                if (!channelList.isNullOrEmpty()) {
                                                    for (mChannel: WKChannel in channelList) {
                                                        for (index in list.indices) {
                                                            val option = WKSendOptions()
                                                            option.setting.receipt =
                                                                iConversationContext.chatChannelInfo.receipt
                                                            sendMsgEntityList.add(
                                                                SendMsgEntity(
                                                                    list[index], mChannel,
                                                                    option
                                                                )
                                                            )
                                                        }
                                                    }

                                                    WKSendMsgUtils.getInstance()
                                                        .sendMessages(sendMsgEntityList)
                                                    WKToastUtils.getInstance()
                                                        .showToastNormal(
                                                            iConversationContext.chatActivity.getString(
                                                                R.string.is_forward
                                                            )
                                                        )
                                                    for (index in chatAdapter.data.indices) {
                                                        chatAdapter.getItem(index).isChoose =
                                                            false
                                                        chatAdapter.getItem(index).isChecked =
                                                            false
                                                    }
                                                    chatAdapter.notifyItemRangeChanged(
                                                        0,
                                                        chatAdapter.itemCount
                                                    )
                                                    multipleChoiceView?.visibility =
                                                        View.GONE
                                                    chatView.visibility = View.VISIBLE
                                                    resetTitleViewListener()
                                                }
                                            },
                                            list
                                        )
                                    )
                            }

                        }
                    })
            )
            WKDialogUtils.getInstance().showBottomSheet(
                iConversationContext.chatActivity,
                iConversationContext.chatActivity.getString(R.string.base_forward),
                false,
                bottomSheetItemList
            )
        }

        deleteView.setOnClickListener {
            val chatAdapter = iConversationContext.chatAdapter
            val list: MutableList<WKMsg> = ArrayList()
            val ids = mutableListOf<String>()
            run {
                var i = 0
                val itemCount: Int = chatAdapter.itemCount
                while (i < itemCount) {
                    if (chatAdapter.getItem(i).isChecked) {
                        list.add(chatAdapter.getItem(i).wkMsg)
                        ids.add(chatAdapter.getItem(i).wkMsg.clientMsgNO)
                    }
                    i++
                }
            }
            if (list.size > 0) {
                WKDialogUtils.getInstance().showDialog(
                    iConversationContext.chatActivity,
                    iConversationContext.chatActivity.getString(R.string.delete_messages),
                    iConversationContext.chatActivity.getString(R.string.delete_select_msg),
                    true,
                    "",
                    iConversationContext.chatActivity.getString(R.string.delete),
                    0,
                    ContextCompat.getColor(iConversationContext.chatActivity, R.color.red)
                ) { index: Int ->
                    if (index == 1) {
                        WKIM.getInstance().msgManager.deleteWithClientMsgNos(ids)
                        MsgModel.getInstance().deleteMsg(list, null)
                        resetTitleViewListener()
                        multipleChoiceView?.visibility = View.GONE
                        toolbarRecyclerView.visibility = View.VISIBLE
                        CommonAnim.getInstance().showBottom2Top(chatView)
                        var i = 0
                        val itemCount: Int = chatAdapter.itemCount
                        while (i < itemCount) {
                            chatAdapter.getItem(i).isChoose = false
                            chatAdapter.getItem(i).isChecked = false
                            chatAdapter.notifyItemChanged(i)
                            i++
                        }
                        resetMenuIv()
                        resetToolBar()
                        iConversationContext.deleteOperationMsg()
                    }
                }
            }
        }
    }

    private fun initBanView() {
        banView = FrameLayout(iConversationContext.chatActivity)
        banView?.visibility = View.GONE
        banView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        panelView.addView(
            banView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55, Gravity.CENTER)
        )
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.text = iConversationContext.chatActivity.getString(R.string.group_ban)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_16)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        banView?.addView(
            textView,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun initForbiddenView() {
        forbiddenView = FrameLayout(iConversationContext.chatActivity)
        forbiddenView?.visibility = View.GONE
        panelView.addView(
            forbiddenView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55, Gravity.CENTER)
        )
        forbiddenView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        val contentLayout = LinearLayout(iConversationContext.chatActivity)
        contentLayout.orientation = LinearLayout.HORIZONTAL
        forbiddenView?.addView(
            contentLayout,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.icon_forbidden)
        contentLayout.addView(imageView, LayoutHelper.createLinear(20, 20, Gravity.CENTER))
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_16)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        contentLayout.addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 10, 0, 0, 0
            )
        )
        textView.tag = "forbiddenTV"
    }

    private fun initChatTopView() {
        chatTopView = LinearLayout(iConversationContext.chatActivity)
        chatTopView?.visibility = View.GONE
        chatTopView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        chatTopView?.setPadding(
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(8f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(8f)
        )
        chatTopLayout.addView(
            chatTopView,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.ic_ab_forward)
        imageView.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.colorAccent
            ), PorterDuff.Mode.MULTIPLY
        )
        chatTopView?.addView(
            imageView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0,
                0,
                10,
                0
            )
        )
        val centerLayout = LinearLayout(iConversationContext.chatActivity)
        centerLayout.orientation = LinearLayout.VERTICAL
        chatTopView?.addView(
            centerLayout,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER)
        )
        val nameTv = AppCompatTextView(iConversationContext.chatActivity)
        nameTv.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.colorAccent
            )
        )
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_14)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        centerLayout.addView(
            nameTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER
            )
        )
        nameTv.maxLines = 1
        nameTv.ellipsize = TextUtils.TruncateAt.END
        val contentTv = AppCompatTextView(iConversationContext.chatActivity)
        contentTv.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        contentTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        centerLayout.addView(
            contentTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER
            )
        )
        contentTv.maxLines = 1
        contentTv.ellipsize = TextUtils.TruncateAt.END

        val rightIv = AppCompatImageView(iConversationContext.chatActivity)
        rightIv.setImageResource(R.mipmap.themes_deletecolor)
        rightIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.popupTextColor
            ), PorterDuff.Mode.MULTIPLY
        )
        chatTopView?.addView(
            rightIv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                10,
                0,
                0,
                0
            )
        )
        rightIv.setOnClickListener {
            CommonAnim.getInstance().animateClose(chatTopView)
            editText.text = null
            iConversationContext.deleteOperationMsg()
        }
        rightIv.background = Theme.createSelectorDrawable(Theme.getPressedColor())
        imageView.tag = "topLeftIv"
        nameTv.tag = "topTitleTv"
        contentTv.tag = "contentTv"
    }

    private fun initFlameView() {
        flameLayout = LinearLayout(iConversationContext.chatActivity)

        chatTopLayout.addView(
            flameLayout,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER)
        )
        flameLayout?.visibility = View.GONE
        flameLayout?.orientation = LinearLayout.HORIZONTAL
        flameLayout?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        flameLayout?.setPadding(
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(0f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(0f)
        )
        val contentLayout = LinearLayout(iConversationContext.chatActivity)
        contentLayout.orientation = LinearLayout.VERTICAL
        flameLayout?.addView(
            contentLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                1f,
                Gravity.CENTER
            )
        )
        val topLayout = LinearLayout(iConversationContext.chatActivity)
        topLayout.orientation = LinearLayout.HORIZONTAL
        contentLayout.addView(
            topLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.flame_small)
        imageView.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.color999
            ), PorterDuff.Mode.MULTIPLY
        )
        val burnTimeTv = AppCompatTextView(iConversationContext.chatActivity)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_14)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        burnTimeTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        burnTimeTv.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        burnTimeTv.text = iConversationContext.chatActivity.getString(R.string.burn_time_desc)
        topLayout.addView(
            imageView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        topLayout.addView(
            burnTimeTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                5,
                0,
                0,
                0
            )
        )
        val seekBarView = SeekBarView(iConversationContext.chatActivity, false)
        seekBarView.setColors(
            Theme.color999,
            Theme.colorAccount
        )
        seekBarView.setDelegate(object : SeekBarView.SeekBarViewDelegate {
            override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                if (stop)
                    setProgress(progress)
            }

            override fun onSeekBarPressed(pressed: Boolean) {
            }
        })
        contentLayout.addView(
            seekBarView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                30,
                Gravity.CENTER,
                10,
                0,
                15,
                0
            )
        )
        val switchView = SwitchView(iConversationContext.chatActivity)
        flameLayout?.addView(
            switchView,
            LayoutHelper.createLinear(45, 40, Gravity.CENTER, 0, 0, 0, 0)
        )
        switchView.setOnCheckedChangeListener { v, isChecked ->
            run {
                if (v.isPressed) {
                    if (iConversationContext.chatChannelInfo.channelType == WKChannelType.PERSONAL) {
                        FriendModel.getInstance().updateUserSetting(
                            iConversationContext.chatChannelInfo.channelID,
                            "flame",
                            if (isChecked) 1 else 0
                        ) { code: Int, msg: String? ->
                            if (code != HttpResponseCode.success.toInt()) {
                                switchView.isChecked = !isChecked
                                WKToastUtils.getInstance().showToast(msg)
                            } else {
                                if (!isChecked) {
                                    CommonAnim.getInstance().animateClose(flameLayout)
                                }
                            }
                        }
                    } else {
                        GroupModel.getInstance().updateGroupSetting(
                            iConversationContext.chatChannelInfo.channelID,
                            "flame",
                            if (isChecked) 1 else 0
                        ) { code, msg ->
                            if (code != HttpResponseCode.success.toInt()) {
                                switchView.isChecked = !isChecked
                                WKToastUtils.getInstance().showToast(msg)
                            } else {
                                if (!isChecked) {
                                    CommonAnim.getInstance().animateClose(flameLayout)
                                }
                            }
                        }
                    }
                }
            }
        }

        switchView.tag = "switchView"
        seekBarView.tag = "seekBarView"
        burnTimeTv.tag = "burnTimeTv"
    }

    private fun initNewImageView() {
        newImageLayout = LinearLayout(iConversationContext.chatActivity)
        newImageLayout?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.layoutColor
            )
        )
        newImageLayout?.orientation = LinearLayout.VERTICAL
        newImageLayout?.visibility = View.GONE
        newImageLayout?.setPadding(
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f)
        )
        followScrollLayout.addView(
            newImageLayout,
            LayoutHelper.createFrame(
                90,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.CENTER or Gravity.END,
                0f,
                0f,
                10f,
                0f
            )
        )
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.popupTextColor
            )
        )
        textView.text = iConversationContext.chatActivity.getString(R.string.probably_send_img)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_10)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        newImageLayout?.addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.drawable.default_view_bg)
        imageView.tag = "imageView"
        newImageLayout?.addView(
            imageView,
            LayoutHelper.createLinear(70, 120, Gravity.CENTER, 0, 10, 0, 0)
        )
    }

    fun addSpan(name: String, uid: String) {
        val text = "@${name} "
        editText.addSpan(
            text,
            uid
        )
    }
}