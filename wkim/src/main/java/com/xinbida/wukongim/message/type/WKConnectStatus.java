package com.xinbida.wukongim.message.type;

/**
 * 2019-11-11 10:10
 * socket链接状态
 */
public class WKConnectStatus {
    //失败
    public static final int fail = 0;
    //登录或者发送消息回执返回状态成功
    public static final int success = 1;
    //被踢（其他设备登录）
    public static final int kicked = 2;
    //同步消息中
    public static final int syncMsg = 3;
    //连接中
    public static final int connecting = 4;
    //无网络
    public static final int noNetwork = 5;
    // 同步完成
    public static final int syncCompleted = 6;
}
