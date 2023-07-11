package com.chat.base.endpoint.entity;

import com.chat.base.msg.IConversationContext;

/**
 * 4/30/21 4:51 PM
 */
public class RTCMenu {

    public int callType;//0语音1视频
    public IConversationContext iConversationContext;
    public RTCMenu(IConversationContext iConversationContext, int callType) {
       this.iConversationContext = iConversationContext;
        this.callType = callType;
    }
}
