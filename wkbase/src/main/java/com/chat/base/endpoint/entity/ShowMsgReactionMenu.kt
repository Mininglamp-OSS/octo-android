package com.chat.base.endpoint.entity

import android.widget.FrameLayout
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.xinbida.wukongim.entity.WKMsgReaction

class ShowMsgReactionMenu(
    val parentView: FrameLayout,
    val from: WKChatIteMsgFromType,
    val chatAdapter: ChatAdapter,
    val list: List<WKMsgReaction>?
)