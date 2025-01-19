package com.chat.uikit.chat.provider

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R

class WKSpanEmptyProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        var height = AndroidUtilities.dp(50f)
        if (uiChatMsgItemEntity.wkMsg != null) {
            height = uiChatMsgItemEntity.wkMsg.messageSeq
        }
        contentLayout.layoutParams.height = height
    }

    override val itemViewType: Int
        get() = WKContentType.spanEmptyView

    override val layoutId: Int
        get() = R.layout.chat_item_span_empty_view
}