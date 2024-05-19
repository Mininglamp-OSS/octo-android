package com.chat.base.msgitem

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.views.BubbleLayout

class WKUnknownProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_unknown_msg, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val linearLayout = parentView.findViewById<BubbleLayout>(R.id.bubbleLayout)
        val contentTv = parentView.findViewById<TextView>(R.id.contentTv)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        linearLayout.setAll(bgType, from, WKContentType.unknown_msg)
        when (from) {
            WKChatIteMsgFromType.SEND -> {
                linearLayout.gravity = Gravity.END
                contentTv.textSize = 16f
                contentTv.setTextColor(ContextCompat.getColor(context, R.color.black))
            }
            WKChatIteMsgFromType.RECEIVED -> {
                linearLayout.gravity = Gravity.START
                contentTv.textSize = 16f
                contentTv.setTextColor(ContextCompat.getColor(context, R.color.colorDark))
            }
            else -> {
                linearLayout.gravity = Gravity.CENTER
                contentTv.textSize = 12f
                contentTv.setTextColor(Color.parseColor("#8D8D8D"))
                contentTv.setBackgroundResource(R.drawable.radian_normal_layout)
            }
        }
        addLongClick(linearLayout, uiChatMsgItemEntity)
    }

    override val itemViewType: Int
        get() = WKContentType.unknown_msg
}