package com.chat.base.endpoint.entity

import android.view.View
import android.widget.ImageView
import com.chat.base.views.blurview.ShapeBlurView

class SetChatBgMenu(
    val channelID: String,
    val channelType: Byte,
    val backGroundIV: ImageView,
    val rootLayout: View,
    val blurView : ShapeBlurView
) {
}