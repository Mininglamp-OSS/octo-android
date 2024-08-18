package com.chat.uikit.chat.manager;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

public class SendMsgEntity {
    public WKMessageContent messageContent;
    public WKChannel wkChannel;
    public WKSendOptions options;

    public SendMsgEntity(WKMessageContent messageContent, WKChannel channel, WKSendOptions options) {
        this.wkChannel = channel;
        this.messageContent = messageContent;
        this.options = options;
    }
}
