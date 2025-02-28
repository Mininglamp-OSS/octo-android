package com.chat.uikit.search.remote

import android.os.Build
import android.text.Html
import android.text.TextUtils
import android.widget.TextView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msgitem.WKContentType
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R

class GlobalAdapter : BaseMultiItemQuickAdapter<DataVO, BaseViewHolder>() {
    init {
        addItemType(-1, R.layout.item_global_span_layout)
        addItemType(0, R.layout.item_global_text_layout)
        addItemType(1, R.layout.item_global_channel_layout)
        addItemType(2, R.layout.item_global_message_layout)
        addItemType(3, R.layout.item_global_search_layout)
    }

    override fun convert(holder: BaseViewHolder, item: DataVO) {
        if (item.itemType == 0) {
            holder.setText(R.id.textView, item.text)
        } else if (item.itemType == 1 || item.itemType == 2) {
            val avatarView = holder.getView<AvatarView>(R.id.avatarView)
            avatarView.setSize(40f)
            avatarView.showAvatar(item.channel?.channel_id, item.channel!!.channel_type)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                holder.setText(R.id.nameTv,  Html.fromHtml(item.channel.getHtmlName(),Html.FROM_HTML_MODE_LEGACY))
            } else {
                holder.setText(R.id.nameTv,  Html.fromHtml(item.channel.getHtmlName()))
            }
            if (item.itemType == 2) {

                val contentTv = holder.getView<TextView>(R.id.contentTv)
                val type = item.message?.getContentType()
                if (type == WKContentType.WK_TEXT) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        contentTv.text = Html.fromHtml(item.message.getHtmlText(),Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        contentTv.text = Html.fromHtml(item.message.getHtmlText())
                    }
                } else if (type == WKContentType.WK_FILE) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        contentTv.text = Html.fromHtml(item.message.getHtmlWithField("name"), Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        contentTv.text = Html.fromHtml(item.message.getHtmlWithField("name"))
                    }
                }
                holder.setText(
                    R.id.timeTv,
                    WKTimeUtils.getInstance().getTimeString(item.message!!.timestamp * 1000)
                )
            }

        } else if (item.itemType == 3) {
            holder.setText(R.id.searchKeyTv, item.text)
        }
    }
}