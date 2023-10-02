package com.chat.base.entity

class BottomSheetItem(var text: CharSequence, var icon: Int, var iClick: IBottomSheetClick) {

    interface IBottomSheetClick {
        fun onClick()
    }
}