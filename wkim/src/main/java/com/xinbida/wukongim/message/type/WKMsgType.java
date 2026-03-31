package com.xinbida.wukongim.message.type;

/**
 * 2019-11-10 17:10
 * 通讯协议消息类型
 */
public class WKMsgType {
    //保留
    public static final short Reserved = 0;
    //客户端请求连接到服务器(c2s)
    public static final short CONNECT = 1;
    //服务端收到连接请求后确认的报文(s2c)
    public static final short CONNACK = 2;
    //发送消息(c2s)
    public static final short SEND = 3;
    //收到消息确认的报文(s2c)
    public static final short SENDACK = 4;
    //收取消息(s2c)
    public static final short RECEIVED = 5;
    //收取消息确认(c2s)
    public static final short REVACK = 6;
    //ping请求
    public static final short PING = 7;
    //对ping请求的相应
    public static final short PONG = 8;
    //请求断开连接
    public static final short DISCONNECT = 9;
}
