package com.chat.base.endpoint.entity;

import com.chat.base.msg.ChatAdapter;
import com.xinbida.wukongim.entity.WKMsg;

/**
 * 4/16/21 5:08 PM
 * 消息回应
 */
public class MsgReactionMenu {
    public String emoji;
    public ChatAdapter chatAdapter;
    public int[] location;
    public WKMsg wkMsg;

    public MsgReactionMenu(WKMsg wkMsg, String emoji, ChatAdapter chatAdapter, int[] location) {
        this.emoji = emoji;
        this.wkMsg = wkMsg;
        this.chatAdapter = chatAdapter;
        this.location = location;
    }
}
