package com.chat.base.msgitem

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.views.BubbleLayout

class WKChatFormatErrorProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context)
            .inflate(R.layout.chat_content_format_err_layout, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val linearLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val contentTv = parentView.findViewById<TextView>(R.id.contentTv)
        val bubbleLayout = parentView.findViewById<BubbleLayout>(R.id.bubbleLayout)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        bubbleLayout.setAll(bgType, from, WKContentType.WK_CONTENT_FORMAT_ERROR)
        if (from == WKChatIteMsgFromType.SEND) {
            linearLayout.gravity = Gravity.END
            contentTv.setTextColor(ContextCompat.getColor(context, R.color.black))
        } else if (from == WKChatIteMsgFromType.RECEIVED) {
            linearLayout.gravity = Gravity.START
            contentTv.setTextColor(ContextCompat.getColor(context, R.color.colorDark))
        }
        addLongClick(bubbleLayout, uiChatMsgItemEntity)
    }

    override val itemViewType: Int
        get() = WKContentType.WK_CONTENT_FORMAT_ERROR
}