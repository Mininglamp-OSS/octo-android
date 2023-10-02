package com.chat.uikit.chat.provider

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.WordToSpan
import com.chat.uikit.R
import com.chat.uikit.contacts.service.FriendModel
import com.xinbida.wukongim.WKIM
import java.util.*

class WKNoRelationProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {

    }

    override val itemViewType: Int
        get() = WKContentType.noRelation

    override val layoutId: Int
        get() = R.layout.chat_item_no_relation_layout

    override fun convert(
        helper: BaseViewHolder,
        item: WKUIChatMsgItemEntity
    ) {
        super.convert(helper, item)
        var showName = ""
        val mChannel = WKIM.getInstance().channelManager.getChannel(
            item.wkMsg.channelID,
            item.wkMsg.channelType
        )
        if (mChannel != null) {
            showName =
                if (TextUtils.isEmpty(mChannel.channelRemark)) mChannel.channelName else mChannel.channelRemark
        }
        val content = String.format(context.getString(R.string.no_relation_request), showName)
        helper.setText(R.id.contentTv, content)
        val link = WordToSpan()
        link.setColorCUSTOM(Theme.colorAccount)
            .setUnderlineURL(true).setRegexCUSTOM(context.getString(R.string.send_request))
            .setLink(content)
            .into(helper.getView(R.id.contentTv))
            .setClickListener { _: String?, _: String? ->
                (Objects.requireNonNull(
                    getAdapter()
                ) as ChatAdapter).conversationContext.hideSoftKeyboard()
                WKDialogUtils.getInstance().showInputDialog(
                    context,
                    context.getString(R.string.apply),
                    context.getString(R.string.input_remark),
                    "",
                    "",
                    20
                ) { text ->
                    FriendModel.getInstance()
                        .applyAddFriend(
                            item.wkMsg.channelID, "", text
                        ) { code: Int, msg: String? ->
                            if (code == HttpResponseCode.success.toInt()) {
                                WKToastUtils.getInstance()
                                    .showToastNormal(context.getString(R.string.applyed))
                            } else {
                                WKToastUtils.getInstance().showToastNormal(msg)
                            }
                        }
                }
            }
    }

}