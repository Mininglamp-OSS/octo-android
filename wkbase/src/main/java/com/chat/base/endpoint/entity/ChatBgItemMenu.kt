package com.chat.base.endpoint.entity

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class ChatBgItemMenu(
    val activity: AppCompatActivity,
    val parentView: ViewGroup,
    val channelID: String,
    val channelType: Byte
) {
}