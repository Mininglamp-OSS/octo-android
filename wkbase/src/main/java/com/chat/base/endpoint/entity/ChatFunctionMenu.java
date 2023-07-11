package com.chat.base.endpoint.entity;

import com.chat.base.msg.IConversationContext;

/**
 * 2020-09-02 13:21
 * 聊天面板模块
 */
public class ChatFunctionMenu extends BaseEndpoint {
    public IChatFunctionCLick iChatFunctionCLick;
    public String sid;

    public ChatFunctionMenu(String sid, int imgResourceID, String text, IChatFunctionCLick iChatFunctionCLick) {
        this.sid = sid;
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.iChatFunctionCLick = iChatFunctionCLick;
    }

    public interface IChatFunctionCLick {
        void onClick(IConversationContext conversationContext);
    }
}
