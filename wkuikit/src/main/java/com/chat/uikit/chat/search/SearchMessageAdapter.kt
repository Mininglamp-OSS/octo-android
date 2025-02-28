package com.chat.uikit.chat.search

import android.os.Build
import android.text.Html
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.entity.GlobalMessage
import com.chat.base.msgitem.WKContentType
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R

class SearchMessageAdapter :
    BaseQuickAdapter<GlobalMessage, BaseViewHolder>(R.layout.item_global_message_layout) {
    override fun convert(holder: BaseViewHolder, item: GlobalMessage) {
        val avatarView = holder.getView<AvatarView>(R.id.avatarView)
        avatarView.setSize(40f)
        avatarView.showAvatar(item.channel.channel_id, item.channel.channel_type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            holder.setText(
                R.id.nameTv,
                Html.fromHtml(item.channel.getHtmlName(), Html.FROM_HTML_MODE_LEGACY)
            )
        } else {
            holder.setText(R.id.nameTv, Html.fromHtml(item.channel.getHtmlName()))
        }
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val type = item.getContentType()
        if (type == WKContentType.WK_TEXT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                contentTv.text = Html.fromHtml(item.getHtmlText(), Html.FROM_HTML_MODE_LEGACY)
            } else {
                contentTv.text = Html.fromHtml(item.getHtmlText())
            }
        } else if (type == WKContentType.WK_FILE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                contentTv.text =
                    Html.fromHtml(item.getHtmlWithField("name"), Html.FROM_HTML_MODE_LEGACY)
            } else {
                contentTv.text = Html.fromHtml(item.getHtmlWithField("name"))
            }
        }
        holder.setText(
            R.id.timeTv,
            WKTimeUtils.getInstance().getTimeString(item.timestamp * 1000)
        )

    }
}