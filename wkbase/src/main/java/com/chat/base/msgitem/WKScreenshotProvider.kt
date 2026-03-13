package com.chat.base.msgitem

import android.text.SpannableString
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.R
import com.chat.base.config.WKConfig
import com.chat.base.msg.ChatAdapter
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan
import com.chat.base.utils.AndroidUtilities
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import org.json.JSONObject

class WKScreenshotProvider : WKChatBaseProvider() {
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
        get() = WKContentType.screenshot

    override val layoutId: Int
        get() = R.layout.chat_system_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            val chatAdapter = getAdapter() as ChatAdapter
            chatAdapter.conversationContext.hideSoftKeyboard()
        }
        val textView = helper.getView<TextView>(R.id.contentTv)
        val content = getScreenshotTip(item)
        textView.setShadowLayer(AndroidUtilities.dp(5f).toFloat(), 0f, 0f, 0)
        val str = SpannableString(content)
        str.setSpan(
            SystemMsgBackgroundColorSpan(
                ContextCompat.getColor(context, R.color.colorSystemBg),
                AndroidUtilities.dp(5f),
                AndroidUtilities.dp((2 * 5).toFloat())
            ), 0, content.length, 0
        )
        textView.text = str
    }

    private fun getScreenshotTip(item: WKUIChatMsgItemEntity): String {
        val loginUid = WKConfig.getInstance().uid
        val fromUid = item.wkMsg.fromUID
        val name: String = if (!TextUtils.isEmpty(fromUid) && fromUid == loginUid) {
            context.getString(R.string.str_you)
        } else {
            // 尝试从消息 JSON 获取名字
            var fromName = ""
            try {
                val json = JSONObject(item.wkMsg.content)
                fromName = json.optString("from_name", "")
            } catch (_: Exception) {
            }
            if (TextUtils.isEmpty(fromName) && !TextUtils.isEmpty(fromUid)) {
                // 从频道信息获取名字
                val channel = WKIM.getInstance().channelManager.getChannel(fromUid, WKChannelType.PERSONAL)
                fromName = channel?.channelName ?: ""
            }
            fromName.ifEmpty { context.getString(R.string.str_someone) }
        }
        return String.format(context.getString(R.string.screenshot_tip), name)
    }
}
