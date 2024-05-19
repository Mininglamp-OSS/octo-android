package com.chat.base.msgitem

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.views.BubbleLayout

class WKSignalDecryptErrorProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context)
            .inflate(R.layout.chat_signal_decrypt_err_layout, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val linearLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val bubbleLayout = parentView.findViewById<BubbleLayout>(R.id.bubbleLayout)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        bubbleLayout.setAll(bgType, from, WKContentType.WK_SIGNAL_DECRYPT_ERROR)
        val contentTv = parentView.findViewById<TextView>(R.id.contentTv)

        when (from) {
            WKChatIteMsgFromType.SEND -> {
                linearLayout.gravity = Gravity.END
                contentTv.setTextColor(Color.parseColor("#313131"))
            }
            WKChatIteMsgFromType.RECEIVED -> {
                linearLayout.gravity = Gravity.START
                contentTv.setTextColor(ContextCompat.getColor(context, R.color.colorDark))
            }
            else -> {
                linearLayout.gravity = Gravity.CENTER
                contentTv.textSize = 12f
                contentTv.setTextColor(Color.parseColor("#8D8D8D"))
                contentTv.setBackgroundResource(R.drawable.radian_normal_layout)
            }
        }
        addLongClick(bubbleLayout, uiChatMsgItemEntity)
    }

    override val itemViewType: Int
        get() = WKContentType.WK_SIGNAL_DECRYPT_ERROR
}