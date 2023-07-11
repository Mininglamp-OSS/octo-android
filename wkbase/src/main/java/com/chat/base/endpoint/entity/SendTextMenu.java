package com.chat.base.endpoint.entity;

import com.chat.base.msg.IConversationContext;

public class SendTextMenu {
    public String text;
    public IConversationContext iConversationContext;

    public SendTextMenu(String text, IConversationContext iConversationContext) {
        this.text = text;
        this.iConversationContext = iConversationContext;
    }
}
