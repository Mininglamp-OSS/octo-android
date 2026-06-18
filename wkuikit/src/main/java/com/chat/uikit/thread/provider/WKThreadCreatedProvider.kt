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

package com.chat.uikit.thread.provider

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.R
import com.chat.uikit.chat.ChatActivity
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent
import com.chat.uikit.thread.service.ThreadModel

import com.xinbida.wukongim.entity.WKChannelType

class WKThreadCreatedProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? = null

    override fun setData(
        adapterPosition: Int, parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity, from: WKChatIteMsgFromType
    ) {}

    override val itemViewType: Int get() = WKContentType.threadCreated
    override val layoutId: Int get() = R.layout.chat_thread_created_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        val content = item.wkMsg.baseContentMsgModel as? WKThreadCreatedContent ?: return

        // 头像
        val avatarView = helper.getView<AvatarView>(R.id.avatarView)
        avatarView.setSize(36f)
        if (!content.from_uid.isNullOrEmpty()) {
            avatarView.showAvatar(content.from_uid, WKChannelType.PERSONAL)
        }

        // 第一行：xxx 发起了子区  今天 19:47
        val titleTv = helper.getView<TextView>(R.id.titleTv)
        val fromName = content.from_name ?: ""
        val timeStr = if (item.wkMsg.timestamp > 0) {
            WKTimeUtils.getInstance().getShowDateAndMinute(item.wkMsg.timestamp * 1000L)
        } else ""
        titleTv.text = "$fromName ${context.getString(R.string.str_thread_started)}  $timeStr"

        // 第二行左：「子区名称」
        helper.getView<TextView>(R.id.threadNameTv).text = "「${content.thread_name ?: ""}」"

        // 第二行右：优先从缓存取最新消息数量，fallback 到 payload（对齐 iOS latestMessageCount:）
        val actionTv = helper.getView<TextView>(R.id.actionTv)
        val latestCount = WKThreadCreatedContent.getMessageCount(content.channel_id, content.message_count)
        if (latestCount > 0) {
            actionTv.text = String.format(context.getString(R.string.str_thread_msg_count), latestCount)
        } else {
            actionTv.text = context.getString(R.string.str_view_thread)
        }

        // 点击
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            (getAdapter() as? ChatAdapter)?.conversationContext?.hideSoftKeyboard()
        }
        helper.getView<View>(R.id.threadCardLayout).setOnClickListener {
            val channelId = content.channel_id ?: return@setOnClickListener
            val parsed = ThreadModel.getInstance().parseChannelId(channelId)
            if (parsed == null) {
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra("channelId", channelId)
                intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC)
                context.startActivity(intent)
                return@setOnClickListener
            }
            val groupNo = parsed[0]
            val shortId = parsed[1]
            ThreadModel.getInstance().getThreadDetail(groupNo, shortId) { code, msg, entity ->
                if (code == HttpResponseCode.success.toInt() && entity != null) {
                    if (entity.status == 3) {
                        // 子区已关闭: 把源消息从全局 map / set 里清掉, 让长按菜单回到"创建子区"
                        // (对齐 iOS WKThreadCreatedCell.onCardTap → markThreadClosedForSourceMessageId)
                        WKThreadCreatedContent.markThreadClosedForSourceMessageId(content.source_message_id)
                        WKToastUtils.getInstance().showToast(context.getString(R.string.str_thread_closed_tip))
                    } else {
                        val intent = Intent(context, ChatActivity::class.java)
                        intent.putExtra("channelId", channelId)
                        intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC)
                        context.startActivity(intent)
                    }
                } else {
                    // 与 iOS 对齐: 任何错误都视作"已关闭/不存在", 不再按字符串匹配降级直接打开。
                    // 历史上靠 errMsg.contains("deleted"|"已关闭"|"已删除") 识别, 但服务端
                    // 返回 404 或文案变化就不命中, 会回退到"直接打开"路径 → 进入死频道。
                    WKThreadCreatedContent.markThreadClosedForSourceMessageId(content.source_message_id)
                    WKToastUtils.getInstance().showToast(context.getString(R.string.str_thread_closed_tip))
                }
            }
        }
    }
}
