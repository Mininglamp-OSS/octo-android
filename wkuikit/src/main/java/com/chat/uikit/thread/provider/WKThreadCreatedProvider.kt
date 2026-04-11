package com.chat.uikit.thread.provider

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.ChatActivity
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent

import com.xinbida.wukongim.entity.WKChannelType

class WKThreadCreatedProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? = null

    override fun setData(
        adapterPosition: Int, parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity, from: WKChatIteMsgFromType
    ) {}

    override val itemViewType: Int get() = WKContentType.threadCreated
    override val layoutId: Int get() = R.layout.chat_thread_created_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        val content = item.wkMsg.baseContentMsgModel as? WKThreadCreatedContent ?: return

        // 头像
        val avatarView = helper.getView<AvatarView>(R.id.avatarView)
        avatarView.setSize(36f)
        if (!content.from_uid.isNullOrEmpty()) {
            avatarView.showAvatar(content.from_uid, WKChannelType.PERSONAL)
        }

        // 第一行：xxx 发起了子区  今天 19:47
        val titleTv = helper.getView<TextView>(R.id.titleTv)
        val fromName = content.from_name ?: ""
        val timeStr = if (item.wkMsg.timestamp > 0) {
            WKTimeUtils.getInstance().getShowDateAndMinute(item.wkMsg.timestamp * 1000L)
        } else ""
        titleTv.text = "$fromName ${context.getString(R.string.str_thread_started)}  $timeStr"

        // 第二行左：「子区名称」
        helper.getView<TextView>(R.id.threadNameTv).text = "「${content.thread_name ?: ""}」"

        // 第二行右：优先从缓存取最新消息数量，fallback 到 payload（对齐 iOS latestMessageCount:）
        val actionTv = helper.getView<TextView>(R.id.actionTv)
        val latestCount = WKThreadCreatedContent.getMessageCount(content.channel_id, content.message_count)
        if (latestCount > 0) {
            actionTv.text = String.format(context.getString(R.string.str_thread_msg_count), latestCount)
        } else {
            actionTv.text = context.getString(R.string.str_view_thread)
        }

        // 点击
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            (getAdapter() as? ChatAdapter)?.conversationContext?.hideSoftKeyboard()
        }
        helper.getView<View>(R.id.threadCardLayout).setOnClickListener {
            val channelId = content.channel_id ?: return@setOnClickListener
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("channelId", channelId)
            intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC)
            context.startActivity(intent)
        }
    }
}
