package com.chat.base.ui.components

class NormalClickableContent(val type: NormalClickableTypes, val content: String) {

    enum class NormalClickableTypes {
        Remind, URL, Other
    }
}