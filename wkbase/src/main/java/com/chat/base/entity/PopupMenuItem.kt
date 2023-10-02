package com.chat.base.entity

class PopupMenuItem(var text: String, var iconResourceID: Int, var iClick: IClick) {
    var color: Int = 0
    var subText: String = ""
    var tag: String = ""

    interface IClick {
        fun onClick()
    }

}