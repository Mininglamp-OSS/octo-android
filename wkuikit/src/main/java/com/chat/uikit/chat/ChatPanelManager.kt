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

package com.chat.uikit.chat

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.view.animation.AccelerateDecelerateInterpolator
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.emoji.EmojiAdapter
import com.chat.base.emoji.EmojiEntry
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
import com.chat.base.msg.MessageForwardSupport
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msg.ChatContentSpanType
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
import com.chat.uikit.chat.adapter.WKRichTextTrayAdapter
import com.chat.uikit.view.voice.HoldToTalkManager
import com.chat.uikit.chat.manager.SendMsgEntity
import com.chat.uikit.chat.manager.WKRichTextComposeModel
import com.chat.uikit.chat.manager.WKRichTextSender
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent
import com.chat.uikit.contacts.service.FriendModel
import com.chat.uikit.chat.sticker.CollectStickerAdapter
import com.chat.uikit.chat.sticker.StickerUrlUtils
import com.chat.uikit.chat.sticker.WKSticker
import com.chat.uikit.chat.sticker.WKStickerManager
import com.chat.uikit.chat.sticker.WKStickerUploader
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.ChooseResult
import com.chat.base.msg.model.WKVectorStickerContent
import com.chat.uikit.group.GroupMemberEntity
import com.chat.uikit.group.RemindMemberAdapter
import com.chat.uikit.group.service.GroupModel
import com.chat.uikit.message.MsgModel
import com.chat.uikit.robot.RobotGIFAdapter
import com.chat.uikit.robot.RobotMenuAdapter
import com.chat.uikit.robot.SlashCommandAdapter
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
import com.chat.base.msg.WKMentionTextContent
import android.app.AlertDialog
import com.chat.base.msgcontent.WKFileContent
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.Objects
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min
import androidx.core.view.isGone


