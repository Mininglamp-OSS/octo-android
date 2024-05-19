package com.chat.base.endpoint.entity

import com.chat.base.msg.IConversationContext
import com.xinbida.wukongim.entity.WKMsg

class WKMsg2UiMsgMenu(
    val iConversationContext: IConversationContext,
    val wkMsg: WKMsg,
    val memberCount: Int,
    val showNickName: Boolean,
    val isChoose: Boolean
)