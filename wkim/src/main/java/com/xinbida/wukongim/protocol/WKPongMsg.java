package com.xinbida.wukongim.protocol;


import com.xinbida.wukongim.message.type.WKMsgType;

/**
 * 2019-11-11 10:49
 * 对ping请求的响应
 */
public class WKPongMsg extends WKBaseMsg {
    public WKPongMsg() {
        packetType = WKMsgType.PONG;
    }
}
