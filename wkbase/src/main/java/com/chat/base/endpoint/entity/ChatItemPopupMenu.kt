package com.chat.base.endpoint.entity

import com.chat.base.msg.IConversationContext
import com.xinbida.wukongim.entity.WKMsg

class ChatItemPopupMenu(imageResource: Int, text: String, iPopupItemClick: IPopupItemClick) {
    var imageResource = 0
    var text = ""
    var iPopupItemClick: IPopupItemClick

    init {
        this.imageResource = imageResource
        this.text = text
        this.iPopupItemClick = iPopupItemClick
    }

    interface IPopupItemClick {
        fun onClick(mMsg: WKMsg, iConversationContext: IConversationContext)
    }
}