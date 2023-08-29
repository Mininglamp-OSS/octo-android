package com.chat.uikit.chat.manager;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

public class SendMsgEntity {
    public WKMessageContent messageContent;
    public WKChannel wkChannel;
    WKMsgSetting setting;

    public SendMsgEntity(WKMessageContent messageContent, WKChannel channel, WKMsgSetting setting) {
        this.wkChannel = channel;
        this.messageContent = messageContent;
        this.setting = setting;
        if (wkChannel != null) {
            this.messageContent.flame = wkChannel.flame;
            this.messageContent.flameSecond = wkChannel.flameSecond;
        }
    }
}
