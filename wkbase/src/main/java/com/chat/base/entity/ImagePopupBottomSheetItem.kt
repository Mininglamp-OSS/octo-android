package com.chat.base.entity

class ImagePopupBottomSheetItem(
    var text: CharSequence,
    var icon: Int,
    var iClick: IBottomSheetClick
) {

    interface IBottomSheetClick {
        fun onClick(index: Int)
    }
}