package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;

/**
 * 2019-11-10 17:03
 * 接受通讯协议消息
 */
public interface IReceivedMsgListener {
    /**
     * 登录状态消息
     *
     * @param connectAckMsg 状态
     */
    void loginStatusMsg(WKConnectAckMsg connectAckMsg);

    /**
     * 心跳消息
     */
    void pongMsg(WKPongMsg pongMsg);

    /**
     * 被踢消息
     */
    void kickMsg(WKDisconnectMsg disconnectMsg);

    /**
     * 发送消息状态消息
     *
     * @param sendAckMsg ack
     */
    void sendAckMsg(WKSendAckMsg sendAckMsg);

    /**
     * 重连
     */
    void reconnect();
}
