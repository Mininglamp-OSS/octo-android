package com.chat.uikit.chat.provider

import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.emoji.MoonUtil
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.ChatMultiForwardDetailActivity
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import kotlin.math.min

class WKMultiForwardProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context)
            .inflate(R.layout.chat_item_multi_forward, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val multiView = parentView.findViewById<LinearLayout>(R.id.multiView)
        multiView.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        val titleTv = parentView.findViewById<TextView>(R.id.titleTv)
        val contentTv = parentView.findViewById<TextView>(R.id.contentTv)
        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val multiForwardContent =
            uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKMultiForwardContent
        val title: String = if (multiForwardContent.channelType.toInt() == 1) {
            if (multiForwardContent.userList.size > 1) {
                val sBuilder = StringBuilder()
                for (i in multiForwardContent.userList.indices) {
                    if (!TextUtils.isEmpty(sBuilder)) sBuilder.append("、")
                    sBuilder.append(multiForwardContent.userList[i].channelName)
                }
                sBuilder.toString()
            } else multiForwardContent.userList[0].channelName
        } else {
            context.getString(R.string.group_chat)
        }
        titleTv.text = String.format(context.getString(R.string.chat_title_records), title)
        //设置内容
        val sBuilder = StringBuilder()
        if (multiForwardContent.msgList != null && multiForwardContent.msgList.size > 0) {
            val size = min(multiForwardContent.msgList.size, 3)
            for (i in 0 until size) {
                var name = ""
                var content = ""
                val messageContent = multiForwardContent.msgList[i].baseContentMsgModel
                if (messageContent != null) {
                    if (!TextUtils.isEmpty(messageContent.fromUID)) {
                        val mChannel = WKIM.getInstance().channelManager.getChannel(
                            messageContent.fromUID,
                            WKChannelType.PERSONAL
                        )
                        if (mChannel != null) {
                            name = mChannel.channelName
                        } else {
                            WKIM.getInstance().channelManager.fetchChannelInfo(
                                messageContent.fromUID,
                                WKChannelType.PERSONAL
                            )
                        }
                    }
                    content = messageContent.getDisplayContent()
                    // 如果文字太长滑动会卡顿
                    if (content.length > 100) {
                        content = content.substring(0, 80)
                    }
                }
                if (!TextUtils.isEmpty(sBuilder)) sBuilder.append("\n")
                sBuilder.append(name).append(":").append(content)
            }
        }
        // 显示表情
        MoonUtil.identifyFaceExpression(context, contentTv, sBuilder.toString(), MoonUtil.DEF_SCALE)
        addLongClick(contentLayout, uiChatMsgItemEntity)
        contentLayout.setOnClickListener {
            val intent = Intent(context, ChatMultiForwardDetailActivity::class.java)
            intent.putExtra("client_msg_no", uiChatMsgItemEntity.wkMsg.clientMsgNO)
            context.startActivity(intent)
        }
    }

    override val itemViewType: Int
        get() = WKContentType.WK_MULTIPLE_FORWARD

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
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        contentLayout.setAll(bgType, from, WKContentType.WK_MULTIPLE_FORWARD)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        addLongClick(contentLayout, uiChatMsgItemEntity)
    }
}