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

package com.chat.uikit.chat.provider

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.AvatarView
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.WKCardContent
import com.chat.uikit.user.UserDetailActivity
import com.xinbida.wukongim.entity.WKChannelType

class WKCardProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_card, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val cardView = parentView.findViewById<LinearLayout>(R.id.cardView)
        cardView.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
        val cardNameTv = parentView.findViewById<TextView>(R.id.userNameTv)
        val cardAvatarIv = parentView.findViewById<AvatarView>(R.id.userCardAvatarIv)
        val cardContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKCardContent ?: return
        cardNameTv.text = cardContent.name
        cardAvatarIv.showAvatar(cardContent.uid, WKChannelType.PERSONAL)
        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        parentView.findViewById<View>(R.id.contentLayout).setOnClickListener {
            val intent = Intent(context, UserDetailActivity::class.java)
            intent.putExtra("uid", cardContent.uid)
            intent.putExtra("vercode", cardContent.vercode)
            intent.putExtra("name", cardContent.name)
            context.startActivity(intent)
        }

    }

    override val itemViewType: Int
        get() = WKContentType.WK_CARD

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        contentLayout.setAll(bgType, from, WKContentType.WK_CARD)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        addLongClick(contentLayout, uiChatMsgItemEntity)
    }
}