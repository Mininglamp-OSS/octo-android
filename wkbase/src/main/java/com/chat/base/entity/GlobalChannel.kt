package com.chat.base.entity

import com.xinbida.wukongim.entity.WKChannelType

class GlobalChannel {
    var channel_id: String = ""
    var channel_type: Byte = WKChannelType.PERSONAL
    var channel_name: String = ""
    var channel_remark: String = ""


    fun getHtmlName():String{
        return channel_name.replace("<mark>", "<font color=#f65835>")
            .replace("</mark>", "</font>")
    }
}