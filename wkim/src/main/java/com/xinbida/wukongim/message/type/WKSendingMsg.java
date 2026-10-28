package com.xinbida.wukongim.message.type;

import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.DateUtils;

/**
 * 2020-05-28 17:45
 * 正在发送的消息
 */
public class WKSendingMsg {
    // 消息
    public WKSendMsg wkSendMsg;
    // 发送次数
    public int sendCount;
    // 发送时间
    public long sendTime;
    // 是否可重发本条消息
    public boolean isCanResend;
    public com.xinbida.wukongim.WKIMApplication.SessionContext uploadSessionContext;

    public WKSendingMsg(int sendCount, WKSendMsg wkSendMsg, boolean isCanResend) {
        this.sendCount = sendCount;
        this.wkSendMsg = wkSendMsg;
        this.isCanResend = isCanResend;
        this.sendTime = DateUtils.getInstance().getCurrentSeconds();
    }

    public WKSendingMsg(int sendCount, WKSendMsg wkSendMsg, boolean isCanResend,
                        com.xinbida.wukongim.WKIMApplication.SessionContext context) {
        this(sendCount, wkSendMsg, isCanResend);
        this.uploadSessionContext = context;
    }
}
