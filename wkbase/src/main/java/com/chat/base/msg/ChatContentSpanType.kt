package com.chat.base.msg

class ChatContentSpanType {
    companion object {
        @JvmStatic
        val mention = "mention"

        @JvmStatic
        val link = "link"

        @JvmStatic
        val botCommand = "bot_command"
    }
}