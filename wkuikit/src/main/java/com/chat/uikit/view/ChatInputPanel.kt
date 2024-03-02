package com.chat.uikit.view

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
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
import com.chat.base.endpoint.entity.*
import com.chat.base.entity.BottomSheetItem
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKChannelMemberRole
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.ui.components.ContactEditText
import com.chat.base.ui.components.SeekBarView
import com.chat.base.utils.*
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.base.views.*
import com.chat.base.views.keyboard.*
import com.chat.uikit.R
import com.chat.uikit.chat.adapter.WKChatToolBarAdapter
import com.chat.uikit.chat.manager.SendMsgEntity
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent
import com.chat.uikit.contacts.service.FriendModel
import com.chat.uikit.databinding.ChatInputLayoutBinding
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
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.enums.PopupPosition
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.*
import com.xinbida.wukongim.msgmodel.WKTextContent
import com.xinbida.wukongim.msgmodel.WKMessageContent
import com.xinbida.wukongim.msgmodel.WKMsgEntity
import org.json.JSONObject
import org.telegram.ui.Components.RLottieDrawable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min


class ChatInputPanel : LinearLayout, IInputPanel {
    private var lastInputTime: Long = 0
    private var panelType = PanelType.NONE
    private var lastPanelType = panelType
    private var isKeyboardOpened = false
    private var toolBarAdapter: WKChatToolBarAdapter? = null
    private var robotMenuAdapter: RobotMenuAdapter? = null
    private var channelId: String = ""
    private var channelType: Byte = WKChannelType.PERSONAL
    private var iConversationContext: IConversationContext? = null
    private var isShowSendBtn: Boolean = false
    private var iInputPanelListener: IInputPanelListener? = null
    private var recyclerViewContentView: FrameLayout? = null
    private var chatUnreadView: View? = null
    private var menuRecyclerView: NoEventRecycleView? = null
    private var menuHeaderView: View? = null
    private var remindRecycleView: NoEventRecycleView? = null
    private var remindHeaderView: View? = null
    private var remindMemberAdapter: RemindMemberAdapter? = null
    private var inlineQueryOffset: String = ""
    private var searchKey: String = ""
    private var username: String = ""
    private var robotGifRecyclerView: NoEventRecycleView? = null
    private var robotGIFAdapter: RobotGIFAdapter? = null
    private var robotGifHeaderView: View? = null
    var signal = 0
    private var flame = 0
    private lateinit var seekBarView: SeekBarView
    private var chatViewHeight: Int = 0
    private var textHeight: Int = 0
    private var lastCount: Int = 1
    private var defEditTextHeight = 0

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        val root = LayoutInflater.from(context).inflate(R.layout.chat_input_layout, this, true)
        viewBinding = ChatInputLayoutBinding.bind(root)
        init()
    }

    private val viewBinding: ChatInputLayoutBinding
    private var isActive = false

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
//        val layoutTransition = LayoutTransition()
//        viewBinding.chatView.layoutTransition = layoutTransition
//        layoutTransition.enableTransitionType(LayoutTransition.CHANGE_APPEARING)
        viewBinding.editText.post { defEditTextHeight = viewBinding.editText.height }
        viewBinding.panelView.post { Log.e("当前坐标", "${viewBinding.panelView.y}") }
        viewBinding.topTitleTv.setTextColor(Theme.colorAccount)
        viewBinding.menuView.background = Theme.getBackground(Theme.colorAccount, 30f)
        setBackgroundColor(ContextCompat.getColor(context, R.color.clrCCC))
        viewBinding.editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!isKeyboardOpened) {
                    UIUtil.requestFocus(viewBinding.editText)
                    UIUtil.showSoftInput(context, viewBinding.editText)
                    handleAnimator(PanelType.INPUT_MOTHOD)
                    onInputPanelStateChangedListener?.onShowInputMethodPanel()
                }
                resetToolBar()
                return@setOnTouchListener false
            }
            false
        }
        viewBinding.topLeftIv.colorFilter = PorterDuffColorFilter(
            Theme.colorAccount, PorterDuff.Mode.MULTIPLY
        )
        viewBinding.topCloseIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                context, R.color.color999
            ), PorterDuff.Mode.MULTIPLY
        )

        viewBinding.forwardIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                context, R.color.popupTextColor
            ), PorterDuff.Mode.MULTIPLY
        )

        viewBinding.deleteIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                context, R.color.popupTextColor
            ), PorterDuff.Mode.MULTIPLY
        )
        viewBinding.topCloseIv.setOnClickListener {
            CommonAnim.getInstance().animateClose(viewBinding.topLayout)
            viewBinding.editText.setText("")
            iConversationContext!!.deleteOperationMsg()
        }
        viewBinding.sendIV.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                context, R.color.popupTextColor
            ), PorterDuff.Mode.MULTIPLY
        )
        EndpointManager.getInstance().setMethod(
            "emoji_click"
        ) { `object` ->
            val emojiName = `object` as String
            if (TextUtils.isEmpty(emojiName)) {
                viewBinding.editText.dispatchKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL
                    )
                )
            } else {
                val curPosition = viewBinding.editText.selectionStart
                val sb = StringBuilder(
                    Objects.requireNonNull(viewBinding.editText.text).toString()
                )
                sb.insert(curPosition, emojiName)
                MoonUtil.addEmojiSpan(
                    viewBinding.editText,
                    emojiName,
                    iConversationContext!!.chatActivity
                )
                // 将光标设置到新增完表情的右侧
                val index = viewBinding.editText.text.toString().length
                viewBinding.editText.setSelection(
                    (curPosition + emojiName.length).coerceAtMost(index)
                )
            }
            null
        }
//        markdownIv.colorFilter = PorterDuffColorFilter(
//            ContextCompat.getColor(
//                context, R.color.popupTextColor
//            ), PorterDuff.Mode.MULTIPLY
//        )

