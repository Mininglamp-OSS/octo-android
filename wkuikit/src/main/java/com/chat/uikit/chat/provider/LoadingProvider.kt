package com.chat.uikit.chat.provider

import android.view.View
import android.view.ViewGroup
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.RadialProgressView
import com.chat.uikit.R

class LoadingProvider : WKChatBaseProvider() {
    override val layoutId: Int
        get() = R.layout.chat_item_loading
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<RadialProgressView>(R.id.progress).setSize(50)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    override val itemViewType: Int
        get() = WKContentType.loading

}