package com.chat.base.endpoint.entity

import android.content.Context

class EditMsgMenu(url: String, context: Context) {
    val url: String
    val context: Context

    init {
        this.context = context
        this.url = url
    }
}