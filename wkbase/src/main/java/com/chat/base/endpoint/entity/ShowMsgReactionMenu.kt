package com.chat.base.endpoint.entity

import android.view.View
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.xinbida.wukongim.entity.WKMsgReaction

class ShowMsgReactionMenu(
    val parentView: View,
    val from: WKChatIteMsgFromType,
    val chatAdapter: ChatAdapter,
    val list: List<WKMsgReaction>?
)