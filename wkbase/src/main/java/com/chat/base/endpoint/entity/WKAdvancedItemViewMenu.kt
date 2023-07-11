package com.chat.base.endpoint.entity

import androidx.appcompat.app.AppCompatActivity

class WKAdvancedItemViewMenu(
    channelID: String,
    channelType: Byte,
    activity: AppCompatActivity,
) {
    val channelID: String
    val channelType: Byte
    val activity: AppCompatActivity

    init {
        this.channelID = channelID
        this.channelType = channelType
        this.activity = activity
    }
}