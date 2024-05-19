package com.chat.base.endpoint.entity

import com.xinbida.wukongim.entity.WKMsg

class PrivacyMessageMenu(val iClick: IClick) {

    interface IClick {
        fun onDelete(mMsg: WKMsg)
        fun clearChannelMsg(channelID: String, channelType: Byte)
    }
}