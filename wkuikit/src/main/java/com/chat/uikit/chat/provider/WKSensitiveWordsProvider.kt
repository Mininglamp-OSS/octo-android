package com.chat.uikit.chat.provider

import android.text.SpannableString
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import org.json.JSONException
import org.json.JSONObject

class WKSensitiveWordsProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override val layoutId: Int
        get() = R.layout.chat_item_sensitive_words_layout

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    override fun convert(
        helper: BaseViewHolder,
        item: WKUIChatMsgItemEntity
    ) {
        super.convert(helper, item)
        if (!TextUtils.isEmpty(item.wkMsg.content)) {
            try {
                val jsonObject = JSONObject(item.wkMsg.content)
                val content = jsonObject.optString("content")
                //                baseViewHolder.setText(R. qid.contentTv, content);
                val textView = helper.getView<TextView>(R.id.contentTv)
                textView.setShadowLayer(AndroidUtilities.dp(10f).toFloat(), 0f, 0f, 0)
                val str = SpannableString(content)
                str.setSpan(
                    SystemMsgBackgroundColorSpan(
                        ContextCompat.getColor(
                            context,
                            R.color.colorSystemBg
                        ), AndroidUtilities.dp(5f), AndroidUtilities.dp((2 * 5).toFloat())
                    ), 0, content.length, 0
                )
                textView.text = str
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override val itemViewType: Int
        get() = WKContentType.sensitiveWordsTips
}