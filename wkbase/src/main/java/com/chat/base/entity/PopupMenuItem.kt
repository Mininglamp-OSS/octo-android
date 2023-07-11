package com.chat.base.entity

class PopupMenuItem(text: String, iconResourceID: Int, iClick: IClick) {
    val text: String
    val iconResourceID: Int
    val iClick: IClick

    init {
        this.iClick = iClick
        this.text = text
        this.iconResourceID = iconResourceID
    }

    interface IClick {
        fun onClick()
    }

}