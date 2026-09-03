/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.msgitem

import android.content.Context
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
import com.xinbida.wukongim.entity.WKMsg
import org.json.JSONObject

/**
 * 群总结完成提示 (type=21) 的系统提示渲染, 对齐 octo-web SummaryNotifyCell。
 *
 * 该类型由 Web 端在总结任务完成时发往来源群, content 形如
 * `{from_uid, from_name}`, 不在 1000-2000 系统号段内 —— 未适配前会被
 * [ChatAdapter.getItemType] 降级成 unknown_msg(-3) 显示 "未知消息"。
 *
 * 实现照搬 [WKScreenshotProvider]: 复用 chat_system_layout 居中灰底提示,
 * 且刻意不调 addLongClick() —— 系统提示不该有长按菜单 (转发/回复/创建子区
 * 对一条提示都不成立)。
 *
 * Web 自 octo-web d31a10c3 起改用 WK_TIP(2000) 发新消息, 2000 走
 * [WKSystemProvider] 通用路径无需适配, 本 provider 只负责存量历史消息。
 */
class WKSummaryNotifyProvider : WKChatBaseProvider() {
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
        get() = WKContentType.summaryNotify

    override val layoutId: Int
        get() = R.layout.chat_system_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            val chatAdapter = getAdapter() as ChatAdapter
            chatAdapter.conversationContext.hideSoftKeyboard()
        }
        val textView = helper.getView<TextView>(R.id.contentTv)
        val content = showSummaryTip(context, item.wkMsg)
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

    companion object {
        /**
         * 生成 "xxx总结了群聊内容" 文案。聊天页气泡与会话列表预览共用一份,
         * 沿用 [WKRevokeProvider.showRevokeMsg] 的既有模式。
         */
        @JvmStatic
        fun showSummaryTip(context: Context, msg: WKMsg): String {
            return String.format(context.getString(R.string.summary_notify_tip), resolveName(context, msg))
        }

        /**
         * 显示名优先级: 前四级复用 [WKChatBaseProvider.setFromName] 的群内显示名口径,
         * 末两级对齐 Web SummaryNotifyContent.tipForSender()。
         *
         * from_name 排在本地资料之后 —— 与 Web 注释同口径: 身份以信封 uid 为准,
         * from_name 只是本地资料拿不到时的尽力兜底。
         */
        private fun resolveName(context: Context, msg: WKMsg): String {
            if (!TextUtils.isEmpty(msg.fromUID) && msg.fromUID == WKConfig.getInstance().uid) {
                return context.getString(R.string.summary_notify_you)
            }
            var name = msg.from?.channelRemark
            if (TextUtils.isEmpty(name)) {
                name = msg.memberOfFrom?.remark
            }
            if (TextUtils.isEmpty(name)) {
                name = msg.memberOfFrom?.memberRemark
            }
            if (TextUtils.isEmpty(name)) {
                name = msg.from?.channelName
            }
            if (TextUtils.isEmpty(name)) {
                name = msg.memberOfFrom?.memberName
            }
            if (TextUtils.isEmpty(name)) {
                name = fromNameOf(msg.content)
            }
            return if (TextUtils.isEmpty(name)) {
                context.getString(R.string.summary_notify_unknown)
            } else {
                name!!
            }
        }

        private fun fromNameOf(contentJson: String?): String {
            if (TextUtils.isEmpty(contentJson)) return ""
            return try {
                JSONObject(contentJson!!).optString("from_name", "")
            } catch (_: Exception) {
                ""
            }
        }
    }
}