class ChatPanelManager(
    val helper: PanelSwitchHelper,
    val parentView: View,
    private val moreLayout: FrameLayout,
    private val followScrollLayout: FrameLayout,
    val iConversationContext: IConversationContext,
    val resetTitleViewListener: () -> Unit,
    val showNewImageListener: (path: String) -> Unit,
) {
    private companion object {
        /** 内置表情面板的工具栏 sid，见 initToolBar() 里兜底添加的那颗。 */
        const val EMOJI_TOOL_BAR_SID = "emojiToolBar"

        /** 外部模块自带表情面板时用的 sid —— 存在它就不再兜底加内置表情面板。 */
        const val STICKER_TOOL_BAR_SID = "chat_toolbar_sticker"

        /**
         * emoji 网格底部要让出来的高度：悬浮删除键 5dp 下边距 + 5dp 阴影 + 36dp 视觉 + 5dp 阴影
         * = 51dp，取 52dp。作为 RecyclerView 的 bottom padding，让最后一行能滚到按钮上方、点得到。
         *
         * 按钮尺寸改了（view_emoji_panel_delete_btn.xml）就要回来改这里。
         */
        const val EMOJI_PANEL_BOTTOM_INSET_DP = 52f
    }

    private val eventKey = "InputPanel"
    private val loginUID = WKConfig.getInstance().uid
    private var isShowSendBtn: Boolean = false
    private var flame = 0
    private var lastInputTime: Long = 0
    private var inlineQueryOffset: String = ""
    private var searchKey: String = ""
    private var username: String = ""
    private val messageTextMaxBytes = 10 * 1024 // 10KB

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
    private val voiceToggleBtn: AppCompatImageView = parentView.findViewById(R.id.voiceToggleBtn)
    private val holdToTalkBtn: View = parentView.findViewById(R.id.holdToTalkBtn)
    private val editTextContainer: RelativeLayout = parentView.findViewById(R.id.editTextContainer)
    private var isVoiceMode = false
    private var holdToTalkManager: HoldToTalkManager? = null
    private var isResultMode = false
    private var resultBubbleEditText: android.widget.EditText? = null
    private var appendOverlay: View? = null
    private var appendWaveBars: List<View>? = null
    private var appendHintLabel: AppCompatTextView? = null
    private var appendWaveTimer: Timer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thinkingOverlayView: View? = null
    private var thinkingDots: List<View>? = null
    private var thinkingAnimator: ValueAnimator? = null
    private var resultBottomViews: List<View>? = null
    private var resultBubbleContainer: FrameLayout? = null
    private var flameLayout: LinearLayout? = null

    // 相册有新图
    private var newImageLayout: LinearLayout? = null

    // 图文混排（RichText=14）输入框附件托盘（Phase 2，对齐 web#237）。
    // 选中的静态图以缩略图挂在输入框上方，可继续打字 / 多批追加 / 拖拽调序 / 单张移除；
    // 点发送时按托盘真实当前顺序整体打成单条 type=14（文本块在前 + 图片按托盘顺序；真·块级
    // 文/图交错留 Phase 3）。模型纯数据、可单测；
    // trayLayout / trayRecyclerView 只是它的渲染镜像。仅主线程读写。
    private val richTextTray = WKRichTextComposeModel()
    private var trayLayout: LinearLayout? = null
    private var trayRecyclerView: RecyclerView? = null
    private var trayAdapter: WKRichTextTrayAdapter? = null

    // Sticker panel adapter：仅在 buildStickerContentView 内使用，字段保留是为了未来加
    // "重排 / 管理"等入口时不用改签名。当前长按走 StickerDetailPopup 预览，不再持有
    // 编辑态。
    private var stickerAdapter: CollectStickerAdapter? = null
    // 托盘发送 in-flight 防重入（对齐 Phase 1 YUJ-2872 defect b / web#227 sendingRef）：
    // 一条托盘发送在等图片异步上传期间，托盘 + 文本仍留在 UI（仅内存，进程死会丢失托盘图片，
    // 文本草稿另有持久化）。若此时用户再点发送键，会把同一批 tray + text 再打一条 → 重复
    // type=14。此标志在发送发起时置 true、入队回调（或切频道清空）时复位，期间吞掉重复发送。
    // pendingRichTextSnapshot 只挡纯文本路径，纯图片托盘无文本快照，故必须用本标志兜住图片重复。
    private var richTextTraySending = false

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

    // slash command popup
    private var slashCommandPopup: PopupWindow? = null
    private var slashCommandAdapter: SlashCommandAdapter? = null
    private var suppressSlashPopup = false
    private var lastHeight = 0
    private var lastTargetLines = 1 // 追踪上一次的目标行数
    private val maxLines: Int = 3

    init {
        this.menuView.background = Theme.getBackground(Theme.colorAccount, 30f)
        // 设置输入框的初始行数
        editText.setMinLines(1)
        editText.setMaxLines(maxLines)
        initListener()
        initRemind()
        initRobotGIF()
        initRobotMenu()
        initSlashCommandPopup()
        initTool()
        initMultipleChoiceView()
        initBanView()
        initForbiddenView()
        initChatTopView()
        initFlame()
        initNewImageView()
        initRichTextTray()
        initHoldToTalk()
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
        if (isResultMode) {
            holdToTalkManager?.cancelResult()
            return false
        }
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
        suppressSlashPopup = true
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
        suppressSlashPopup = false
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

    /**
     * 文本字节是否超过输入框上限（与发送键文本路径同一阈值）。图文混排聚合发送前用它做
     * 同源校验，避免绕过 [showTextToFileAlert] 发出超限 payload。
     */
    fun isTextOverByteLimit(text: String): Boolean {
        return messageTextMaxBytes > 0 && text.toByteArray(Charsets.UTF_8).size > messageTextMaxBytes
    }

    /** 弹出"转为文件发送"确认框（供超限文本复用，与发送键路径同源）。 */
    fun promptTextToFile(text: String) {
        showTextToFileAlert(text)
    }

    /**
     * 把当前输入框的 @mention（三态 humans/ais + 群成员 uids）应用到给定消息体。
     *
     * 供图文混排（RichText=14）聚合发送复用——与发送键文本路径【同源】，
     * 保证群 @ 通知（含 @所有AI）不丢。同时写入 mention.entities（offset/length/uid），
     * 使接收侧可高亮并点击 @mention 跳转个人名片。
     *
     * 复用既有 [scanPlainTextMentions] / [expandRobotMembersIntoUids]，与 sendIV 文本
     * 发送逻辑保持单一来源。
     */
    fun applyInputMentionsTo(content: WKMessageContent, text: String) {
        val list = editText.allUIDs.toMutableList()
        val entities = editText.allEntity.toMutableList()

        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP ||
            iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC
        ) {
            scanPlainTextMentions(text, entities, list)
        }

        if (list.isEmpty()) return

        val mInfo = WKMentionInfo()
        val uidList: MutableList<String> = ArrayList()
        for (uid in list) {
            when {
                uid.equals("-1", ignoreCase = true) -> {
                    content.mentionHumans = 1
                    mInfo.humans = true
                }
                uid == "-2" -> {
                    content.mentionAis = 1
                    mInfo.ais = true
                }
                else -> uidList.add(uid)
            }
        }
        // @所有AI 命中时把会话内 robot 成员展开到 mention.uids，兼容旧 adapter。
        if (content.mentionAis == 1) {
            expandRobotMembersIntoUids(uidList)
        }
        mInfo.uids = uidList
        content.mentionInfo = mInfo

        val mentionEntities = entities.filter { it.type == ChatContentSpanType.mention }
        if (mentionEntities.isNotEmpty()) {
            content.entities = mentionEntities
        }
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
                mMsg.baseContentMsgModel?.displayContent ?: ""
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
        val textModel = mMsg.baseContentMsgModel as? WKTextContent ?: return
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
        WKIM.getInstance().channelMembersManager.addOnAddChannelMemberListener(this.eventKey) { list ->
            for (channelMember in list) {
                if (channelMember.memberUID == loginUID) {
                    showOrHideForbiddenView()
                    break
                }
            }
        }
        WKIM.getInstance().channelMembersManager.addOnRefreshChannelMemberInfo(
            this.eventKey
        ) { mChannelMember, _ ->
            if (mChannelMember != null
                && mChannelMember.channelID.equals(iConversationContext.chatChannelInfo.channelID)
                && mChannelMember.channelType == iConversationContext.chatChannelInfo.channelType
                && iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP
            ) {
                //禁言
                if (mChannelMember.memberUID == this.loginUID) {
                    showOrHideForbiddenView()
                }
            }
        }
        WKIM.getInstance().channelManager.addOnRefreshChannelInfo(
            this.eventKey
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
                markdownIv.visibility = View.GONE
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
            this.loginUID
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
            if (uid == this.loginUID) return
            if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP ||
                iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) {
                val loginMember = WKIM.getInstance().channelMembersManager.getMember(
                    iConversationContext.chatChannelInfo.channelID,
                    iConversationContext.chatChannelInfo.channelType,
                    this.loginUID
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

    /**
     * 文本超出字节限制时弹窗提示，确认后转为 .txt 文件发送
     */
    private fun showTextToFileAlert(text: String) {
        val context = iConversationContext.chatActivity
        AlertDialog.Builder(context)
            .setMessage(context.getString(com.chat.base.R.string.str_text_exceed_limit_tip))
            .setNegativeButton(context.getString(com.chat.base.R.string.cancel), null)
            .setPositiveButton(context.getString(com.chat.base.R.string.str_confirm_send)) { _, _ ->
                sendTextAsFile(text)
            }
            .show()
    }

    /**
     * 将文本内容生成 .txt 文件并以文件消息发送
     */
    private fun sendTextAsFile(text: String) {
        val context = iConversationContext.chatActivity
        // 用前10个字符作为文件名
        var namePrefix = if (text.length > 10) text.substring(0, 10) else text
        // 移除文件名中的非法字符
        namePrefix = namePrefix.replace(Regex("[/\\\\:*?\"<>|\\n\\r\\t]"), "")
        if (namePrefix.isEmpty()) {
            namePrefix = context.getString(com.chat.base.R.string.str_default_text_file_name)
        }
        val fileName = "$namePrefix.txt"

        // 写入临时文件
        val tmpDir = File(context.cacheDir, "WKTextToFile")
        tmpDir.mkdirs()
        val file = File(tmpDir, fileName)
        file.writeText(text, Charsets.UTF_8)

        // 创建文件消息并发送
        val fileContent = WKFileContent()
        fileContent.localPath = file.absolutePath
        fileContent.name = fileName
        fileContent.extension = ".txt"
        fileContent.size = file.length()

        iConversationContext.sendMessage(fileContent)

        // 清空输入框
        editText.text = null
        lastInputTime = 0
        if (chatTopView?.visibility == View.VISIBLE) {
            CommonAnim.getInstance().animateClose(chatTopView)
        }
    }

    fun onDestroy() {
        dismissSlashCommandPopup()
        slashCommandPopup = null
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
        releaseHoldToTalk()
        stopEmojiDeleteRepeat()
        EndpointManager.getInstance().remove("emoji_click")
        WKIM.getInstance().robotManager.removeRefreshRobotMenu(iConversationContext.chatChannelInfo.channelID)
        WKIM.getInstance().channelManager.removeRefreshChannelInfo(this.eventKey)
        WKIM.getInstance().channelMembersManager.removeRefreshChannelMemberInfo(this.eventKey)
        WKIM.getInstance().channelMembersManager.removeAddChannelMemberListener(this.eventKey)
    }


    private fun initFlame() {
        flame = iConversationContext.chatChannelInfo.flame
        initFlameView()
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        if (flame == 1) {
            flameIV.visibility = View.VISIBLE
            CommonAnim.getInstance().showOrHide(flameIV, true, true)
        }
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
                if (menu.sid.equals(STICKER_TOOL_BAR_SID)) {
                    isAddEmojiLayout = false
                }
                tempToolBarList.add(menu)
            }
        }
        if (isAddEmojiLayout) {
            val emojiToolBar = ChatToolBarMenu(
                EMOJI_TOOL_BAR_SID,
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
        if (menus.isNotEmpty()) {
            robotMenuAdapter!!.setList(menus)
        }

        resetMenuHeader()

        menuLayout.setOnClickListener {
            menuLayout.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )

            if (robotMenuAdapter!!.data.isEmpty()) {
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
                Log.e("robotId", menu.robot_id)
                WKSendMsgUtils.getInstance().sendMessage(wkMsg)
            }
        }
        // 监听机器人刷新菜单
        WKIM.getInstance().robotManager.addOnRefreshRobotMenu(iConversationContext.chatChannelInfo.channelID) {
            checkRobotMenu(iConversationContext)
            refreshSlashCommandMenus()
        }

    }

    private fun refreshSlashCommandMenus() {
        if (slashCommandAdapter == null) return
        var menus = WKRobotModel.getInstance().getRobotMenus(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        if (menus.isEmpty() && "botfather" == iConversationContext.chatChannelInfo.channelID) {
            menus = botFatherFallbackMenus()
        }
        slashCommandAdapter!!.setAllItems(menus)
    }

    private fun initSlashCommandPopup() {
        if (iConversationContext.chatChannelInfo.channelType != WKChannelType.PERSONAL) return
        val channelID = iConversationContext.chatChannelInfo.channelID
        val isBotChannel = iConversationContext.chatChannelInfo.robot == 1
                || com.chat.base.space.SystemBotsFallback.isSystemBot(channelID)
        if (!isBotChannel) return

        slashCommandAdapter = SlashCommandAdapter()
        val recyclerView = RecyclerView(iConversationContext.chatActivity)
        recyclerView.layoutManager = LinearLayoutManager(iConversationContext.chatActivity)
        recyclerView.adapter = slashCommandAdapter
        recyclerView.setBackgroundColor(
            ContextCompat.getColor(iConversationContext.chatActivity, R.color.homeColor)
        )
        val divider = androidx.recyclerview.widget.DividerItemDecoration(
            iConversationContext.chatActivity, LinearLayoutManager.VERTICAL
        )
        recyclerView.addItemDecoration(divider)

        slashCommandPopup = PopupWindow(
            recyclerView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            AndroidUtilities.dp(180f)
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = AndroidUtilities.dp(8f).toFloat()
        }

        slashCommandAdapter!!.setOnItemClickListener { _, _, position ->
            val menu = slashCommandAdapter!!.data[position]
            editText.setText("/${menu.cmd} ")
            editText.setSelection(editText.text!!.length)
            dismissSlashCommandPopup()
        }

        var menus = WKRobotModel.getInstance().getRobotMenus(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        if (menus.isEmpty() && "botfather" == channelID) {
            menus = botFatherFallbackMenus()
        }
        slashCommandAdapter!!.setAllItems(menus)
    }

    private fun botFatherFallbackMenus(): List<WKRobotMenuEntity> {
        val ctx = iConversationContext.chatActivity
        val cmds = arrayOf(
            "quickstart" to ctx.getString(R.string.bot_cmd_quickstart),
            "newbot" to ctx.getString(R.string.bot_cmd_newbot),
            "mybots" to ctx.getString(R.string.bot_cmd_mybots),
            "connect" to ctx.getString(R.string.bot_cmd_connect),
            "disconnect" to ctx.getString(R.string.bot_cmd_disconnect),
            "setname" to ctx.getString(R.string.bot_cmd_setname),
            "setdescription" to ctx.getString(R.string.bot_cmd_setdescription),
            "deletebot" to ctx.getString(R.string.bot_cmd_deletebot),
            "token" to ctx.getString(R.string.bot_cmd_token),
            "revoke" to ctx.getString(R.string.bot_cmd_revoke),
            "pending" to ctx.getString(R.string.bot_cmd_pending),
            "approve" to ctx.getString(R.string.bot_cmd_approve_request),
            "reject" to ctx.getString(R.string.bot_cmd_reject_request),
            "cancel" to ctx.getString(R.string.bot_cmd_cancel),
            "help" to ctx.getString(R.string.bot_cmd_help)
        )
        return cmds.map { (cmd, desc) ->
            WKRobotMenuEntity().apply { this.cmd = cmd; this.remark = desc }
        }
    }

    private fun showSlashCommandPopup(query: String) {
        if (slashCommandAdapter == null || suppressSlashPopup) return
        if (!editText.isAttachedToWindow) return
        slashCommandAdapter!!.filter(query)
        if (slashCommandAdapter!!.data.isEmpty()) {
            dismissSlashCommandPopup()
            return
        }
        if (slashCommandPopup?.isShowing != true) {
            val itemHeight = AndroidUtilities.dp(44f)
            val maxItems = 4
            val popupHeight = itemHeight * minOf(slashCommandAdapter!!.data.size, maxItems)
            slashCommandPopup?.height = popupHeight
            slashCommandPopup?.showAsDropDown(editText, 0, -(editText.height + popupHeight), Gravity.START)
        }
    }

    private fun dismissSlashCommandPopup() {
        try {
            slashCommandPopup?.dismiss()
        } catch (_: Exception) {
        }
    }

    private fun checkRobotMenu(iConversationContext: IConversationContext) {
        // 不显示机器人菜单按钮
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
        // 子区用父群的成员列表来做 @mention（对齐 iOS 行为）
        val mentionChannelID: String
        val mentionChannelType: Byte
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) {
            val parsed = com.chat.uikit.thread.service.ThreadModel.getInstance()
                .parseChannelId(iConversationContext.chatChannelInfo.channelID)
            mentionChannelID = parsed?.get(0) ?: iConversationContext.chatChannelInfo.channelID
            mentionChannelType = WKChannelType.GROUP
        } else {
            mentionChannelID = iConversationContext.chatChannelInfo.channelID
            mentionChannelType = iConversationContext.chatChannelInfo.channelType
        }
        remindMemberAdapter = RemindMemberAdapter(
            mentionChannelID,
            mentionChannelType
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
                    // 三态 mention sentinel：
                    //   uid = "-1" → @所有人（mention.all=1 / humans=1）
                    //   uid = "-2" → @所有AI（mention.ais=1，bot UID 展开在发送时完成）
                    if (entity.type == GroupMemberEntity.TYPE_AT_AIS) {
                        memberEntity.memberName =
                            iConversationContext.chatActivity.getString(R.string.all_ais)
                        memberEntity.memberUID = "-2"
                    } else {
                        memberEntity.memberName =
                            iConversationContext.chatActivity.getString(R.string.all)
                        memberEntity.memberUID = "-1"
                    }
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
            // 图文混排（RichText=14）输入框附件托盘（Phase 2，对齐 web#237）：托盘非空时，
            // 点发送 = 把文本 + 托盘有序图片整体打成单条 type=14（文本块在前 + 图片按托盘顺序），而非发纯文本。
            // 若托盘发送未被接管（如进入 reply/edit 态，sendRichTextTray 返回 false），则<em>不</em>
            // 拦截，继续走下方原有文本 / reply / edit 发送路径，避免发送键失灵。
            if (!richTextTray.isEmpty() && flushRichTextTraySend()) {
                return@setOnClickListener
            }
            var content = StringUtils.replaceBlank(editText.text.toString())
            if (!TextUtils.isEmpty(content)) {
                content = editText.text.toString()

                // 重复发送拦截（YUJ-2872 🔴 defect b）：图文混排发送 in-flight 期间，被消费
                // 的文本仍留在输入框（YUJ-2832 崩溃恢复）。此时点发送键会把同一段可见文本作为
                // 独立纯文本单发 → 重复文本 + 之后那条 RichText。命中（与 in-flight 快照完全
                // 相同）则吞掉这次点击；文本被改动即视为新意图，放行。
                if (iConversationContext.isPendingRichTextDuplicate(content)) {
                    return@setOnClickListener
                }

                // 检查文本字节大小是否超过限制
                val textBytes = content.toByteArray(Charsets.UTF_8)
                if (messageTextMaxBytes > 0 && textBytes.size > messageTextMaxBytes) {
                    showTextToFileAlert(content)
                    return@setOnClickListener
                }

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
                val allEntities = editText.allEntity.toMutableList()
                val list = editText.allUIDs.toMutableList()

                // 扫描纯文本中的 @mention，匹配群成员后自动补上 entity
                if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP ||
                    iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) {
                    scanPlainTextMentions(content, allEntities, list)
                }

                // 三态 mention：sentinel uid（"-1"/"-2"）只是 UI 路由标记，不能落到 mention.entities，
                // 否则 server / 对端会把 "-1"/"-2" 当成真实用户 uid（对齐 iOS PR#128 round 2 教训）。
                allEntities.removeAll { e ->
                    e.type == ChatContentSpanType.mention &&
                            (e.value == "-1" || e.value == "-2")
                }

                val hasMentions = list.isNotEmpty()

                val textMsgModel = if (hasMentions)
                    WKMentionTextContent(content) else WKTextContent(content)

                if (hasMentions) {
                    val mMentionInfo = WKMentionInfo()
                    val uidList: MutableList<String> = ArrayList()
                    for (uid in list) {
                        when {
                            uid.equals("-1", ignoreCase = true) -> {
                                // 三态 mention：@所有人 走新协议 mention.humans=1，
                                // 不再设置 legacy mention.all=1（旧 adapter bot 误唤醒）。
                                // 对齐 iOS dmwork-ios#129 / Web。
                                textMsgModel.mentionHumans = 1
                                mMentionInfo.humans = true
                            }
                            uid == "-2" -> {
                                // 三态 mention：@所有AI 命中，标志位写入 mention.ais=1
                                textMsgModel.mentionAis = 1
                                mMentionInfo.ais = true
                            }
                            else -> {
                                uidList.add(uid)
                            }
                        }
                    }
                    // 三态 mention：@所有AI 命中时把当前会话内 robot 成员展开到 mention.uids，
                    // 兼容旧 adapter（新 adapter 直接读 mention.ais 路由）。对齐 iOS / Web PR#101。
                    if (textMsgModel.mentionAis == 1) {
                        expandRobotMembersIntoUids(uidList)
                    }
                    mMentionInfo.uids = uidList
                    textMsgModel.mentionInfo = mMentionInfo
                }
                textMsgModel.entities = allEntities

                iConversationContext.sendMessage(textMsgModel)
                editText.text = null
                lastInputTime = 0
                if (chatTopView?.visibility == View.VISIBLE) {
                    CommonAnim.getInstance().animateClose(chatTopView)
                }
            }
        }
        editText.addTextChangedListener(object : TextWatcher {
//            var linesCount = 0

            // var lastHeight = AndroidUtilities.dp(35f)
            var start = 0
            var count = 0
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.count = count
                if (!TextUtils.isEmpty(s.toString())) {
                    val content = StringUtils.replaceBlank(s.toString())
                    if (!TextUtils.isEmpty(content)) {
                        if (!isShowSendBtn) {
                            sendIV.clearColorFilter()
                            sendIV.visibility = View.VISIBLE
                            CommonAnim.getInstance().animImageView(sendIV)
                        }
                        isShowSendBtn = true
                    } else {
                        // 文本为空白：托盘有图时仍保留发送键（Phase 2 纯图片托盘可发）。
                        if (!richTextTray.isEmpty()) {
                            isShowSendBtn = true
                            sendIV.visibility = View.VISIBLE
                        } else {
                            isShowSendBtn = false
                            sendIV.visibility = View.GONE
                        }
                    }
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, false, true)
                    }
                } else {
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, true, true)
                    }
                    // 文本清空：托盘有图时仍保留发送键（Phase 2 纯图片托盘可发）。
                    if (!richTextTray.isEmpty()) {
                        isShowSendBtn = true
                        sendIV.visibility = View.VISIBLE
                    } else {
                        isShowSendBtn = false
                        sendIV.visibility = View.GONE
                    }
                }
                // slash command detection for bot chats
                val fullText = s.toString()
                if (fullText.startsWith("/") && !fullText.contains(" ")) {
                    val query = fullText.substring(1)
                    showSlashCommandPopup(query)
                } else {
                    dismissSlashCommandPopup()
                }

                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                if (selectionEnd != selectionStart || selectionStart <= 0) {
                    hideRemindView()
                    return
                }

                try {
                    val fullStr = s.toString()
                    var text = fullStr.substring(start, start + count)
                    if (start + count == fullStr.length) {
                        if (count == 0 || TextUtils.isEmpty(text)) {
                            if (fullStr.lastIndexOf("@") >= 0) {
                                val index = fullStr.lastIndexOf("@")
                                val remindText = fullStr.substring(index, fullStr.length)
                                if (!TextUtils.isEmpty(remindText)) text = remindText
                            }
                        } else {
                            if (fullStr.startsWith("@") && fullStr.contains(" ")) {
                                text = fullStr
                            } else {
                                if (fullStr.lastIndexOf("@") >= 0) {
                                    val index = fullStr.lastIndexOf("@")
                                    val remindText = fullStr.substring(index, fullStr.length)
                                    if (!TextUtils.isEmpty(remindText)) text = remindText
                                }
                            }
                        }
                    } else {
                        val temp = fullStr.substring(0, start)
                        if (!TextUtils.isEmpty(temp) && temp.contains("@")) {
                            val index = temp.lastIndexOf("@")

                            if (count == 0) {
                                val endIndex = editText.selectionEnd
                                if (endIndex <= fullStr.length) {
                                    val str = fullStr.substring(index, endIndex) + text
                                    if (!TextUtils.isEmpty(str)) {
                                        text = str
                                    }
                                }
                            } else {
                                val end = (index + count).coerceAtMost(fullStr.length)
                                text = fullStr.substring(index, end) + text
                            }
                        }
                    }
                    if (!TextUtils.isEmpty(text) && (mentionDisplay(text) || text.startsWith("@"))) {
                        searchInputText(text)
                    } else {
                        hideRemindView()
                        hideRobotView()
                        CommonAnim.getInstance().showOrHide(hitTv, false, true)
                        CommonAnim.getInstance().showOrHide(sendIV, true, true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatPanelMention", "onTextChanged error: s='$s' start=$start count=$count selStart=$selectionStart", e)
                    hideRemindView()
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // 保存当前高度
                lastHeight = editText.height
            }

            override fun afterTextChanged(s: Editable) {
                updateEditHeight()
                MoonUtil.replaceEmoticons(
                    iConversationContext.chatActivity,
                    s, start, count
                )
                if (s.toString().length <= 2 && !s.toString().startsWith("@")) {
                    //搜索表情
                    EndpointManager.getInstance()
                        .invoke(
                            "search_chat_edit_content",
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
                                loginUID
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

    private fun scanPlainTextMentions(
        content: String,
        entities: MutableList<WKMsgEntity>,
        uidList: MutableList<String>
    ) {
        // 三态 mention：先识别广播 token（@所有人 / @所有AI / @all / @All AIs / ...）。
        // 这些标签可能含空格（英文 "All AIs"），下面 @\\S+ 正则会被空格切碎，
        // 必须在通用扫描前先按字面量匹配 → 转成 sentinel uid（-1 / -2）。
        scanBroadcastMentions(content, entities, uidList)

        val pattern = java.util.regex.Pattern.compile("@([^@\\s]+)")
        val matcher = pattern.matcher(content)
        var channelId = iConversationContext.chatChannelInfo.channelID
        var channelType = iConversationContext.chatChannelInfo.channelType

        // 子区成员在父群上，需要用父群的channelID查成员
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            val channel = iConversationContext.chatChannelInfo
            val parentGroupNo = channel.remoteExtraMap?.get("parentGroupNo") as? String
            if (!parentGroupNo.isNullOrEmpty()) {
                channelId = parentGroupNo
                channelType = WKChannelType.GROUP
            }
        }

        val members = WKIM.getInstance().channelMembersManager
            .getMembers(channelId, channelType)
        if (members == null || members.isEmpty()) return

        while (matcher.find()) {
            val offset = matcher.start()
            val fullMatch = matcher.group()
            val alreadyCovered = entities.any { e ->
                e.type == ChatContentSpanType.mention && e.offset == offset
            }
            if (alreadyCovered) continue

            val candidateName = matcher.group(1) ?: continue

            var matchedMember: WKChannelMember? = null
            for (m in members) {
                val memberName = m.memberName ?: ""
                val memberRemark = m.memberRemark ?: ""
                val channel = WKIM.getInstance().channelManager
                    .getChannel(m.memberUID, WKChannelType.PERSONAL)
                val channelName = channel?.channelName ?: ""
                val channelRemark = channel?.channelRemark ?: ""

                if (candidateName == memberName || candidateName == memberRemark
                    || candidateName == channelName || candidateName == channelRemark) {
                    matchedMember = m
                    break
                }
            }
            if (matchedMember != null) {
                val entity = WKMsgEntity()
                entity.type = ChatContentSpanType.mention
                entity.offset = offset
                entity.length = fullMatch.length
                entity.value = matchedMember.memberUID
                entities.add(entity)
                if (!uidList.contains(matchedMember.memberUID)) {
                    uidList.add(matchedMember.memberUID)
                }
            }
        }
    }

    /**
     * 三态 mention：locale-independent 的广播标签字面量匹配。
     *
     * 必须覆盖任意 sender locale 的 wire text（对齐 iOS PR#128 round 1/4 教训）：
     * Chinese 端发出 "@所有AI" 到 English 端、English 端发出 "@All AIs" 到 Chinese 端，
     * 两个方向都要能识别。因此把 canonical 中文 + 已知英文翻译 + 当前 locale 的资源全列上。
     *
     * 长度降序匹配：保证 "@All AIs" 优先于 "@all" 命中，避免短标签先抢。
     */
    private fun scanBroadcastMentions(
        content: String,
        entities: MutableList<WKMsgEntity>,
        uidList: MutableList<String>
    ) {
        val ctx = iConversationContext.chatActivity
        val labelToSentinel: List<Pair<String, String>> = mutableListOf<Pair<String, String>>().apply {
            // @所有人 — locale-independent canonical aliases + current locale resource
            add("所有人" to "-1")
            add("All People" to "-1")
            add("Everyone" to "-1")
            add("all" to "-1")
            add(ctx.getString(R.string.mention_everyone) to "-1")
            // @所有AI — locale-independent canonical aliases + current locale resource
            add("所有AI" to "-2")
            add("All AIs" to "-2")
            add(ctx.getString(R.string.mention_all_ai) to "-2")
        }
            // 去重（不同 locale 可能落到同一字面量）
            .distinctBy { it.first.lowercase() + "|" + it.second }
            // 长度降序：保证 "@All AIs" 优先于 "@all" 命中
            .sortedByDescending { it.first.length }

        for ((label, sentinel) in labelToSentinel) {
            if (label.isEmpty()) continue
            val token = "@$label"
            var fromIndex = 0
            while (fromIndex <= content.length - token.length) {
                val idx = content.indexOf(token, fromIndex, ignoreCase = true)
                if (idx < 0) break
                val end = idx + token.length
                // 末位边界：不能把 "@所有AIs" / "@All AIs2" 这种延伸串误命中
                if (end < content.length) {
                    val nextCh = content[end]
                    if (nextCh.isLetterOrDigit() || nextCh == '_') {
                        fromIndex = idx + 1
                        continue
                    }
                }
                val overlaps = entities.any { e ->
                    e.type == ChatContentSpanType.mention &&
                            idx < e.offset + e.length && e.offset < end
                }
                if (!overlaps) {
                    val entity = WKMsgEntity()
                    entity.type = ChatContentSpanType.mention
                    entity.offset = idx
                    entity.length = token.length
                    entity.value = sentinel
                    entities.add(entity)
                    if (!uidList.contains(sentinel)) {
                        uidList.add(sentinel)
                    }
                }
                fromIndex = end
            }
        }
    }

    /**
     * 三态 mention：把当前会话内 robot 成员展开到 mention.uids，兼容旧 adapter
     *（新 adapter 直接根据 mention.ais=1 路由，但 server 当前还需要 UID 列表来定向投递）。
     * 对齐 iOS PR#128 / Web PR#101。
     *
     * 子区会话（COMMUNITY_TOPIC）使用父群成员列表。
     * 使用 LinkedHashSet 保留顺序去重，避免大群里 List.contains 退化成 O(n)。
     */
    private fun expandRobotMembersIntoUids(uidList: MutableList<String>) {
        var channelId = iConversationContext.chatChannelInfo.channelID
        var channelType = iConversationContext.chatChannelInfo.channelType
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            val channel = iConversationContext.chatChannelInfo
            val parentGroupNo = channel.remoteExtraMap?.get("parentGroupNo") as? String
            if (!parentGroupNo.isNullOrEmpty()) {
                channelId = parentGroupNo
                channelType = WKChannelType.GROUP
            }
        }
        val members = WKIM.getInstance().channelMembersManager
            .getMembers(channelId, channelType) ?: return
        if (members.isEmpty()) return

        val seen = HashSet<String>(uidList)
        for (m in members) {
            val uid = m.memberUID ?: continue
            if (uid.isEmpty()) continue
            if (m.robot != 1) continue
            if (m.isDeleted == 1) continue
            if (seen.add(uid)) {
                uidList.add(uid)
            }
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
            username = username.replace("@", "").replace(" ", "")
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
                        content.substring(index, content.length).replace(" ", "")
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
        if ((iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP || iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) && isSearchGroupMembers) {
            var remindSearchKey: String = content

            remindSearchKey = remindSearchKey.replace("@", "")
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
            if (remindRecycleView!!.isGone) CommonAnim.getInstance()
                .showBottom2Top(remindRecycleView)
        }
    }

    private fun updateEditHeight() {
        val layout = editText.layout
        if (layout == null) {
            return
        }
        // 将高度更新和动画放到post中，确保Layout已更新
//        editText.post(Runnable {
//            val layout = editText.layout
//            if (layout == null) {
//                return@Runnable
//            }
        val lineCount = layout.lineCount
        // 计算目标行数（不超过MAX_LINES）
        val targetLines = min(
            lineCount.toDouble(),
            maxLines.toDouble()
        ).toInt()
        // 只有当目标行数改变时才执行动画或调整高度
        if (targetLines != lastTargetLines) {
            // 计算精确的高度
            var newHeight = layout.getLineTop(targetLines) +
                    editText.getCompoundPaddingTop() +
                    editText.getCompoundPaddingBottom()
            if (newHeight < AndroidUtilities.dp(35f)) {
                newHeight = AndroidUtilities.dp(35f)
            }
            // 创建高度动画
            val animator = ValueAnimator.ofInt(lastHeight, newHeight)
            animator.setDuration(200) // 动画持续时间
            animator.interpolator = AccelerateDecelerateInterpolator()

            animator.addUpdateListener(ValueAnimator.AnimatorUpdateListener { animation: ValueAnimator? ->
                val animatedValue = animation!!.getAnimatedValue() as Int
                val params = editText.layoutParams
                params.height = animatedValue
                editText.setLayoutParams(params)
                iConversationContext.chatRecyclerViewScrollToEnd()
            })
            animator.start()
            // 更新上一次的目标行数
            lastTargetLines = targetLines

        } else if (lineCount <= maxLines) {
            // 如果行数未变且在限制内，确保高度正确（无动画，作为备用检查）
            var correctHeight = layout.getLineTop(targetLines) +
                    editText.getCompoundPaddingTop() +
                    editText.getCompoundPaddingBottom()
            if (correctHeight < AndroidUtilities.dp(35f)) {
                correctHeight = AndroidUtilities.dp(35f)
            }
            if (editText.height != correctHeight) {
                val params = editText.layoutParams
                params.height = correctHeight
                editText.setLayoutParams(params)
                iConversationContext.chatRecyclerViewScrollToEnd()
            }
        }
//        })
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
            if (result?.results != null && result.results.isNotEmpty()) {
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
        val rv = remindRecycleView ?: return
        if ((iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP || iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) && rv.visibility != View.GONE) {
            CommonAnim.getInstance().hideTop2Bottom(rv)
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

    /**
     * emoji 面板内容区（tab bar 下面那块，[getEmojiLayout] 里的 contentContainer）的高度，
     * 单位 px —— 跟 [syncEmojiPanelHeightWithKeyboard] 共享，用来判断键盘高度变化后要不要重新排布。
     */
    private var emojiPanelContentHeightPx = 0

    /** [getEmojiLayout] 里的 contentContainer，供 [syncEmojiPanelHeightWithKeyboard] 重新设置高度。 */
    private var emojiPanelContentContainer: View? = null

    /**
     * 面板内容高度：已知真实键盘高度就用它，否则（新设备/键盘从未弹出过）退化为屏幕高度的 1/3 估算。
     *
     * 唯一的高度来源 —— 以后新增面板要跟键盘对齐高度，也应该调这个方法，不要各自重复
     * "读 WKConstants.getKeyboardHeight() + /3 兜底" 这段逻辑。
     */
    private fun resolvePanelContentHeightPx(): Int {
        val height = WKConstants.getKeyboardHeight()
        return if (height > 0) height else AndroidUtilities.getScreenHeight() / 3
    }

    /**
     * 键盘高度第一次被真实记录（或后续变化，如切换输入法）时，由 [ChatActivity] 的
     * addKeyboardStateListener 回调调用，把 emoji 面板内容区的高度同步过去。
     *
     * 面板此刻必然不可见（这个回调只在键盘正显示时触发，键盘和面板互斥），所以这里改
     * LayoutParams 不会让用户看到面板跳动 —— 下次点表情按钮重新显示时用的就是新高度。
     *
     * 若目前面板还没建出来（[emojiPanelContentContainer] 为空）或高度没变化，直接跳过。
     */
    fun syncEmojiPanelHeightWithKeyboard() {
        val container = emojiPanelContentContainer ?: return
        val tabBarHeightDp = 36
        val newContentHeightPx =
            resolvePanelContentHeightPx() - AndroidUtilities.dp(tabBarHeightDp.toFloat())
        if (newContentHeightPx == emojiPanelContentHeightPx) return
        emojiPanelContentHeightPx = newContentHeightPx
        // 注意单位：这里是 LayoutParams.height 本身，要填 px；跟 getEmojiLayout() 里传给
        // LayoutHelper.createLinear(...) 的入参不同 —— 那个方法内部会把 int 参数当 dp 再转 px。
        val params = container.layoutParams
        params.height = newContentHeightPx
        container.layoutParams = params
        container.requestLayout()
    }

    private fun getEmojiLayout(): View {
        val activity = iConversationContext.chatActivity

        // 两个内容 View：emoji grid + 我的贴图 grid。
        // 复用原 emoji 行为不变，另建 sticker 视图与之切换。
        val emojiContent = buildEmojiContentView()
        val stickerContent = buildStickerContentView()

        val contentContainer = FrameLayout(activity)

        // 顶部 tab bar：两个按钮 emoji / 我的贴图。
        val tabBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                AndroidUtilities.dp(8f), AndroidUtilities.dp(4f),
                AndroidUtilities.dp(8f), AndroidUtilities.dp(4f)
            )
        }
        val emojiTabBtn = buildEmojiPanelTabButton(activity.getString(R.string.str_emoji_tab))
        val stickerTabBtn = buildEmojiPanelTabButton(activity.getString(R.string.str_my_stickers_tab))
        tabBar.addView(
            emojiTabBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        )
        tabBar.addView(
            stickerTabBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        )

        fun selectTab(index: Int) {
            val selColor = Theme.colorAccount
            val normalColor = ContextCompat.getColor(activity, R.color.color999)
            emojiTabBtn.setTextColor(if (index == 0) selColor else normalColor)
            stickerTabBtn.setTextColor(if (index == 1) selColor else normalColor)
            contentContainer.removeAllViews()
            if (index == 0) {
                contentContainer.addView(emojiContent)
                // 离开贴图 tab 时退出编辑态
                stickerAdapter?.setEditMode(false)
            } else {
                contentContainer.addView(stickerContent)
                // 每次切到贴图 tab 都 refresh 一次收藏列表（含首次进入拉数据）。
                WKStickerManager.load()
            }
        }
        emojiTabBtn.setOnClickListener { selectTab(0) }
        stickerTabBtn.setOnClickListener { selectTab(1) }

        val tabBarHeightDp = 36
        val contentHeightPx =
            resolvePanelContentHeightPx() - AndroidUtilities.dp(tabBarHeightDp.toFloat())
        emojiPanelContentHeightPx = contentHeightPx
        emojiPanelContentContainer = contentContainer

        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            tabBar,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, tabBarHeightDp)
        )
        root.addView(
            contentContainer,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                (contentHeightPx / AndroidUtilities.density).toInt()
            )
        )

        selectTab(0)  // 默认 emoji tab
        return root
    }

    /**
     * 表情面板右下角的悬浮删除（退格）键。
     *
     * 旧表情面板 [com.chat.base.emoji.EmojiFragment] 本来就有这颗键（frag_emoji_layout.xml），
     * 面板迁到本类纯代码搭建时漏掉了 —— 结果表情点错只能去点输入框调起系统键盘退格，
     * 而点输入框又会把表情面板收起来。这里把它补回来。
     *
     * 单击退一格；长按按 [emojiDeleteRepeatIntervalMs] 连续退格，抬手即停。
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun buildEmojiDeleteButton(): View {
        val activity = iConversationContext.chatActivity
        val btn = android.view.LayoutInflater.from(activity)
            .inflate(R.layout.view_emoji_panel_delete_btn, null, false)

        // passcode_delete 是深色图标，跟主题染色否则深色模式下看不见（对齐 EmojiFragment.initView）
        Theme.setColorFilter(
            activity,
            btn.findViewById<AppCompatImageView>(R.id.emojiDeleteIv),
            R.color.popupTextColor
        )

        val hitArea = btn.findViewById<RelativeLayout>(R.id.emojiDeleteLayout)
        hitArea.setOnClickListener { dispatchBackspace() }
        hitArea.setOnLongClickListener {
            startEmojiDeleteRepeat()
            true  // 消费掉长按，系统不会再补一次 click，避免长按结束后多删一个
        }
        hitArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> stopEmojiDeleteRepeat()
            }
            false  // 不拦截，click / longClick 照常走
        }
        return btn
    }

    private val emojiDeleteRepeatIntervalMs = 80L

    private val emojiDeleteRepeat = object : Runnable {
        override fun run() {
            // 删空了就自然收尾，不在空输入框上空转
            if (!dispatchBackspace()) return
            mainHandler.postDelayed(this, emojiDeleteRepeatIntervalMs)
        }
    }

    private fun startEmojiDeleteRepeat() {
        mainHandler.removeCallbacks(emojiDeleteRepeat)
        mainHandler.post(emojiDeleteRepeat)
    }

    private fun stopEmojiDeleteRepeat() {
        mainHandler.removeCallbacks(emojiDeleteRepeat)
    }

    /**
     * 往输入框派发一次退格，与 initListener() 里 "emoji_click" 端点的删除分支同源。
     *
     * "[微笑]" 这类自定义表情由 [com.chat.base.emoji.MoonUtil.addEmojiSpan] 插入时带
     * AlignImageSpan（ReplacementSpan 子类），系统 BaseKeyListener 退格会整体删掉，
     * 不用在这里特殊处理。
     *
     * @return 是否真的删了 —— 输入框已空时返回 false，供长按连续删自行停下。
     */
    private fun dispatchBackspace(): Boolean {
        if (editText.text.isNullOrEmpty()) return false
        editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        return true
    }

    private fun buildEmojiPanelTabButton(label: String): AppCompatTextView {
        val activity = iConversationContext.chatActivity
        return AppCompatTextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.color999))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
            Theme.setPressedBackground(this)
        }
    }

    /**
     * 抽取自旧 [getEmojiLayout]：emoji grid 视图，行为完全不变。
     * onAttachedToWindow 在 tab 切换 / 面板重开时重新走"最近使用"排序。
     */
    private fun buildEmojiContentView(): View {
        val activity = iConversationContext.chatActivity
        val width = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(30f) * 8
        val customList = EmojiManager.getInstance().getEmojiWithType("custom_")
        val normalList = EmojiManager.getInstance().getEmojiWithType("0_")
        val naturelList = EmojiManager.getInstance().getEmojiWithType("1_")
        val symbolsList = EmojiManager.getInstance().getEmojiWithType("2_")
        val baseList = ArrayList<EmojiEntry>().apply {
            addAll(customList)
            addAll(normalList)
            addAll(naturelList)
            addAll(symbolsList)
        }
        val emojiAdapter = EmojiAdapter(buildOrderedEmojiList(baseList), width)
        // onAttachedToWindow：tab 切回来或面板重开时按最新 prefs 重排。
        // 同一次面板期间顺序保持稳定（点击不当场跳位），下次打开才生效——对齐
        // WeChat / iOS 的体验（iOS WKEmojiContentView 也不在 didSelect 后 reloadData）。
        val emojiLayout = object : LinearLayout(activity) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                emojiAdapter.setList(buildOrderedEmojiList(baseList))
            }
        }
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = GridLayoutManager(activity, 8)
        recyclerView.adapter = emojiAdapter
        // 底部让出删除键的高度，否则最后一行被压住点不到。
        // clipToPadding=false：滚动中表情照常从这块区域穿过，只有停在底部时
        // 最后一行才停在按钮上方。
        recyclerView.clipToPadding = false
        recyclerView.setPadding(0, 0, 0, AndroidUtilities.dp(EMOJI_PANEL_BOTTOM_INSET_DP))
        emojiLayout.addView(
            recyclerView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
        )

        emojiAdapter.setOnItemClickListener { adapter, _, position ->
            val emojiEntry = adapter.getItem(position) as EmojiEntry
            if (emojiEntry.tag.startsWith("custom_")) {
                val textContent = WKTextContent(emojiEntry.text)
                iConversationContext.sendMessage(textContent)
            } else {
                val curPosition: Int = editText.selectionStart
                val sb = java.lang.StringBuilder(
                    Objects.requireNonNull(editText.text).toString()
                )
                sb.insert(curPosition, emojiEntry.text)
                MoonUtil.addEmojiSpan(editText, emojiEntry.text, iConversationContext.chatActivity)
                editText.setSelection(curPosition + emojiEntry.text.length)
            }
            recordRecentEmoji(emojiEntry.text)
        }

        // 悬浮删除键只挂在「表情」tab —— 另一个 tab（自定义表情/贴图）点了直接发送、
        // 全程不写输入框，退格键在那儿没有作用对象，挂上去还会白占一块防遮挡留白。
        val emojiRoot = FrameLayout(activity)
        emojiRoot.addView(
            emojiLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        emojiRoot.addView(
            buildEmojiDeleteButton(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = AndroidUtilities.dp(5f)
                bottomMargin = AndroidUtilities.dp(5f)
            }
        )
        return emojiRoot
    }

    /**
     * 我的贴图 tab —— 1:1 复刻 iOS `WKMyStickerContentView` 两段式交互：
     * - 5 列 [GridLayoutManager]，首格永远是 "+"（触发相册选图上传）
     * - 非编辑态：tap 发送 / 长按进入编辑态（haptic 中等震感）
     * - 编辑态：cells 抖动 + × 徽章；tap 退出编辑态；tap × 二次确认删除；
     *   长按 hold-still 500ms 弹预览；长按+移动由 [ItemTouchHelper] 接管拖拽重排
     * - 退出编辑态：tap 空白 / 切 tab / tap sticker / tap "+"
     * - 数据源 [WKStickerManager.stickersLiveData]，本地顺序 [com.chat.uikit.chat.sticker.StickerLocalOrderStore]
     *
     * [ItemTouchHelper.isLongPressDragEnabled] 置为 false —— drag 由 Adapter 内部
     * [com.chat.uikit.chat.sticker.CollectStickerAdapter.Callbacks.startDrag] 手动
     * 触发，因为需要区分"长按 hold-still 出预览" vs "长按+移动出 drag"。
     */
    private fun buildStickerContentView(): View {
        val activity = iConversationContext.chatActivity
        val container = FrameLayout(activity)

        // 拖拽重排：编辑态启用，"+" 首格禁止移动。
        // isLongPressDragEnabled=false —— 由 Adapter 的 EditModeTouchListener 手动决定
        // 何时 startDrag（区分"长按 hold-still 出预览"vs"长按+移动出 drag"）。
        val touchCallback = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (stickerAdapter?.isEditMode() != true) return 0
                if (viewHolder.bindingAdapterPosition == 0) return 0 // "+" 不可拖
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (target.bindingAdapterPosition == 0) return false
                return stickerAdapter?.moveItem(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                ) ?: false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                stickerAdapter?.currentOrderIds()?.let { WKStickerManager.reorder(it) }
            }
        }
        val itemTouchHelper = ItemTouchHelper(touchCallback)

        val adapter = CollectStickerAdapter(object : CollectStickerAdapter.Callbacks {
            override fun onAddClick() {
                // 编辑态点 "+"：iOS 上会先退出编辑态。Android 保持一致 —— 先退出再打开选图
                stickerAdapter?.takeIf { it.isEditMode() }?.setEditMode(false)
                pickAndUploadSticker(activity)
            }

            override fun onStickerClick(sticker: WKSticker, position: Int) {
                sendCollectedSticker(sticker)
            }

            override fun onEnterEditMode() {
                stickerAdapter?.setEditMode(true)
                // Haptic 中等震感，对齐 iOS UIImpactFeedbackStyleMedium
                container.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS
                )
            }

            override fun onPreviewSticker(sticker: WKSticker, position: Int) {
                com.chat.uikit.chat.sticker.StickerDetailPopup.showForPanel(activity, sticker) {
                    sendCollectedSticker(it)
                }
            }

            override fun onDeleteSticker(sticker: WKSticker, position: Int) {
                confirmDeleteSticker(activity, sticker)
            }

            override fun startDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
            }
        })
        stickerAdapter = adapter

        val recyclerView = RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, 5)
            this.adapter = adapter
            itemAnimator = DefaultItemAnimator()
            // tap 空白（非任何 cell）退出编辑态
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                    if (e.actionMasked == android.view.MotionEvent.ACTION_UP &&
                        stickerAdapter?.isEditMode() == true &&
                        rv.findChildViewUnder(e.x, e.y) == null
                    ) {
                        stickerAdapter?.setEditMode(false)
                    }
                    return false
                }
            })
        }
        itemTouchHelper.attachToRecyclerView(recyclerView)

        container.addView(
            recyclerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val emptyTv = AppCompatTextView(activity).apply {
            text = activity.getString(R.string.str_no_collect_stickers)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.color999))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            visibility = View.GONE
            setPadding(
                AndroidUtilities.dp(24f), AndroidUtilities.dp(80f),
                AndroidUtilities.dp(24f), 0
            )
        }
        container.addView(
            emptyTv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        WKStickerManager.stickersLiveData.observe(activity) { list ->
            val safeList = list ?: emptyList()
            adapter.submitList(safeList)
            emptyTv.visibility = if (safeList.isEmpty()) View.VISIBLE else View.GONE
        }

        return container
    }

    /** 拉起相册选一张图 → 客户端校验 → 上传。UI 层不关心细节，交给 [WKStickerUploader]。 */
    private fun pickAndUploadSticker(activity: android.app.Activity) {
        GlideUtils.getInstance().chooseIMG(
            activity, 1, false, ChooseMimeType.img, false, false,
            object : GlideUtils.ISelectBack {
                override fun onBack(paths: MutableList<ChooseResult>?) {
                    val first = paths?.firstOrNull { it.path?.isNotEmpty() == true } ?: return
                    val file = java.io.File(first.path)
                    WKStickerUploader.upload(file, object : WKStickerUploader.Callback {
                        override fun onSuccess(sticker: WKSticker) {
                            // WKStickerUploader 内部已 toast 成功，此处不重复
                        }
                        override fun onError(messageResId: Int) {
                            if (messageResId != 0) {
                                com.chat.base.utils.WKToastUtils.getInstance()
                                    .showToastNormal(activity.getString(messageResId))
                            }
                        }
                    })
                }
                override fun onCancel() {}
            }
        )
    }

    /** 点击已收藏的贴纸 → 构造 [WKVectorStickerContent] 发送。 */
    private fun sendCollectedSticker(sticker: WKSticker) {
        val content = WKVectorStickerContent().apply {
            url = sticker.path
            category = if (sticker.category.isNullOrEmpty()) "user" else sticker.category
            placeholder = sticker.placeholder ?: ""
            format = when {
                !sticker.format.isNullOrEmpty() -> sticker.format
                else -> StickerUrlUtils.extractExt(sticker.path) ?: "png"
            }
        }
        iConversationContext.sendMessage(content)
    }

    /** 编辑态点 × → 二次确认 → 走 API 删除。 */
    private fun confirmDeleteSticker(activity: android.app.Activity, sticker: WKSticker) {
        WKDialogUtils.getInstance().showDialog(
            activity,
            null,
            activity.getString(R.string.str_sticker_delete_confirm),
            true,
            activity.getString(R.string.cancel),
            activity.getString(R.string.sure),
            0, 0
        ) { which ->
            if (which == 1) WKStickerManager.delete(sticker.sticker_id, null)
        }
    }

    /**
     * 把 [baseList] 按"最近使用"前置重排：prefs 里 `common_used_emojis` 保存的是用户用过的
     * emoji text（逗号分隔，最新在前），按该顺序先放，剩下的保持原排序。最小化改动方案——
     * 不引入 iOS "最近使用 / 所有表情" 两段 section，只是单网格里冒泡，UI 风险面降到最低。
     */
    private fun buildOrderedEmojiList(baseList: List<EmojiEntry>): List<EmojiEntry> {
        val recentTexts = readRecentEmojiTexts()
        if (recentTexts.isEmpty()) return ArrayList(baseList)
        val byText = baseList.associateBy { it.text }
        val ordered = ArrayList<EmojiEntry>(baseList.size)
        val used = HashSet<String>()
        for (text in recentTexts) {
            val entry = byText[text] ?: continue
            if (used.add(text)) ordered.add(entry)
        }
        for (entry in baseList) {
            if (used.add(entry.text)) ordered.add(entry)
        }
        return ordered
    }

    private fun readRecentEmojiTexts(): List<String> {
        val raw = WKSharedPreferencesUtil.getInstance().getSPWithUID("common_used_emojis") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    private fun recordRecentEmoji(text: String) {
        if (text.isEmpty()) return
        val current = readRecentEmojiTexts().filter { it != text }
        val updated = (listOf(text) + current).take(32)
        WKSharedPreferencesUtil.getInstance()
            .putSPWithUID("common_used_emojis", updated.joinToString(","))
    }


    private fun initHoldToTalk() {
        // Voice toggle button click
        voiceToggleBtn.setOnClickListener {
            val desc = String.format(
                iConversationContext.chatActivity.getString(R.string.microphone_permissions_des),
                iConversationContext.chatActivity.getString(R.string.app_name)
            )
            WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
                override fun onResult(result: Boolean) {
                    if (result) toggleVoiceMode()
                }
                override fun clickResult(isCancel: Boolean) {}
            }, iConversationContext.chatActivity, desc, Manifest.permission.RECORD_AUDIO)
        }

        // Hold to talk touch listener
        holdToTalkBtn.setOnTouchListener { _, event ->
            holdToTalkManager?.handleTouch(event) ?: false
        }
    }

    private fun toggleVoiceMode() {
        isVoiceMode = !isVoiceMode
        if (isVoiceMode) {
            voiceToggleBtn.setImageResource(R.mipmap.ic_keyboard_toggle)
            editTextContainer.visibility = View.GONE
            holdToTalkBtn.visibility = View.VISIBLE
            sendIV.visibility = View.GONE
            markdownIv.visibility = View.GONE
            SoftKeyboardUtils.getInstance().loseFocus(editText)
            SoftKeyboardUtils.getInstance().hideInput(iConversationContext.chatActivity, editText)

            if (holdToTalkManager == null) {
                holdToTalkManager = HoldToTalkManager(iConversationContext.chatActivity).apply {
                    listener = object : HoldToTalkManager.Listener {
                        override fun onSendText(text: String) {
                            // 有 pending 图：STT 文本作 caption，与图聚合（或 caption 全空时纯图）。
                            // 对齐 iOS holdToTalkManager:sendText: → _commitPendingWithCaption。
                            if (!richTextTray.isEmpty()) {
                                val previous = editText.text?.toString() ?: ""
                                editText.setText(text)
                                if (!flushRichTextTraySend()) {
                                    // tray 未接管（如 reply/edit 态）— 复原文本并按原文本路径发出。
                                    editText.setText(previous)
                                    sendVoiceTextDirect(text)
                                }
                                return
                            }
                            sendVoiceTextDirect(text)
                        }

                        override fun onSendVoice(audioPath: String, seconds: Int, waveform: String) {
                            val voiceContent = com.xinbida.wukongim.msgmodel.WKVoiceContent(audioPath, seconds)
                            voiceContent.waveform = waveform
                            iConversationContext.sendMessage(voiceContent)
                            // 兼容「语音模式下也可贴图」：语音消息发出后，若图片栏还有图，按纯图路径
                            // 逐张发出（与 iOS sendVoice: 之后 sendAlbumImageDatas: 顺序一致；
                            // RichText=14 不承载语音，这里分两条消息送达）。
                            if (!richTextTray.isEmpty()) {
                                val snapshot = richTextTray.orderedPaths()
                                richTextTray.clear()
                                refreshRichTextTray()
                                for (p in snapshot) {
                                    iConversationContext.sendMessage(com.xinbida.wukongim.msgmodel.WKImageContent(p))
                                }
                            }
                        }

                        override fun onRecordingStarted() {}
                        override fun onRecordingStopped() {}

                        override fun getCurrentInputText(): String? {
                            return editText.text?.toString()
                        }

                        override fun getChatContext(): String? {
                            return com.chat.uikit.chat.face.WKVoiceViewManager.getInstance()
                                .buildChatContext(iConversationContext)
                        }

                        override fun onShowResultUI(text: String) {
                            showResultMode(text)
                        }

                        override fun onDismissResultUI() {
                            dismissResultMode()
                        }

                        override fun onAppendText(text: String) {
                            resultBubbleEditText?.let { et ->
                                val current = et.text?.toString() ?: ""
                                val newText = if (current.isEmpty()) text else "$current$text"
                                et.setText(newText)
                                et.setSelection(newText.length)
                            }
                        }

                        override fun onAppendThinkingStart() {
                            showBubbleThinking()
                        }

                        override fun onAppendThinkingEnd() {
                            hideBubbleThinking()
                        }
                    }
                }
            }
        } else {
            voiceToggleBtn.setImageResource(R.mipmap.ic_voice_toggle)
            editTextContainer.visibility = View.VISIBLE
            holdToTalkBtn.visibility = View.GONE
            // 退出语音模式后发送键可见性须同时考虑「已暂存的图文托盘」，否则纯图托盘 +
            // 空文本会让发送键消失（trapped tray，CR P1）。updateSendBtnForTray() 已 OR 进
            // !richTextTray.isEmpty()，且此处已退出 isVoiceMode 故不会被其早返回短路。
            updateSendBtnForTray()
            holdToTalkManager?.cancelRecording()
        }
    }

    private var resultOverlay: FrameLayout? = null

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showResultMode(text: String) {
        isResultMode = true
        val ctx = iConversationContext.chatActivity
        val sw = ctx.resources.displayMetrics.widthPixels
        val sh = ctx.resources.displayMetrics.heightPixels

        // Full-screen overlay (iOS: 65% black)
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.argb(166, 0, 0, 0))
            isClickable = true
        }

        // "自研引擎 ▾" label (iOS: centered, 40pt above bubble)
        val engineLabel = AppCompatTextView(ctx).apply {
            this.text = ctx.getString(R.string.engine_label)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(AndroidUtilities.dp(14f), AndroidUtilities.dp(5f), AndroidUtilities.dp(14f), AndroidUtilities.dp(5f))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(14f).toFloat()
                setColor(Theme.colorAccount)
            }
            isClickable = true
            setOnClickListener { /* consume */ }
        }
        val bubbleY = (sh * 0.15f).toInt()
        val engineLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(28f)
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = bubbleY - AndroidUtilities.dp(36f)
        }
        overlay.addView(engineLabel, engineLp)

        // Bubble (iOS: light blue RGB(224,240,255), 16pt radius, sw-40 width)
        val bubble = FrameLayout(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(16f).toFloat()
                setColor(android.graphics.Color.argb(255, 224, 240, 255))
            }
        }
        val bubbleEt = android.widget.EditText(ctx).apply {
            setText(text)
            setSelection(text.length)
            setTextColor(android.graphics.Color.BLACK)
            textSize = 17f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(10f), AndroidUtilities.dp(12f), AndroidUtilities.dp(10f))
            minHeight = AndroidUtilities.dp(80f)
            maxHeight = AndroidUtilities.dp(300f)
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
            isFocusable = true
            isFocusableInTouchMode = true
        }
        bubble.addView(bubbleEt, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        val bubbleLp = FrameLayout.LayoutParams(sw - AndroidUtilities.dp(40f), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = bubbleY
        }
        overlay.addView(bubble, bubbleLp)
        resultBubbleEditText = bubbleEt

        // Bottom button bar (iOS: 80pt tall, 40pt from bottom)
        val spacing = sw / 4
        val btnSize = AndroidUtilities.dp(52f)
        val barH = AndroidUtilities.dp(80f)
        val barY = sh - barH - AndroidUtilities.dp(40f)

        // Cancel button (iOS: 52pt circle, gray 0.3)
        val cancelContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setOnClickListener { holdToTalkManager?.cancelResult() }
        }
        val cancelIcon = AppCompatImageView(ctx).apply {
            setImageResource(R.mipmap.ic_htt_cancel)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.argb(255, 77, 77, 77))
            }
            setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))
        }
        cancelContainer.addView(cancelIcon, LinearLayout.LayoutParams(btnSize, btnSize).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        val cancelLabel = AppCompatTextView(ctx).apply {
            this.text = ctx.getString(R.string.btn_cancel)
            setTextColor(android.graphics.Color.argb(255, 204, 204, 204))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
        }
        cancelContainer.addView(cancelLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = AndroidUtilities.dp(6f) })
        val cancelLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = spacing - btnSize / 2
            topMargin = barY
        }
        overlay.addView(cancelContainer, cancelLp)

        // Mic/continue button (iOS: 52pt circle, gray 0.3)
        val micContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        val micIcon = AppCompatImageView(ctx).apply {
            setImageResource(R.mipmap.ic_htt_mic)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.argb(255, 77, 77, 77))
            }
            setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))
        }
        micContainer.addView(micIcon, LinearLayout.LayoutParams(btnSize, btnSize).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        val micLabel = AppCompatTextView(ctx).apply {
            this.text = ctx.getString(R.string.voice_hold_to_continue)
            setTextColor(android.graphics.Color.argb(255, 204, 204, 204))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
        }
        micContainer.addView(micLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = AndroidUtilities.dp(6f) })
        micContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    showAppendRecordingUI()
                    holdToTalkManager?.startAppendRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    hideAppendRecordingUI()
                    holdToTalkManager?.stopAppendRecording()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    hideAppendRecordingUI()
                    holdToTalkManager?.cancelAppendRecording()
                    true
                }
                else -> true
            }
        }
        val micLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = spacing * 2 - btnSize / 2
            topMargin = barY
        }
        overlay.addView(micContainer, micLp)

        // Send button (iOS: 72x52pt pill, gray 0.92 bg, 16pt semibold)
        val sendBtn = AppCompatTextView(ctx).apply {
            this.text = ctx.getString(R.string.voice_send)
            setTextColor(android.graphics.Color.argb(255, 38, 38, 51))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(26f).toFloat()
                setColor(android.graphics.Color.argb(255, 234, 234, 234))
            }
            setOnClickListener {
                val resultText = resultBubbleEditText?.text?.toString()?.trim() ?: ""
                if (resultText.isNotEmpty()) {
                    holdToTalkManager?.sendResultText(resultText)
                }
            }
        }
        val sendW = AndroidUtilities.dp(72f)
        val sendH = AndroidUtilities.dp(52f)
        val sendLp = FrameLayout.LayoutParams(sendW, sendH).apply {
            leftMargin = spacing * 3 - sendW / 2
            topMargin = barY + (btnSize - sendH) / 2
        }
        overlay.addView(sendBtn, sendLp)

        resultBottomViews = listOf(cancelContainer, micContainer, sendBtn)
        resultBubbleContainer = bubble
        resultOverlay = overlay
        val contentView = ctx.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showAppendRecordingUI() {
        val overlay = resultOverlay ?: return
        val ctx = iConversationContext.chatActivity
        val sw = ctx.resources.displayMetrics.widthPixels
        val sh = ctx.resources.displayMetrics.heightPixels

        // Semi-transparent overlay (iOS: 45% black)
        val appendBg = FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.argb(115, 0, 0, 0))
            isClickable = true
        }

        // Bubble (iOS: 55% width, 70pt height, at 35% from top)
        val abW = (sw * 0.55f).toInt()
        val abH = AndroidUtilities.dp(70f)
        val abY = (sh * 0.35f).toInt()
        val bubble = FrameLayout(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(14f).toFloat()
                setColor(android.graphics.Color.argb(255, 225, 240, 255))
            }
        }

        // 24 waveform bars
        val barCount = 24
        val barW = AndroidUtilities.dp(3f)
        val barGap = AndroidUtilities.dp(2.5f)
        val totalBarsW = barCount * barW + (barCount - 1) * barGap
        val barsStartX = (abW - totalBarsW) / 2
        val barBaseH = AndroidUtilities.dp(6f)
        val barsY = (abH - AndroidUtilities.dp(40f)) / 2
        val bars = mutableListOf<View>()

        for (i in 0 until barCount) {
            val bar = View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = AndroidUtilities.dp(1.5f).toFloat()
                    setColor(android.graphics.Color.argb(179, 89, 141, 217))
                }
            }
            val barLp = FrameLayout.LayoutParams(barW, barBaseH).apply {
                leftMargin = barsStartX + i * (barW + barGap)
                topMargin = barsY + (AndroidUtilities.dp(40f) - barBaseH) / 2
            }
            bubble.addView(bar, barLp)
            bars.add(bar)
        }

        val bubbleLp = FrameLayout.LayoutParams(abW, abH).apply {
            leftMargin = (sw - abW) / 2
            topMargin = abY
        }
        appendBg.addView(bubble, bubbleLp)

        // Hint label (iOS: "松手 转文字，上滑取消", white, 14sp medium)
        val hint = AppCompatTextView(ctx).apply {
            this.text = ctx.getString(R.string.voice_release_to_text)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        val hintLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = abY + abH + AndroidUtilities.dp(16f)
        }
        appendBg.addView(hint, hintLp)

        overlay.addView(appendBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        appendOverlay = appendBg
        appendWaveBars = bars
        appendHintLabel = hint

        // Start waveform animation timer
        appendWaveTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    mainHandler.post { updateAppendWaveform() }
                }
            }, 100, 100)
        }
    }

    private fun updateAppendWaveform() {
        val bars = appendWaveBars ?: return
        val amplitude = holdToTalkManager?.smoothedPower ?: 0f

        val barCount = bars.size
        val center = barCount / 2f
        val maxBarH = AndroidUtilities.dp(28f)
        val baseH = AndroidUtilities.dp(6f)
        val containerH = AndroidUtilities.dp(40f)

        for (i in bars.indices) {
            val dist = kotlin.math.abs(i - center) / center
            val attenuation = 1f - dist * 0.6f
            val noise = 0.3f + (Math.random().toFloat() * 0.7f)
            val h = (baseH + amplitude * maxBarH * attenuation * noise).toInt()
                .coerceIn(baseH, containerH - AndroidUtilities.dp(4f))
            val lp = bars[i].layoutParams as FrameLayout.LayoutParams
            lp.height = h
            lp.topMargin = (AndroidUtilities.dp(70f) - AndroidUtilities.dp(40f)) / 2 + (containerH - h) / 2
            bars[i].layoutParams = lp
        }
    }

    private fun showBubbleThinking() {
        val bubbleContainer = resultBubbleContainer ?: return

        // Hide bottom buttons
        resultBottomViews?.forEach { it.visibility = View.INVISIBLE }

        // Make text non-editable
        resultBubbleEditText?.isFocusable = false
        resultBubbleEditText?.isFocusableInTouchMode = false

        // Semi-transparent overlay on bubble (iOS: light blue 0.75 alpha)
        val thinkingBg = FrameLayout(iConversationContext.chatActivity).apply {
            setBackgroundColor(android.graphics.Color.argb(191, 224, 240, 255))
        }
        bubbleContainer.addView(thinkingBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        thinkingOverlayView = thinkingBg

        // 3 animated dots (iOS: 10pt, blue, cycling at 0.35s)
        val dotSize = AndroidUtilities.dp(10f)
        val dotGap = AndroidUtilities.dp(12f)
        val totalW = dotSize * 3 + dotGap * 2
        val dots = mutableListOf<View>()
        for (i in 0..2) {
            val dot = View(iConversationContext.chatActivity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.argb(200, 89, 140, 217))
                }
                alpha = 0.8f
            }
            val lp = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = android.view.Gravity.CENTER
                leftMargin = (i - 1) * (dotSize + dotGap)
            }
            thinkingBg.addView(dot, lp)
            dots.add(dot)
        }
        thinkingDots = dots

        // Animate dots (scale 1.0 → 1.4 → 1.0, cycling)
        thinkingAnimator = ValueAnimator.ofFloat(0f, 3f).apply {
            duration = 1050
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                for (i in dots.indices) {
                    val phase = ((v - i + 3) % 3)
                    val scale = if (phase < 0.5f) 1f + 0.4f * (phase / 0.5f)
                    else if (phase < 1f) 1.4f - 0.4f * ((phase - 0.5f) / 0.5f)
                    else 1f
                    dots[i].scaleX = scale
                    dots[i].scaleY = scale
                    dots[i].alpha = if (phase < 1f) 1f else 0.8f
                }
            }
            start()
        }
    }

    private fun hideBubbleThinking() {
        thinkingAnimator?.cancel()
        thinkingAnimator = null

        thinkingOverlayView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        thinkingOverlayView = null
        thinkingDots = null

        // Restore bottom buttons
        resultBottomViews?.forEach { it.visibility = View.VISIBLE }

        // Restore text editable
        resultBubbleEditText?.isFocusable = true
        resultBubbleEditText?.isFocusableInTouchMode = true
    }

    private fun hideAppendRecordingUI() {
        appendWaveTimer?.cancel()
        appendWaveTimer = null
        appendOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        appendOverlay = null
        appendWaveBars = null
        appendHintLabel = null
    }

    private fun dismissResultMode() {
        isResultMode = false
        hideBubbleThinking()
        hideAppendRecordingUI()
        resultOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        resultOverlay = null
        resultBubbleEditText = null
        resultBubbleContainer = null
        resultBottomViews = null
    }

    fun releaseHoldToTalk() {
        holdToTalkManager?.release()
        holdToTalkManager = null
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
                // 表情面板要保留输入框光标：面板里点表情是往输入框插入、右下角退格键是往输入框删，
                // 没有光标就看不出会插到哪 / 删掉哪。上面的 loseFocus 把焦点转交给了父容器
                // （见 SoftKeyboardUtils.loseFocus），而 TextView 只在 isFocused() 时才画光标。
                //
                // 这里在原有 loseFocus + hideInput 之后再把焦点要回来，而不是直接不调 loseFocus ——
                // 保留那套压键盘的顺序，避免动到它原本可能在兜的 OEM 键盘反弹。requestFocus 本身
                // 不拉输入法，且 ChatActivity 是 stateAlwaysHidden，重新获焦也不会把系统键盘带出来。
                // 其他工具栏面板（+ 更多等）不涉及输入框编辑，行为保持不变。
                if (mChatToolBarMenu.sid == EMOJI_TOOL_BAR_SID ||
                    mChatToolBarMenu.sid == STICKER_TOOL_BAR_SID
                ) {
                    SoftKeyboardUtils.getInstance().requestFocus(editText)
                }
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
                                        if (forwardContent.userList.isEmpty()) {
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
                                    val wkMsg = chatAdapter.getItem(i).wkMsg
                                    val content = wkMsg.baseContentMsgModel
                                    if (content != null && MessageForwardSupport.allowForward(wkMsg.type)) {
                                        list.add(content)
                                    } else {
                                        list.add(WKTextContent(content?.displayContent.orEmpty()))
                                    }
                                }
                                i++
                            }
                            if (list.isNotEmpty()) {
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
            if (list.isNotEmpty()) {
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

    // =====================================================================
    // 图文混排（RichText=14）输入框附件托盘（Phase 2，对齐 web#237）
    // =====================================================================

    /**
     * 构建输入框上方的附件托盘缩略图条（横向 RecyclerView + 拖拽调序），挂在
     * followScrollLayout（与 newImageLayout / remind 等输入区附属视图同一层）。默认隐藏，
     * 托盘非空时显示。完全代码构建，不新增 layout xml，与既有附属视图做法一致。
     */
    private fun initRichTextTray() {
        val layout = LinearLayout(iConversationContext.chatActivity)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.visibility = View.GONE
        layout.setBackgroundColor(
            ContextCompat.getColor(iConversationContext.chatActivity, R.color.chat_face_tab_bg)
        )
        layout.setPadding(
            AndroidUtilities.dp(8f),
            AndroidUtilities.dp(6f),
            AndroidUtilities.dp(8f),
            AndroidUtilities.dp(6f)
        )

        val recyclerView = RecyclerView(iConversationContext.chatActivity)
        recyclerView.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        val adapter = WKRichTextTrayAdapter(
            iConversationContext.chatActivity,
            richTextTray,
            onRemove = { id ->
                // 发送进行中禁止 remove：flushRichTextTraySend 已快照 orderedPaths，
                // 此时 ✕ 会让用户以为取消了某图，但冻结快照仍会上传发出，
                // 随后 onEnqueued 清空托盘 → 被删的图还是发了（ghost-send，CR P2）。
                // 与 add 路径同步由 richTextTraySending 门控。
                if (richTextTraySending) return@WKRichTextTrayAdapter
                if (richTextTray.removeById(id)) {
                    refreshRichTextTray()
                }
            },
            onReorder = {
                // 顺序已变：发送 payload 会按模型新顺序打包，无需额外动作。
            },
            onAddTapped = {
                // 末尾「+」cell：再次拉相册按 remaining 限张数，不允许选视频，gif 也跳过
                // （tray 只承载静态图）。对齐 iOS WKMoreItemClickEvent.addMorePendingImagesForContext。
                if (richTextTraySending) return@WKRichTextTrayAdapter
                pickMorePendingImages()
            },
            onPreview = { index ->
                // 点缩略图全屏预览：复用项目现有 WKDialogUtils.showImagePopup（XPopup
                // CustomImageViewerPopup），单图也支持翻页 / 1/N 角标，外观保持 Android 风格。
                showPendingImagePreview(index)
            }
        )
        recyclerView.adapter = adapter

        // 长按拖拽调序：ItemTouchHelper 逐格 onMove 把 UI 交换映射回模型（真实顺序）。
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // 末尾 + cell 不可拖动（adapter 自有判定，外部 ItemTouchHelper 同步挡一道）。
                if (!adapter.isReorderable(viewHolder.bindingAdapterPosition)) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // 发送进行中禁止拖拽调序：同 onRemove，冻结快照已取，
                // 此时调序不会反映到实际发送 payload，会误导用户（CR P2）。
                if (richTextTraySending) return false
                // 拖到 + cell 位置上不允许（onItemMove 内部也会再过滤）。
                if (!adapter.isReorderable(target.bindingAdapterPosition)) return false
                return adapter.onItemMove(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除（删除走缩略图右上角 ✕，语义更明确）。
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(recyclerView)

        layout.addView(
            recyclerView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                64  // dp，与 iOS kPendingThumbSize 对齐；外层 padding 6/6 → 总高 76dp 接近 iOS preferredHeight=80。
                    // 注意 LayoutHelper.createLinear 内部已 resolve(size)→dp 转换，外层不要再包 AndroidUtilities.dp。
            )
        )
        // 把 tray 插入 bottomView（≈ iOS inputPanel.contentView）里 panelView 之上, 与 iOS
        // 把 bar 嵌在 topView 与 messageToolBar 之间对齐。不要放进 followScrollLayout —— 那
        // 是 @ / 菜单 / 命令等弹出列表的 stack, 同层会被 tray 不透明背景挡住 (#bug 选图后 @
        // popup 看不见 root cause)。
        run {
            val bottomParent = parentView as? android.widget.LinearLayout
            val panelChild = parentView.findViewById<View>(R.id.panelView)
            if (bottomParent != null && panelChild != null) {
                val insertAt = bottomParent.indexOfChild(panelChild)
                bottomParent.addView(
                    layout,
                    if (insertAt >= 0) insertAt else bottomParent.childCount,
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                // 兜底：拿不到 LinearLayout / panelView 时落回 followScrollLayout（旧行为，
                // 与 @ popup 会冲突，但保证 bar 至少能渲染）。
                followScrollLayout.addView(
                    layout,
                    LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.BOTTOM
                    )
                )
            }
        }

        trayLayout = layout
        trayRecyclerView = recyclerView
        trayAdapter = adapter
    }

    /**
     * 把本次选取的静态图片加入「待发送图片栏」末尾（对齐 iOS d0ee07d
     * WKMoreItemClickEvent.onPhotoItemPressed → ctx appendPendingImageDatas:）。
     * 加入后刷新 bar 并更新发送键可见性（bar 非空即便没文本也允许发送）。
     * 返回 true 表示已接管。
     *
     * <p>iOS 在这一步已经把"全屏 caption 编辑页"删掉了（WKRichTextCaptionViewController），
     * Android 同步对齐：直接 addAll 到模型并刷新视图，不再启动二级 Activity。textView 草稿
     * 保持原状不动（用户在主聊天页直接补 caption / @ 人）。
     *
     * <p>in-flight 期间拒绝加入（YUJ-2872 同款防护）：一条托盘发送已捕获 orderedPaths 在异步
     * 上传中，其 onEnqueued 会 clearRichTextTray() 清空整个托盘。若此时把新一批图加进<em>同一个</em>
     * 托盘，它们既不在本次发送快照里、又会被这次 clear 抹掉 → 静默丢图。故 in-flight 期间不接管，
     * 返回 false 让调用方走原逐条发送（新图照常单发出去，不丢）。
     */
    fun addImagesToRichTextTray(imageLocalPaths: List<String>?): Boolean {
        if (imageLocalPaths.isNullOrEmpty()) {
            return false
        }
        if (richTextTraySending) {
            return false
        }
        // 还能塞几张：受 9 张硬上限和已有数量约束（与 iOS appendImageDatas: 同口径）。
        val remaining = WKRichTextComposeModel.MAX_IMAGES - richTextTray.size()
        if (remaining <= 0) {
            val msg = String.format(
                iConversationContext.chatActivity.getString(com.chat.base.R.string.richtext_image_limit),
                WKRichTextComposeModel.MAX_IMAGES
            )
            com.chat.base.utils.WKToastUtils.getInstance().showToastNormal(msg)
            return true  // 已接管（拒收，提示完返回，避免外层降级逐条发送）
        }
        val limited = if (imageLocalPaths.size > remaining) {
            val msg = String.format(
                iConversationContext.chatActivity.getString(com.chat.base.R.string.richtext_image_limit),
                WKRichTextComposeModel.MAX_IMAGES
            )
            com.chat.base.utils.WKToastUtils.getInstance().showToastNormal(msg)
            imageLocalPaths.take(remaining)
        } else {
            imageLocalPaths
        }
        richTextTray.addAll(limited)
        refreshRichTextTray()
        return true
    }

    /** 托盘当前是否有待发图片。 */
    fun hasRichTextTrayImages(): Boolean = !richTextTray.isEmpty()

    /**
     * 点发送键时把「输入框文本 + 托盘有序图片」聚合为一条 type=14 发出（Phase 2 输入框
     * 附件托盘；真·穿插的文/图块交错排序留 Phase 3）。委派给 ChatActivity 的发送侧聚合实现
     * （复用 Phase 1 WKRichTextSender 的原子性 / 跨频道路由 / 文本必达 / snapshot 清空等已验证
     * 能力），本方法只负责：in-flight 防重入、超字节校验、组装顺序参数、入队后清空托盘 + 输入框。
     *
     * <p>原子性（承接 Phase 1 教训）：托盘与输入框的清空必须在<strong>消息真正入队后</strong>
     * 才做（onEnqueued）。上传未完成期间图片留在托盘、文本留在输入框。注：托盘仅内存态，
     * 进程死会丢失托盘图片（文本草稿另有持久化）——这是 accepted scope 的 UX 非对称，非发送原子性回归。
     *
     * @return true 表示本次点击已被托盘发送接管（含「超字节弹转文件框」）；false 表示未接管
     *         （如进入 reply/edit 态），调用方应继续走原有文本 / reply / edit 发送路径。
     */
    private fun flushRichTextTraySend(): Boolean {
        // in-flight 防重入：上一次托盘发送还在上传图片期间，吞掉重复点击，避免重复 type=14。
        if (richTextTraySending) {
            return true
        }
        val rawTextRaw = editText.text?.toString() ?: ""
        // 纯空白（只有空格 / 换行）归一化为 ""：否则 WKRichTextSender 的 TextUtils.isEmpty("  ")
        // 判为非空，会发出空白 text block，甚至在全图失败降级时发一条空白纯文本。与
        // previewBlocks 的「空白不出 text 块」语义一致。
        val rawText = if (rawTextRaw.isBlank()) "" else rawTextRaw
        val orderedPaths = richTextTray.orderedPaths()
        if (orderedPaths.isEmpty()) {
            return false
        }
        // 文本超字节上限：交回发送键转文件路径（与纯文本同源阈值），不发超限 payload。
        if (!TextUtils.isEmpty(rawText) && isTextOverByteLimit(rawText)) {
            showTextToFileAlert(rawText)
            return true
        }
        richTextTraySending = true
        val handled = iConversationContext.sendRichTextTray(rawText, orderedPaths, {
            // 入队后回调（主线程）：清托盘 + 清输入框。snapshot 由 ChatActivity 侧把关跨频道
            // / 新草稿安全，这里只在回调里做 UI 收口。
            clearRichTextTray()
            // snapshot-aware 清输入框（对齐 Phase 1 YUJ-2872 defect a）：上传可耗时数秒，用户
            // 在等待期间可能又打了新草稿。只有当输入框仍恰好等于<em>发起时</em>的原始可见内容
            // （rawTextRaw，含空白）时才清，否则保留用户新打的内容，绝不擦新草稿。
            val current = editText.text?.toString() ?: ""
            if (WKRichTextSender.shouldClearComposer(rawTextRaw, current)) {
                editText.text = null
                lastInputTime = 0
            }
            if (chatTopView?.visibility == View.VISIBLE) {
                CommonAnim.getInstance().animateClose(chatTopView)
            }
        }, {
            // 终态回调（主线程）：复位 in-flight 防重入标志。覆盖「全图失败且无文本→什么都没发」
            // 这种 onEnqueued 永不触发的终态，否则发送键会永久失灵；并更新发送键可见性。
            richTextTraySending = false
            updateSendBtnForTray()
        })
        if (!handled) {
            // 上下文未接管（如 reply/edit 态 sendRichTextTray 返回 false）。复位 in-flight 标志，
            // 返回 false 让发送键继续走原有文本 / reply / edit 路径。托盘图片保留（用户退出
            // reply/edit 后可再发）。
            richTextTraySending = false
            return false
        }
        return true
    }

    /** 清空托盘（发送入队后、或切换频道时调用）并刷新 UI。 */
    fun clearRichTextTray() {
        // 切频道也会走到这里：复位 in-flight 标志，避免旧频道一条托盘发送 in-flight 时切到
        // 新频道后，标志残留把新频道的托盘发送永久卡住。
        richTextTraySending = false
        if (richTextTray.isEmpty() && trayLayout?.visibility != View.VISIBLE) {
            return
        }
        richTextTray.clear()
        refreshRichTextTray()
    }

    /** 同步托盘 UI（显隐 + 列表刷新 + 发送键可见性）到模型当前状态。 */
    private fun refreshRichTextTray() {
        trayAdapter?.notifyDataSetChanged()
        val hasItems = !richTextTray.isEmpty()
        trayLayout?.visibility = if (hasItems) View.VISIBLE else View.GONE
        updateSendBtnForTray()
    }

    /**
     * 语音模式 STT 文本直发路径（无 pending 图的情况）。从原 HoldToTalkManager.Listener.onSendText
     * 提取出来：扫 mention → 三态分流 → 装配 WKMentionTextContent / WKTextContent → 发出。
     * 与 tray 路径并存：tray 非空时走 flushRichTextTraySend 聚合 RichText；为空 / tray 拒收时走这里。
     */
    private fun sendVoiceTextDirect(text: String) {
        val allEntities = mutableListOf<WKMsgEntity>()
        val list = mutableListOf<String>()

        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP ||
            iConversationContext.chatChannelInfo.channelType == WKChannelType.COMMUNITY_TOPIC) {
            scanPlainTextMentions(text, allEntities, list)
        }

        // 三态 mention：sentinel uid 不能写进 mention.entities
        allEntities.removeAll { e ->
            e.type == ChatContentSpanType.mention &&
                    (e.value == "-1" || e.value == "-2")
        }

        val hasMentions = list.isNotEmpty()
        val textMsgModel = if (hasMentions)
            WKMentionTextContent(text) else WKTextContent(text)
        if (hasMentions) {
            val mMentionInfo = WKMentionInfo()
            val uidList: MutableList<String> = ArrayList()
            for (uid in list) {
                when {
                    uid.equals("-1", ignoreCase = true) -> {
                        // 三态 mention：@所有人 走新协议 mention.humans=1，
                        // 不再设置 legacy mention.all=1（旧 adapter bot 误唤醒）。
                        // 对齐 iOS dmwork-ios#129 / Web。
                        textMsgModel.mentionHumans = 1
                        mMentionInfo.humans = true
                    }
                    uid == "-2" -> {
                        textMsgModel.mentionAis = 1
                        mMentionInfo.ais = true
                    }
                    else -> {
                        uidList.add(uid)
                    }
                }
            }
            if (textMsgModel.mentionAis == 1) {
                expandRobotMembersIntoUids(uidList)
            }
            mMentionInfo.uids = uidList
            textMsgModel.mentionInfo = mMentionInfo
        }
        textMsgModel.entities = allEntities
        iConversationContext.sendMessage(textMsgModel)
    }

    /**
     * 末尾「+」cell 触发：再次拉相册按 remaining 限张数，不允许选视频，gif 也跳过
     * （tray 只承载静态图）。对齐 iOS WKMoreItemClickEvent.addMorePendingImagesForContext。
     */
    private fun pickMorePendingImages() {
        if (richTextTraySending) return
        val remaining = WKRichTextComposeModel.MAX_IMAGES - richTextTray.size()
        if (remaining <= 0) {
            val msg = String.format(
                iConversationContext.chatActivity.getString(com.chat.base.R.string.richtext_image_limit),
                WKRichTextComposeModel.MAX_IMAGES
            )
            com.chat.base.utils.WKToastUtils.getInstance().showToastNormal(msg)
            return
        }
        com.chat.base.glide.GlideUtils.getInstance().chooseIMG(
            iConversationContext.chatActivity, remaining, false,
            com.chat.base.glide.ChooseMimeType.img, false, false,
            object : com.chat.base.glide.GlideUtils.ISelectBack {
                override fun onBack(paths: MutableList<com.chat.base.glide.ChooseResult>?) {
                    if (paths.isNullOrEmpty()) return
                    val pathList = mutableListOf<String>()
                    for (p in paths) {
                        if (p == null || TextUtils.isEmpty(p.path)) continue
                        // gif 走 sticker 路径，不入 tray（与 tryAddRichTextTray 同口径）。
                        if (com.chat.base.utils.WKFileUtils.getInstance().isGif(p.path)) continue
                        pathList.add(p.path)
                    }
                    if (pathList.isEmpty()) return
                    addImagesToRichTextTray(pathList)
                }

                override fun onCancel() {}
            }
        )
    }

    /**
     * 点缩略图：拉起全屏图片预览。复用项目现有 [WKDialogUtils.showImagePopup]
     * （XPopup CustomImageViewerPopup），单图也支持 1/N 角标 / 翻页，外观保持 Android 风格；
     * 行为对齐 iOS 在 bar 上点缩略图调起 YBImageBrowser 的 UX。
     */
    private fun showPendingImagePreview(index: Int) {
        val items = richTextTray.items()
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, items.size - 1)

        // tempImgList: 路径列表，作为 popup 的数据源；imgList: 缩略图 ImageView 列表，用于
        // popup 转场动画的源视图。从 RecyclerView 的 holder 拿对应 ThumbViewHolder.thumb。
        val tempImgList = ArrayList<Any>(items.size)
        val imgList = ArrayList<android.widget.ImageView>(items.size)
        var srcView: android.widget.ImageView? = null
        for (i in items.indices) {
            tempImgList.add(items[i].localPath)
            val holder = trayRecyclerView?.findViewHolderForAdapterPosition(i)
                    as? WKRichTextTrayAdapter.ThumbViewHolder
            val tv = holder?.thumb
            if (tv != null) {
                imgList.add(tv)
                if (i == safeIndex) srcView = tv
            } else {
                // 没复用上的位置，给个占位（XPopup 需要 imgList 数量与 tempImgList 一致）。
                imgList.add(android.widget.ImageView(iConversationContext.chatActivity))
            }
        }
        com.chat.base.utils.WKDialogUtils.getInstance().showImagePopup(
            iConversationContext.chatActivity,
            tempImgList,
            imgList,
            srcView,
            safeIndex,
            ArrayList(),
            null,
            null
        )
    }

    /**
     * 托盘非空时，即便输入框没有文本也应允许发送（纯图片托盘 → 发单条 RichText，
     * 或退化为逐张图片由发送路径决定）。文本存在与否的发送键显隐仍由 TextWatcher 管理，
     * 这里只在「有图无字」时把发送键补显出来；「无图无字」时不强制显示。
     */
    private fun updateSendBtnForTray() {
        if (isVoiceMode) {
            return
        }
        val hasText = !TextUtils.isEmpty(StringUtils.replaceBlank(editText.text?.toString() ?: ""))
        val hasTrayImages = !richTextTray.isEmpty()
        if (hasTrayImages || hasText) {
            if (!isShowSendBtn) {
                sendIV.clearColorFilter()
                sendIV.visibility = View.VISIBLE
            }
            isShowSendBtn = true
        } else {
            isShowSendBtn = false
            sendIV.visibility = View.GONE
        }
    }
}