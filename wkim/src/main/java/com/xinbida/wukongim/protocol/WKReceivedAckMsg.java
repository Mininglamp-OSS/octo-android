package com.xinbida.wukongim.protocol;


import com.xinbida.wukongim.message.type.WKMsgType;

/**
 * 2019-11-11 10:46
 * 收到消息Ack消息
 */
public class WKReceivedAckMsg extends WKBaseMsg {
    //服务端的消息ID(全局唯一)
    public String messageID;
    //序列号
    public int messageSeq;
    public WKReceivedAckMsg() {
        packetType = WKMsgType.REVACK;
        remainingLength = 8;//序列号
    }
}
