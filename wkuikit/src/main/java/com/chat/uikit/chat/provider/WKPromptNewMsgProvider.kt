package com.chat.uikit.chat.provider

import android.view.View
import android.view.ViewGroup
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.uikit.R

class WKPromptNewMsgProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    override val layoutId: Int
        get() = R.layout.chat_item_unread_msg_line
    override val itemViewType: Int
        get() = WKContentType.msgPromptNewMsg

}