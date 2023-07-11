package com.chat.base.endpoint.entity;

import com.chat.base.msg.IConversationContext;

/**
 * 4/9/21 1:01 PM
 * 查看消息已读未读详情
 */
public class ReadMsgDetailMenu {
    public String messageID;
    public IConversationContext iConversationContext;

    public ReadMsgDetailMenu(String messageID, IConversationContext iConversationContext) {
        this.messageID = messageID;
        this.iConversationContext = iConversationContext;
    }
}