//        editText.viewTreeObserver.addOnGlobalLayoutListener {
//            if (textHeight == 0) {
//                val sPaint: Paint = editText.paint
//                val sF: Paint.FontMetrics = sPaint.fontMetrics
//                textHeight = kotlin.math.ceil((sF.descent - sF.top).toDouble()).toInt() + 2
//                val sWidth = sPaint.measureText(editText.text.toString())
//            }
//            val sPaint: Paint = editText.paint
//            var sWidth = sPaint.measureText(editText.text.toString())
//            val total = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(43f)
//            if (sWidth <= 0) {
//                sWidth = total.toFloat()
//            }
//            var count = total / sWidth.toInt()
//            if (count > 3) count = 3
//            if (editText.lineCount == 1 && chatViewHeight == 0) {
//                chatViewHeight = editText.layoutParams.height
//            }
//            val newHeight = chatViewHeight + (editText.lineCount - 1) * textHeight
//            Log.e("高度一只111", "--->$lastCount,$count")
//            if (lastCount == count) {
//                Log.e("高度一只", "--->")
//                return@addOnGlobalLayoutListener
//            }
//            lastCount = count
//            Log.e("--->", "--dd$newHeight")
//           // CommonAnim.getInstance().animateOpen(editText, editText.layoutParams.height, newHeight)
//        }
    }

    private fun resetToolBar() {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.getItem(index).isDisable =
                false
            toolBarAdapter!!.getItem(index).isSelected = false
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)
    }

    fun chatAvatarClick(uid: String, isLongClick: Boolean) {
        if (isLongClick) {
            if (uid == WKConfig.getInstance().uid) return
            if (channelType == WKChannelType.GROUP) {
                val loginMember = WKIM.getInstance().channelMembersManager.getMember(
                    channelId,
                    channelType,
                    WKConfig.getInstance().uid
                )
                if (loginMember != null) {
                    if ((iConversationContext!!.chatChannelInfo.forbidden == 1 && loginMember.role == WKChannelMemberRole.normal) || loginMember.forbiddenExpirationTime > 0) {
                        return
                    }
                }
                val member =
                    WKIM.getInstance().channelMembersManager.getMember(
                        channelId,
                        channelType,
                        uid
                    )
                if (member != null) {
                    viewBinding.editText.addSpan(
                        "@${member.memberName} ",
                        member.memberUID
                    )
                } else {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        uid,
                        WKChannelType.PERSONAL
                    )
                    if (channel != null) {
                        viewBinding.editText.addSpan(
                            "@${channel.channelName} ",
                            channel.channelID
                        )
                    }
                }
            }

        } else {
            if (channelType != WKChannelType.CUSTOMER_SERVICE) {
                //点击事件
                val intent =
                    Intent(
                        iConversationContext!!.chatActivity,
                        UserDetailActivity::class.java
                    )
                intent.putExtra("uid", uid)
                if (channelType == WKChannelType.GROUP) {
                    intent.putExtra("groupID", channelId)
                }
                iConversationContext!!.chatActivity.startActivity(intent)
            }

        }
    }

    fun setEditContent(text: String) {
        val curPosition: Int = viewBinding.editText.selectionStart
        val sb = StringBuilder(
            Objects.requireNonNull(viewBinding.editText.text).toString()
        )
        sb.insert(curPosition, text)
        viewBinding.editText.setText(sb.toString())
        viewBinding.editText.setText(
            MoonUtil.getEmotionContent(
                iConversationContext!!.chatActivity,
                viewBinding.editText,
                sb.toString()
            )
        )
        viewBinding.editText.setSelection(curPosition + text.length)
    }

    fun lastBottom(recyclerView: RecyclerView): Int {
        return recyclerView.adapter?.let { adapter ->
            if (adapter.itemCount <= 0) {
                recyclerView.height
            } else {
                (recyclerView.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
                    val view =
                        layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())
                    recyclerView.height - (view?.bottom ?: 0)
                } ?: throw IllegalStateException()
            }
        } ?: throw IllegalStateException()
    }

    fun scrollToEnd(topRecyclerView: View, margin: Float) {
        if (lastPanelType == PanelType.NONE) return

        var rToValue: Float
//        var rFromValue = fromValue
//        val max = max(abs(toValue.toInt()), abs(fromValue.toInt()))
//        if (margin > max) {
//            rToValue = 0f
//            rFromValue = 0f
//        } else {
//            if (rToValue != 0.0f) {
//                rToValue += margin
//            }
//            if (rFromValue != 0f) {
//                rFromValue += margin
//            }
//            if (rToValue > 0f) rToValue = 0f
//            if (rFromValue > 0f) rFromValue = 0f
//        }
        rToValue = -WKConstants.getKeyboardHeight() - topRecyclerView.y + margin
        if (rToValue >= 0) rToValue = 0f

        rToValue += topRecyclerView.y
//        if (abs(rToValue) > WKConstant.getKeyboardHeight()) {
//            rToValue = -WKConstant.getKeyboardHeight().toFloat()
//        }
        val recyclerViewTranslationYAnimator: ObjectAnimator =
            ObjectAnimator.ofFloat(
                topRecyclerView,
                "translationY",
                topRecyclerView.y,
                rToValue
            )
        val animatorSet = AnimatorSet()
        animatorSet.duration = 250
        animatorSet.play(recyclerViewTranslationYAnimator)
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                topRecyclerView.requestLayout()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animatorSet.start()
    }

    fun getLastPanelType(): PanelType {
        return lastPanelType
    }

    fun isCanBack(): Boolean {
        return if (lastPanelType == PanelType.NONE) {
            true
        } else {
            hideSoftKeyBoard()
            false
        }
    }

    private fun showSoftKeyBoard() {
        if (!isKeyboardOpened) {
            UIUtil.requestFocus(viewBinding.editText)
            UIUtil.showSoftInput(context, viewBinding.editText)
            handleAnimator(PanelType.INPUT_MOTHOD)
            onInputPanelStateChangedListener?.onShowInputMethodPanel()
        }
        resetToolBar()
    }

    private fun hideSoftKeyBoard() {
        UIUtil.loseFocus(viewBinding.editText)
        UIUtil.hideSoftInput(context, viewBinding.editText)
        onInputPanelStateChangedListener?.onShowVoicePanel()
        reset()
    }


    fun appendEditContent(showName: String, uid: String) {
        viewBinding.editText.dispatchKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL
            )
        )
        viewBinding.editText.requestFocus()
        viewBinding.editText.addSpan("@$showName ", uid)
        Looper.myLooper()?.let {
            Handler(it).postDelayed({
                showSoftKeyBoard()
            }, 200)
        }
    }

    private fun resetMenuIv() {
        CommonAnim.getInstance()
            .rotateImage(viewBinding.menuIv, 360f, 180f, R.mipmap.icon_menu)
    }

    private fun showMenuIv() {
        CommonAnim.getInstance().rotateImage(
            viewBinding.menuIv,
            180f,
            360f,
            R.mipmap.icon_menu_close
        )
    }

    fun hideMultipleChoice() {
        showOrHideForbiddenView()
        isDisableToolBar(false)
        CommonAnim.getInstance().hideTop2Bottom(viewBinding.multipleChoiceView)
    }

    fun showMultipleChoice() {
        hideSoftKeyBoard()
        isDisableToolBar(true)
        CommonAnim.getInstance().showBottom2Top(viewBinding.multipleChoiceView)
    }

    private fun isDisableToolBar(isDisable: Boolean) {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.data[index].isDisable = isDisable
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)

    }

    fun hideTopView() {
        CommonAnim.getInstance().animateClose(viewBinding.topLayout)
    }

    fun showEditLayout(mMsg: WKMsg) {
        val textModel = mMsg.baseContentMsgModel as WKTextContent
        var content = textModel.getDisplayContent()
        if (!TextUtils.isEmpty(mMsg.remoteExtra.contentEdit)) {
            val json = JSONObject(mMsg.remoteExtra.contentEdit)
            content = json.optString("content")
        }
        viewBinding.contentTv.text = content
        viewBinding.editText.setText(content)
        viewBinding.editText.setSelection(content.length)
        if (viewBinding.topLayout.visibility == View.GONE) {
            CommonAnim.getInstance().animateOpen(
                viewBinding.topLayout,
                0,
                AndroidUtilities.dp(55f)
            ) {
                iConversationContext!!.chatRecyclerViewScrollToEnd()
                showSoftKeyBoard()
            }
        }
        viewBinding.topLeftIv.setImageResource(R.mipmap.msg_edit)

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
        viewBinding.topLeftIv.setImageResource(R.mipmap.msg_panel_reply)
        viewBinding.topTitleTv.text = showName
        val content = mMsg.baseContentMsgModel.getDisplayContent()
        viewBinding.contentTv.text = content
//        MoonUtil.identifyFaceExpression(
//            iConversationContext!!.chatActivity,
//            replyDisplayTv,
//            mMsg.baseContentMsgModel.getDisplayContent(),
//            MoonUtil.DEF_SCALE
//        )
        if (viewBinding.topLayout.visibility == GONE) {
            CommonAnim.getInstance().animateOpen(
                viewBinding.topLayout,
                0,
                AndroidUtilities.dp(55f)
            ) {
                iConversationContext!!.chatRecyclerViewScrollToEnd()
                showSoftKeyBoard()
            }
        }
    }

    fun getEditText(): ContactEditText {
        return viewBinding.editText
    }

    fun initView(
        iConversationContext: IConversationContext,
        recyclerViewContentView: FrameLayout,
        chatUnreadView: View,
        chatMorePanel: ChatMorePanel
    ) {
        this.chatUnreadView = chatUnreadView
        this.recyclerViewContentView = recyclerViewContentView
        this.iConversationContext = iConversationContext
        this.flame = iConversationContext.chatChannelInfo.flame
        this.channelId = iConversationContext.chatChannelInfo.channelID
        this.channelType = iConversationContext.chatChannelInfo.channelType
        initFlame()
        viewBinding.toolbarRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        toolBarAdapter = WKChatToolBarAdapter()
        toolBarAdapter!!.animationEnable = false
        viewBinding.toolbarRecyclerView.adapter = toolBarAdapter
        //去除刷新条目闪动动画
        (Objects.requireNonNull(viewBinding.toolbarRecyclerView.itemAnimator) as DefaultItemAnimator).supportsChangeAnimations =
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
        toolBarAdapter!!.setList(tempToolBarList)
        toolBarAdapter!!.addChildClickViewIds(R.id.imageView)
        toolBarAdapter!!.setOnItemChildClickListener { adapter1: BaseQuickAdapter<*, *>, view: View, position: Int ->
            if (view.id == R.id.imageView) {
                SingleClickUtil.determineTriggerSingleClick(view, 500) {
                    val mChatToolBarMenu =
                        adapter1.getItem(position) as ChatToolBarMenu?
                            ?: return@determineTriggerSingleClick
                    if (mChatToolBarMenu.isDisable) return@determineTriggerSingleClick
                    // 如果点击的是@
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_remind") {
                        val index = viewBinding.editText.selectionStart
                        if (index != Objects.requireNonNull(viewBinding.editText.text)
                                .toString().length
                        ) {
                            viewBinding.editText.text.insert(
                                viewBinding.editText.selectionStart,
                                "@"
                            )
                        } else {
                            viewBinding.editText.append("@")
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
                                showNewImgDialog(path, iConversationContext)
                            }, 300)
                        }
                    }
                    //存在点击显示的view
                    if (mChatToolBarMenu.bottomView != null) {
                        if (mChatToolBarMenu.isSelected) {
                            //已经选中就隐藏底部view弹起软键盘
                            mChatToolBarMenu.isSelected = false
                            UIUtil.requestFocus(viewBinding.editText)
                            UIUtil.showSoftInput(context, viewBinding.editText)
                            handleAnimator(PanelType.INPUT_MOTHOD)
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
                            chatMorePanel.addBottomView(mChatToolBarMenu.bottomView)
                            mChatToolBarMenu.bottomView.startAnimation(
                                loadAnimation(
                                    iConversationContext
                                )
                            )
                            UIUtil.loseFocus(viewBinding.editText)
                            UIUtil.hideSoftInput(context, viewBinding.editText)
                            handleAnimator(PanelType.MORE)
                            onInputPanelStateChangedListener?.onShowMorePanel()
                        }
                    }
                    if (mChatToolBarMenu.iChatToolBarListener != null) mChatToolBarMenu.iChatToolBarListener.onChecked(
                        true,
                        iConversationContext
                    )
                }
            }
        }
        val drawable = RLottieDrawable(
            iConversationContext.chatActivity,
            R.raw.cancel_search,
            "cancel_search",
            AndroidUtilities.dp(35f),
            AndroidUtilities.dp(35f)
        )
        viewBinding.closeSearchLottieIV.setAutoRepeat(true)
        viewBinding.closeSearchLottieIV.setAnimation(drawable)
        viewBinding.closeSearchLottieIV.playAnimation()
        viewBinding.closeSearchLottieIV.setOnClickListener {
            viewBinding.editText.setText("")
            hideRobotView()
            CommonAnim.getInstance().showOrHide(viewBinding.closeSearchLottieIV, false, true)
            CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
            CommonAnim.getInstance().showOrHide(viewBinding.sendIV, true, true)
        }
        setListeners(iConversationContext)
        initRobotMenuData(iConversationContext)
        initRemindData(iConversationContext)
        initRobotGIF(iConversationContext)
        EndpointManager.getInstance().invoke(
            "initInputPanel",
            InitInputPanelMenu(
                this,
                iConversationContext,
                recyclerViewContentView
            )
        )
    }

    //相册有新的图片
    private fun showNewImgDialog(path: String, iConversationContext: IConversationContext) {
        R.mipmap.ic_arrow_drop_down
        WKSharedPreferencesUtil.getInstance().putSP("new_img_path", path)
        XPopup.Builder(iConversationContext.chatActivity)
            .hasShadowBg(false)
            .popupPosition(PopupPosition.Top)
            .atView(viewBinding.sendIV).offsetX(15)
            .asCustom(NewImgView(
                iConversationContext.chatActivity, path
            ) {
                //预览一张相册新增的图片
                iInputPanelListener!!.previewNewImg(path)
            })
            .show()
    }

    private fun loadAnimation(iConversationContext: IConversationContext): Animation? {
        return AnimationUtils.loadAnimation(
            iConversationContext.chatActivity,
            R.anim.anim_add_child
        )
    }

    private fun setListeners(iConversationContext: IConversationContext) {
        initRefreshListener()
        SingleClickUtil.onSingleClick(viewBinding.markdownIv) {
            EndpointManager.getInstance().invoke("show_rich_edit", iConversationContext)
        }
        viewBinding.sendIV.setOnClickListener {
            var content = StringUtils.replaceBlank(viewBinding.editText.text.toString())
            if (!TextUtils.isEmpty(content)) {
                content = viewBinding.editText.text.toString()
                viewBinding.sendIV.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(
                        context, R.color.popupTextColor
                    ), PorterDuff.Mode.MULTIPLY
                )
                val drawable = EmojiManager.getInstance().getDrawable(context, content)
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
                            viewBinding.editText.setText("")
                            lastInputTime = 0
                            return@setOnClickListener
                        }
                    }
                }
                val textMsgModel = WKTextContent(content)

                val list = viewBinding.editText.allUIDs
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
                textMsgModel.entities = viewBinding.editText.allEntity

                iConversationContext.sendMessage(textMsgModel)
                viewBinding.editText.setText("")
                lastInputTime = 0
            }
        }
        viewBinding.forwardView.setOnClickListener {
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
                            forwardContent.channelType = channelType
                            val list: MutableList<WKMsg> =
                                ArrayList()
                            forwardContent.userList = ArrayList()
                            var i = 0
                            val size: Int = chatAdapter.itemCount
                            while (i < size) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    list.add(chatAdapter.getItem(i).wkMsg)
                                    if (channelType == WKChannelType.PERSONAL) {
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
                                                    val setting = WKMsgSetting()
                                                    setting.receipt = mChannel.receipt
//                                                        setting.signal = 0
                                                    WKIM.getInstance()
                                                        .msgManager
                                                        .sendMessage(
                                                            forwardContent,
                                                            setting,
                                                            mChannel.channelID,
                                                            mChannel.channelType
                                                        )
                                                }
                                                WKToastUtils.getInstance()
                                                    .showToastNormal(context.getString(R.string.is_forward))

                                                for (index in toolBarAdapter!!.data.indices) {
                                                    toolBarAdapter!!.getItem(index).isDisable =
                                                        false
                                                }
                                                toolBarAdapter!!.notifyItemRangeChanged(
                                                    0,
                                                    toolBarAdapter!!.itemCount
                                                )
                                                viewBinding.multipleChoiceView.visibility = GONE
                                                viewBinding.toolbarRecyclerView.visibility =
                                                    VISIBLE
                                                if (iInputPanelListener != null) iInputPanelListener!!.onResetTitleView()
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
                            val size: Int = chatAdapter.itemCount
                            while (i < size) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    if ((chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_TEXT
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_IMAGE
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_GIF)
                                    ) list.add(chatAdapter.getItem(i).wkMsg.baseContentMsgModel) else {
                                        val textContent =
                                            WKTextContent(chatAdapter.getItem(i).wkMsg.baseContentMsgModel.getDisplayContent())
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
                                                            val setting = WKMsgSetting()
                                                            setting.receipt =
                                                                iConversationContext.chatChannelInfo.receipt
//                                                                setting.signal = signal
                                                            sendMsgEntityList.add(
                                                                SendMsgEntity(
                                                                    list[index],
                                                                    WKChannel(
                                                                        mChannel.channelID,
                                                                        mChannel.channelType
                                                                    ),
                                                                    setting
                                                                )
                                                            )
                                                        }
                                                    }

                                                    WKSendMsgUtils.getInstance()
                                                        .sendMessages(sendMsgEntityList)
                                                    WKToastUtils.getInstance()
                                                        .showToastNormal(context.getString(R.string.is_forward))
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
                                                    viewBinding.multipleChoiceView.visibility =
                                                        GONE
                                                    if (iInputPanelListener != null) iInputPanelListener!!.onResetTitleView()
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
                context,
                context.getString(R.string.base_forward),
                false,
                bottomSheetItemList
            )
        }

        viewBinding.editText.addTextChangedListener(object : TextWatcher {
            var linesCount = 0
            var lastHeight = AndroidUtilities.dp(35f)
            var start = 0
            var count = 0
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.count = count
                if (!TextUtils.isEmpty(s.toString())) {
                    val content = StringUtils.replaceBlank(s.toString())
//                    val content = s.toString().replace("\\s*|\r|\n|\t", "")
                    if (!isShowSendBtn && !TextUtils.isEmpty(content)) {
                        CommonAnim.getInstance().animImageView(viewBinding.sendIV)
                    }
                    isShowSendBtn = true

                    if (TextUtils.isEmpty(content)) {
                        viewBinding.sendIV.colorFilter = PorterDuffColorFilter(
                            ContextCompat.getColor(
                                context, R.color.popupTextColor
                            ), PorterDuff.Mode.MULTIPLY
                        )
                    } else {
                        viewBinding.sendIV.colorFilter = PorterDuffColorFilter(
                            Theme.colorAccount, PorterDuff.Mode.MULTIPLY
                        )
                    }
                    CommonAnim.getInstance().showOrHide(viewBinding.markdownIv, false, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(viewBinding.flameIV, false, true)
                    }
                } else {
                    CommonAnim.getInstance().showOrHide(viewBinding.markdownIv, true, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(viewBinding.flameIV, true, true)
                    }
                    isShowSendBtn = false
                    viewBinding.sendIV.colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(
                            context, R.color.popupTextColor
                        ), PorterDuff.Mode.MULTIPLY
                    )
                }
                val selectionStart = viewBinding.editText.selectionStart
                val selectionEnd = viewBinding.editText.selectionEnd
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
                            val endIndex = viewBinding.editText.selectionEnd
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
                    CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
                    CommonAnim.getInstance()
                        .showOrHide(viewBinding.closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(viewBinding.sendIV, true, true)
                }

                // 计算输入框高度
//                if (s.toString().isEmpty()) {
////                    viewBinding.editText.layoutParams.height = AndroidUtilities.dp(35f)
//                    linesCount = viewBinding.editText.lineCount
//                    return
//                }
//                if (viewBinding.editText.lineCount > 3) return
//                if (linesCount == 0) {
//                    linesCount = viewBinding.editText.lineCount
////                    viewBinding.editText.layoutParams.height = AndroidUtilities.dp(35f)
//                    return
//                }
//
//                val anim = ValueAnimator.ofInt(
//                            viewBinding.editText.layoutParams.height,
//                            AndroidUtilities.dp(35f)
//                        ).setDuration(150)
//                        anim.addUpdateListener { animation: ValueAnimator ->
//                            viewBinding.editText.layoutParams.height =
//                                animation.animatedValue as Int
//                            viewBinding.editText.requestLayout()
//                        }
                if (linesCount != viewBinding.editText.lineCount) {
                    linesCount = viewBinding.editText.lineCount
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
                                viewBinding.chatView
                            ) { viewBinding.editText.setText("") })
                } else {
                    EndpointManager.getInstance().invoke("hide_search_chat_edit_view", null)
                }

                //发送'正在输入'命令
                val nowTime = WKTimeUtils.getInstance().currentSeconds
                if (nowTime - lastInputTime >= 5 && !TextUtils.isEmpty(s)) {
                    var isSend = true
                    if (channelType == WKChannelType.GROUP) {
                        val mChannelMember =
                            WKIM.getInstance().channelMembersManager.getMember(
                                channelId,
                                channelType,
                                WKConfig.getInstance().uid
                            )
                        if (mChannelMember == null || mChannelMember.isDeleted == 1 || mChannelMember.status != 1) {
                            isSend = false
                        }
                    } else {
                        val channel = WKIM.getInstance().channelManager.getChannel(
                            channelId,
                            channelType
                        )
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
                        MsgModel.getInstance().typing(channelId, channelType)
                    }
                    lastInputTime = WKTimeUtils.getInstance().currentSeconds
                }
            }
        })
        viewBinding.deleteView.setOnClickListener {
            val chatAdapter = iConversationContext.chatAdapter
            val list: MutableList<WKMsg> = ArrayList()
            val ids = mutableListOf<String>()
            run {
                var i = 0
                val size: Int = chatAdapter.itemCount
                while (i < size) {
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
                        iInputPanelListener!!.onResetTitleView()
                        viewBinding.multipleChoiceView.visibility = GONE
                        viewBinding.toolbarRecyclerView.visibility = VISIBLE
                        var i = 0
                        val size: Int = chatAdapter.itemCount
                        while (i < size) {
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

//        WKIM.getInstance().channelMembersManager.addOnAddChannelMemberListener("InputPanel") {
//            if (remindMemberAdapter != null) remindMemberAdapter!!.queryGroupMembers(
//                channelId,
//                channelType
//            )
//        }
//        WKIM.getInstance().channelMembersManager.addOnRemoveChannelMemberListener("InputPanel") {
//            if (remindMemberAdapter != null) remindMemberAdapter!!.queryGroupMembers(
//                channelId,
//                channelType
//            )
//        }
        WKIM.getInstance().channelMembersManager.addOnRefreshChannelMemberInfo(
            "InputPanel"
        ) { mChannelMember, _ ->
            if (mChannelMember != null
                && mChannelMember.channelID.equals(channelId)
                && mChannelMember.channelType == channelType
                && channelType == WKChannelType.GROUP
            ) {
                //禁言
                if (mChannelMember.memberUID == WKConfig.getInstance().uid) {
                    showOrHideForbiddenView()
                }
            }
        }
    }

    fun showOrHideForbiddenView() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
        if (channelType == WKChannelType.CUSTOMER_SERVICE) {
            hideBan()
            return
        }
        val mChannel = WKIM.getInstance().channelManager.getChannel(
            channelId,
            channelType
        )
        val mChannelMember = WKIM.getInstance().channelMembersManager.getMember(
            channelId,
            channelType,
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

    fun initRefreshListener() {
        WKIM.getInstance().channelManager.addOnRefreshChannelInfo(
            "InputPanel"
        ) { mChannel, _ ->
            if (mChannel.channelType == channelType && mChannel.channelID.equals(channelId)) {
                showOrHideForbiddenView()
                // 封禁群
                if (mChannel.status == WKChannelStatus.statusDisabled) {
                    showBan()
                } else {
                    hideBan()
                }
                flame = mChannel.flame
                CommonAnim.getInstance().showOrHide(viewBinding.flameIV, flame == 1, true)
                viewBinding.markdownIv.visibility = if (flame == 1) View.GONE else View.VISIBLE
                showFlame(mChannel.flameSecond)
            }
        }
    }

    // 显示封禁
    fun showBan() {
        viewBinding.banView.visibility = VISIBLE
        viewBinding.forbiddenView.visibility = GONE
        viewBinding.chatView.visibility = GONE
        //  toolbarRecyclerView.visibility = GONE
        isDisableToolBar(true)
    }

    //隐藏封禁
    fun hideBan() {
        if (viewBinding.banView.visibility == GONE) return
        viewBinding.banView.visibility = GONE
        viewBinding.chatView.visibility = VISIBLE
        isDisableToolBar(false)
        // toolbarRecyclerView.visibility = VISIBLE
    }

    private fun showForbiddenWithMemberView(time: Long) {
        showForbiddenView()
        val nowTime = WKTimeUtils.getInstance().currentSeconds
        val day = (time - nowTime) / (3600 * 24)
        val hour = (time - nowTime) / 3600
        val min = (time - nowTime) / 60
        var showText = String.format(context.getString(R.string.forbidden_to_minute), 1)
        if (day > 0)
            showText = String.format(context.getString(R.string.forbidden_to_day), day)
        else {
            if (hour > 0) {
                showText = String.format(context.getString(R.string.forbidden_to_hour), hour)
            } else {
                if (min > 0) {
                    showText = String.format(context.getString(R.string.forbidden_to_minute), min)
                }
            }
        }
        showForbiddenTimer(time)
        AndroidUtilities.runOnUIThread {
            viewBinding.forbiddenTv.text = showText
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
                    AndroidUtilities.runOnUIThread { hideForbiddenView() }
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
                            context.getString(R.string.forbidden_detail_day),
                            dayStr,
                            hourStr,
                            minStr,
                            secondStr
                        )
                    } else {
                        if (hour > 0) {
                            content = String.format(
                                context.getString(R.string.forbidden_detail_hour),
                                hourStr,
                                minStr,
                                secondStr
                            )
                        } else {
                            content = if (min > 0) {
                                String.format(
                                    context.getString(R.string.forbidden_detail_minute),
                                    minStr,
                                    secondStr
                                )
                            } else {
                                String.format(
                                    context.getString(R.string.forbidden_detail_second),
                                    secondStr
                                )
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread { viewBinding.forbiddenTv.text = content }

                }
            }
        }
        timer!!.schedule(timerTask, 0, 1000)

    }

    private fun showForbiddenView() {
        UIUtil.hideSoftInput(context, viewBinding.editText)
        viewBinding.forbiddenView.visibility = VISIBLE
        viewBinding.chatView.visibility = GONE
        viewBinding.toolbarRecyclerView.visibility = GONE
        viewBinding.banView.visibility = GONE
    }

    private fun hideForbiddenView() {
        if (viewBinding.forbiddenView.visibility == GONE) return
        viewBinding.forbiddenView.visibility = GONE
        viewBinding.chatView.visibility = VISIBLE
        viewBinding.toolbarRecyclerView.visibility = VISIBLE
    }

    private fun initRobotMenuData(
        iConversationContext: IConversationContext,
    ) {
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
        this.menuRecyclerView!!.setView(this, this.menuHeaderView)
        robotMenuAdapter!!.addHeaderView(this.menuHeaderView!!)
        this.recyclerViewContentView!!.addView(this.menuRecyclerView)
        val menus = WKRobotModel.getInstance().getRobotMenus(channelId, channelType)
        menuRecyclerView!!.adapter = robotMenuAdapter
        menuRecyclerView!!.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        menuRecyclerView!!.addOnScrollListener(menuRecyclerView!!.onScrollListener)
        if (menus.size > 0) {
            robotMenuAdapter!!.setList(menus)
            CommonAnim.getInstance().showLeft2Right(viewBinding.menuView)
        }

        resetMenuHeader()

        viewBinding.menuLayout.setOnClickListener {
            viewBinding.menuLayout.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            if (robotMenuAdapter!!.data.size == 0) {
                val tempMenu: List<WKRobotMenuEntity> =
                    WKRobotModel.getInstance().getRobotMenus(channelId, channelType)
                robotMenuAdapter!!.setList(tempMenu)
            }
            menuRecyclerView!!.scrollToPosition(0)
            if (menuRecyclerView!!.visibility == View.VISIBLE) {
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
                viewBinding.menuLayout.performClick()
                val textContent = WKTextContent(menu.cmd)
                textContent.robotID = menu.robot_id
                val list: MutableList<WKMsgEntity> =
                    ArrayList()
                val entity = WKMsgEntity()
                entity.length = menu.cmd.length
                entity.offset = 0
                entity.type = "bot_command"
                list.add(entity)
                textContent.entities = list
                iConversationContext.sendMessage(textContent)
            }
        }
        // 监听机器人刷新菜单
        WKIM.getInstance().robotManager.addOnRefreshRobotMenu(channelId) {
            checkRobotMenu(iConversationContext)
        }
    }

    private fun resetMenuHeader() {
        this.post {
            var width = 40f
            if (robotMenuAdapter!!.data.size > 3) width = 48f
            menuHeaderView!!.layoutParams.height =
                top - AndroidUtilities.dp(
                    min(
                        robotMenuAdapter!!.data.size,
                        3
                    ) * width
                )
            if (lastPanelType != PanelType.NONE) {
                menuHeaderView!!.layoutParams.height -= WKConstants.getKeyboardHeight()
            }
            this.menuRecyclerView!!.setHeaderViewY(this.menuHeaderView!!.layoutParams.height.toFloat())
        }
    }

    private fun initRemindData(iConversationContext: IConversationContext) {
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
        remindRecycleView!!.setView(this, remindHeaderView)
        remindRecycleView!!.addOnScrollListener(remindRecycleView!!.onScrollListener)
        remindMemberAdapter = RemindMemberAdapter(channelId, channelType)
        remindRecycleView!!.adapter = remindMemberAdapter
        remindMemberAdapter!!.addHeaderView(remindHeaderView!!)
        remindMemberAdapter!!.onNormal()
        this.recyclerViewContentView!!.addView(this.remindRecycleView)
//        remindRecycleView!!.addIScrollListener { _, _ ->
//            val layoutManager = remindRecycleView!!.layoutManager as LinearLayoutManager
//            val lastCompletelyVisibleItemPosition =
//                layoutManager.findLastCompletelyVisibleItemPosition()
//            if (lastCompletelyVisibleItemPosition == layoutManager.itemCount - 1) {
//                remindMemberAdapter!!.loadMore()
//            }
//        }
        post {
            var height = 40f
            if (remindMemberAdapter!!.data.size > 3) height = 46f
            remindHeaderView!!.layoutParams.height =
                top - AndroidUtilities.dp(
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
                    viewBinding.editText.dispatchKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL
                        )
                    )
                }
                //追加一个@提醒并弹出软键盘
                viewBinding.editText.requestFocus()
                viewBinding.editText.addSpan("@$showName ", memberEntity.memberUID)
            }
        }
        this.remindRecycleView!!.visibility = GONE
    }

    private fun initRobotGIF(iConversationContext: IConversationContext) {

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
        recyclerViewContentView!!.addView(robotGifRecyclerView)
        post {
            robotGifHeaderView!!.layoutParams.height =
                this.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
        robotGifRecyclerView!!.setView(this, robotGifHeaderView)
        robotGIFAdapter!!.setOnItemClickListener { adapter, _, position ->
            val entity = adapter.data[position] as WKRobotGIFEntity
            if (entity.isNull) return@setOnItemClickListener
            hideRobotView()
            val stickerContent = WKGifContent()
            stickerContent.height = entity.height
            stickerContent.width = entity.width
            stickerContent.url = entity.url
            iConversationContext.sendMessage(stickerContent)
            viewBinding.editText.setText("")
            CommonAnim.getInstance().showOrHide(viewBinding.closeSearchLottieIV, false, true)
            CommonAnim.getInstance().showOrHide(viewBinding.sendIV, true, true)
            CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
        }
        this.robotGifRecyclerView!!.visibility = GONE
    }

    fun onBackListener(): Boolean {
        if (menuRecyclerView!!.visibility == visibility) {
            resetMenuIv()
            CommonAnim.getInstance().hideTop2Bottom(menuRecyclerView)
            return true
        }
        return false
    }

    fun onDestroy() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
        EndpointManager.getInstance().remove("emoji_click")
        WKIM.getInstance().robotManager.removeRefreshRobotMenu(channelId)
        WKIM.getInstance().channelManager.removeRefreshChannelInfo("InputPanel")
        WKIM.getInstance().channelMembersManager.removeRefreshChannelMemberInfo("InputPanel")
//        WKIM.getInstance().channelMembersManager.removeAddChannelMemberListener("InputPanel")
//        WKIM.getInstance().channelMembersManager.removeRemoveChannelMemberListener("InputPanel")
    }

    private fun checkRobotMenu(iConversationContext: IConversationContext) {
        val robotMembers =
            WKIM.getInstance().channelMembersManager.getRobotMembers(channelId, channelType)
        if ((iConversationContext.chatChannelInfo.robot == 1 || robotMembers != null) && robotMembers.size > 0) {
            if (viewBinding.menuView.visibility == GONE) {
                CommonAnim.getInstance().showLeft2Right(viewBinding.menuView)
            }
//            if (menuRecyclerView!!.visibility == visibility && robotMenuAdapter!!.data.size == 0) {
            val menus = WKRobotModel.getInstance().getRobotMenus(channelId, channelType)
            robotMenuAdapter!!.setList(menus)
            resetMenuHeader()
//            }
        }

    }


    private fun handleAnimator(panelType: PanelType) {
        if (lastPanelType == panelType) {
            return
        }
        isActive = true
        this.panelType = panelType
        var fromValue = 0.0f
        var toValue = 0.0f
        when (panelType) {
            PanelType.VOICE -> {
                when (lastPanelType) {
                    PanelType.INPUT_MOTHOD -> {
                        fromValue = -KeyboardHelper.inputPanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    PanelType.EXPRESSION -> {
                        fromValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    PanelType.MORE -> {
                        fromValue = -KeyboardHelper.morePanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    PanelType.NONE -> {
                        fromValue = 0.0f
                        toValue = 0.0f
                    }

                    else -> {
                    }
                }
            }

            PanelType.INPUT_MOTHOD ->
                when (lastPanelType) {
                    PanelType.VOICE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.inputPanelHeight.toFloat()
                    }

                    PanelType.EXPRESSION -> {
                        fromValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                        toValue = -KeyboardHelper.inputPanelHeight.toFloat()
                    }

                    PanelType.MORE -> {
                        fromValue = -KeyboardHelper.morePanelHeight.toFloat()
                        toValue = -KeyboardHelper.inputPanelHeight.toFloat()
                    }

                    PanelType.NONE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.inputPanelHeight.toFloat()
                    }

                    else -> {
                    }
                }

            PanelType.EXPRESSION ->
                when (lastPanelType) {
                    PanelType.INPUT_MOTHOD -> {
                        fromValue = -KeyboardHelper.inputPanelHeight.toFloat()
                        toValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                    }

                    PanelType.VOICE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                    }

                    PanelType.MORE -> {
                        fromValue = -KeyboardHelper.morePanelHeight.toFloat()
                        toValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                    }

                    PanelType.NONE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                    }

                    else -> {
                    }
                }

            PanelType.MORE ->
                when (lastPanelType) {
                    PanelType.INPUT_MOTHOD -> {
                        fromValue = -KeyboardHelper.inputPanelHeight.toFloat()
                        toValue = -KeyboardHelper.morePanelHeight.toFloat()
                    }

                    PanelType.VOICE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.morePanelHeight.toFloat()
                    }

                    PanelType.EXPRESSION -> {
                        fromValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                        toValue = -KeyboardHelper.morePanelHeight.toFloat()
                    }

                    PanelType.NONE -> {
                        fromValue = 0.0f
                        toValue = -KeyboardHelper.morePanelHeight.toFloat()
                    }

                    else -> {
                    }
                }

            PanelType.NONE ->
                when (lastPanelType) {
                    PanelType.VOICE -> {
                        // from 0.0f to 0.0f
                    }

                    PanelType.INPUT_MOTHOD -> {
                        fromValue = -KeyboardHelper.inputPanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    PanelType.EXPRESSION -> {
                        fromValue = -KeyboardHelper.expressionPanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    PanelType.MORE -> {
                        fromValue = -KeyboardHelper.morePanelHeight.toFloat()
                        toValue = 0.0f
                    }

                    else -> {
                    }
                }
        }
        onLayoutAnimatorHandleListener?.invoke(panelType, lastPanelType, fromValue, toValue)
        lastPanelType = panelType
        resetRecyclerView(fromValue, toValue)
//        resetRecyclerView(this.robotGifRecyclerView!!, fromValue, toValue)
//        resetRecyclerView(this.menuRecyclerView!!, fromValue, toValue)
//        if (iConversationContext!!.chatChannelInfo.channelType == WKChannelType.GROUP) {
//            resetRecyclerView(this.remindRecycleView!!, fromValue, toValue)
//        }
    }

    private var onLayoutAnimatorHandleListener: ((panelType: PanelType, lastPanelType: PanelType, fromValue: Float, toValue: Float) -> Unit)? =
        null
    private var onInputPanelStateChangedListener: OnInputPanelStateChangedListener? = null
    override fun onSoftKeyboardOpened() {
        isKeyboardOpened = true
    }

    override fun onSoftKeyboardClosed() {
        isKeyboardOpened = false
        if (lastPanelType == PanelType.INPUT_MOTHOD) {
            UIUtil.loseFocus(viewBinding.editText)
            UIUtil.hideSoftInput(context, viewBinding.editText)
            handleAnimator(PanelType.NONE)
        }
    }

    override fun setOnLayoutAnimatorHandleListener(listener: ((panelType: PanelType, lastPanelType: PanelType, fromValue: Float, toValue: Float) -> Unit)?) {
        this.onLayoutAnimatorHandleListener = listener
    }

    override fun setOnInputStateChangedListener(listener: OnInputPanelStateChangedListener?) {
        this.onInputPanelStateChangedListener = listener
    }

    override fun reset() {
        if (!isActive) {
            return
        }
        UIUtil.loseFocus(viewBinding.editText)
        UIUtil.hideSoftInput(context, viewBinding.editText)
        handleAnimator(PanelType.NONE)
        resetToolBar()
        isActive = false
    }

    override fun getPanelHeight(): Int {
        return KeyboardHelper.keyboardHeight
    }

    fun addInputPanelListener(iInputPanelListener: IInputPanelListener) {
        this.iInputPanelListener = iInputPanelListener
    }

    interface IInputPanelListener {
        fun onResetTitleView()
        fun previewNewImg(path: String)
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun resetRecyclerView(
        fromValue: Float,
        toValue: Float
    ) {
        val recyclerViewTranslationYAnimator: ObjectAnimator =
            ObjectAnimator.ofFloat(
                recyclerViewContentView,
                "translationY",
                fromValue,
                toValue
            )
        val animatorSet = AnimatorSet()
        animatorSet.duration = 250
        animatorSet.play(recyclerViewTranslationYAnimator)
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                recyclerViewContentView!!.requestLayout()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animatorSet.start()

        val chatUnreadViewAnimator: ObjectAnimator =
            ObjectAnimator.ofFloat(
                chatUnreadView,
                "translationY",
                fromValue,
                toValue
            )
        val chatUnreadAnimatorSet = AnimatorSet()
        chatUnreadAnimatorSet.duration = 250
        chatUnreadAnimatorSet.play(chatUnreadViewAnimator)
        chatUnreadAnimatorSet.interpolator = DecelerateInterpolator()
        chatUnreadAnimatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                chatUnreadView!!.requestLayout()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        chatUnreadAnimatorSet.start()


    }

    fun hideRemindView() {
        if (channelType == WKChannelType.GROUP && remindRecycleView!!.visibility != GONE) {
            CommonAnim.getInstance().hideTop2Bottom(remindRecycleView!!)
        }
    }

    private fun hideRobotView() {
        if (robotGifRecyclerView!!.visibility != GONE) {
            CommonAnim.getInstance().hideTop2Bottom(robotGifRecyclerView!!)
//            initRobotGIF(iConversationContext!!)
            robotGifHeaderView!!.layoutParams.height =
                this.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
    }

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
                if (mRobot != null && index != 0 && viewBinding.editText.text.toString()
                        .startsWith("@") && viewBinding.editText.text.toString()
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

                        CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
                        inlineQueryOffset = ""
//                        if (TextUtils.isEmpty(searchKey)) {
//                            if (this.robotGifRecyclerView!!.visibility != View.GONE) {
//                                CommonAnim.getInstance().hideTop2Bottom(this.robotGifRecyclerView)
//                            }
//                        } else
                        searchRobotGif(searchKey, username)
                    } else {
                        val mTextPaint: TextPaint = viewBinding.editText.paint
                        val textWidth = mTextPaint.measureText(viewBinding.editText.text.toString())
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
                            viewBinding.hitTv.hint = mRobot.placeholder
                            CommonAnim.getInstance().showOrHide(viewBinding.hitTv, true, true)
                            val lp = RelativeLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.WRAP_CONTENT
                            )
                            lp.topMargin = AndroidUtilities.dp(8f)
                            lp.leftMargin = textWidth.toInt() + AndroidUtilities.dp(10f)
                            viewBinding.hitTv.layoutParams = lp
                        } else {
                            CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
                        }
                    }
                    CommonAnim.getInstance().showOrHide(viewBinding.sendIV, false, true)
                    CommonAnim.getInstance().showOrHide(viewBinding.closeSearchLottieIV, true, true)

                } else {
                    CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
                    CommonAnim.getInstance()
                        .showOrHide(viewBinding.closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(viewBinding.sendIV, true, true)

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
                CommonAnim.getInstance().showOrHide(viewBinding.hitTv, false, true)
                CommonAnim.getInstance().showOrHide(viewBinding.closeSearchLottieIV, false, true)
                CommonAnim.getInstance().showOrHide(viewBinding.sendIV, true, true)
                hideRobotView()
            }
        }
        if (channelType == WKChannelType.GROUP && isSearchGroupMembers) {
            var remindSearchKey: String = content

            remindSearchKey = remindSearchKey.replace("@".toRegex(), "")
//            val keyword = mentionEnd(content)
            Log.e("开始搜索内容了", "-->$remindSearchKey,$content")
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
                this.top - AndroidUtilities.dp((min * height))
            remindRecycleView!!.setHeaderViewY(remindHeaderView!!.layoutParams.height.toFloat())
            if (remindRecycleView!!.visibility == GONE) CommonAnim.getInstance()
                .showBottom2Top(remindRecycleView)
        }
    }

    private fun searchRobotGif(searchKey: String, username: String) {
        this.searchKey = searchKey
        this.username = username
        WKRobotModel.getInstance().inlineQuery(
            inlineQueryOffset, username, searchKey, channelId, channelType
        ) { _: Int, _: String?, result: WKRobotInlineQueryResult? ->
            if (TextUtils.isEmpty(inlineQueryOffset)) {
                robotGifRecyclerView!!.scrollToPosition(0)
                robotGifHeaderView!!.layoutParams.height =
                    this.top - AndroidUtilities.dp(100f)
                this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
            }
            if (result?.results != null && result.results.size > 0) {
                if (TextUtils.isEmpty(inlineQueryOffset)) robotGIFAdapter!!.setList(result.results) else robotGIFAdapter!!.addData(
                    result.results
                )
                resetData()
                inlineQueryOffset = result.next_offset
                if (this.robotGifRecyclerView!!.visibility != visibility) {
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

    fun updateForwardView(num: Int) {
        if (num > 0) {
            viewBinding.forwardView.isEnabled = true
            viewBinding.deleteTv.setTextColor(ContextCompat.getColor(context, R.color.colorDark))
            viewBinding.forwardTv.setTextColor(ContextCompat.getColor(context, R.color.colorDark))
            viewBinding.deleteIv.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    context, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
            viewBinding.forwardIv.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    context, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
        } else {
            viewBinding.forwardView.isEnabled = false
            viewBinding.deleteTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
            viewBinding.forwardTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
            viewBinding.deleteIv.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    context, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
            viewBinding.forwardIv.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    context, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
        }
    }

    private fun initFlame() {
        if (flame == 1) {
            viewBinding.flameIV.visibility = View.VISIBLE
            CommonAnim.getInstance().showOrHide(viewBinding.flameIV, true, true)
            viewBinding.markdownIv.visibility = View.GONE
        } else
            viewBinding.markdownIv.visibility = View.VISIBLE
        seekBarView = SeekBarView(context, false)
        seekBarView.setColors(
            Theme.color999,
            Theme.colorAccount
        )
        viewBinding.seekBarLayout.addView(
            seekBarView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30)
        )
        seekBarView.setDelegate(object : SeekBarView.SeekBarViewDelegate {
            override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                if (stop)
                    setProgress(progress)
            }

            override fun onSeekBarPressed(pressed: Boolean) {
            }
        })
        viewBinding.flameIV.setOnClickListener {
            if (viewBinding.flameLayout.visibility == View.GONE) {
                CommonAnim.getInstance().animateOpen(
                    viewBinding.flameLayout,
                    0,
                    AndroidUtilities.dp(85f)
                )
            } else {
                CommonAnim.getInstance().animateClose(viewBinding.flameLayout)
            }
        }
        viewBinding.burnSwitchView.setOnCheckedChangeListener { v, isChecked ->
            run {
                if (v.isPressed) {
                    if (channelType == WKChannelType.PERSONAL) {
                        FriendModel.getInstance().updateUserSetting(
                            channelId, "flame", if (isChecked) 1 else 0
                        ) { code: Int, msg: String? ->
                            if (code != HttpResponseCode.success.toInt()) {
                                viewBinding.burnSwitchView.isChecked = !isChecked
                                WKToastUtils.getInstance().showToast(msg)
                            } else {
                                if (!isChecked) {
                                    CommonAnim.getInstance().animateClose(viewBinding.flameLayout)
                                }
                            }
                        }
                    } else {
                        GroupModel.getInstance().updateGroupSetting(
                            channelId,
                            "flame",
                            if (isChecked) 1 else 0
                        ) { code, msg ->
                            if (code != HttpResponseCode.success.toInt()) {
                                viewBinding.burnSwitchView.isChecked = !isChecked
                                WKToastUtils.getInstance().showToast(msg)
                            } else {
                                if (!isChecked) {
                                    CommonAnim.getInstance().animateClose(viewBinding.flameLayout)
                                }
                            }
                        }
                    }
                }
            }
        }
        showFlame(iConversationContext!!.chatChannelInfo.flameSecond)
    }

    private fun showFlame(flameSecond: Int) {
        viewBinding.burnSwitchView.isChecked = flame == 1
        if (flame == 0 && viewBinding.flameLayout.visibility == View.VISIBLE) {
            CommonAnim.getInstance().animateClose(viewBinding.flameLayout)
        }
        var content: String? = ""
        when (flameSecond) {
            0 -> {
                content = context.getString(R.string.burn_time_0)
                seekBarView.setProgress(0f, true)
            }

            10 -> {
                content = context.getString(R.string.time_10)
                seekBarView.setProgress(10 / 180f, true)
            }

            20 -> {
                content = context.getString(R.string.time_20)
                seekBarView.setProgress(20 / 180f, true)
            }

            30 -> {
                content = context.getString(R.string.time_30)
                seekBarView.setProgress(30 / 180f, true)
            }

            60 -> {
                content = context.getString(R.string.time_60)
                seekBarView.setProgress(60 / 180f, true)
            }

            120 -> {
                content = context.getString(R.string.time_120)
                seekBarView.setProgress(120 / 180f, true)
            }

            180 -> {
                content = context.getString(R.string.time_180)
                seekBarView.setProgress(180 / 180f, true)
            }
        }
        if (flameSecond == 0) {
            viewBinding.burnTimeTv.text = content
        } else viewBinding.burnTimeTv.text = String.format(
            context.getString(R.string.burn_time_desc),
            content
        )
    }

    private fun setProgress(progress: Float) {
        val seekPg = progress * 180
        val newProgress: Int
        val content: String
        if (seekPg < 5) {
            newProgress = 0
            content = context.getString(R.string.burn_time_0)
            seekBarView.setProgress(0f, true)
        } else if (seekPg in 5.0..15.0) {
            newProgress = 10
            content = context.getString(R.string.time_10)
        } else if (seekPg > 15 && seekPg <= 25) {
            newProgress = 20
            content = context.getString(R.string.time_20)
        } else if (seekPg > 25 && seekPg <= 35) {
            newProgress = 30
            content = context.getString(R.string.time_30)
        } else if (seekPg > 35 && seekPg <= 90) {
            newProgress = 60
            content = context.getString(R.string.time_60)
        } else if (seekPg > 90 && seekPg <= 150) {
            newProgress = 120
            content = context.getString(R.string.time_120)
        } else {
            newProgress = 180
            content = context.getString(R.string.time_180)
        }
        if (newProgress == 0) {
            viewBinding.burnTimeTv.text = content
        } else viewBinding.burnTimeTv.text = String.format(
            context.getString(R.string.burn_time_desc),
            content
        )
        seekBarView.setProgress(newProgress.toFloat() / 180, true)
        if (channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(
                channelId, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (code != HttpResponseCode.success.toInt()) {
                    WKToastUtils.getInstance().showToast(msg)
                }
            }
        } else {
            GroupModel.getInstance().updateGroupSetting(
                channelId, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (code != HttpResponseCode.success.toInt()) {
                    WKToastUtils.getInstance().showToastNormal(msg)
                }
            }
        }
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
        val emojiLayout = LinearLayout(context)
        val emojiAdapter = EmojiAdapter(list, width)
        val recyclerView = RecyclerView(context)
        val emojiLayoutManager = GridLayoutManager(context, 8)
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
            val curPosition: Int = viewBinding.editText.selectionStart
            val sb = java.lang.StringBuilder(
                Objects.requireNonNull(viewBinding.editText.text).toString()
            )
            sb.insert(curPosition, emojiName)
            MoonUtil.addEmojiSpan(viewBinding.editText, emojiName, context)
            viewBinding.editText.setSelection(curPosition + emojiName.length)
        }
        return emojiLayout
    }
}