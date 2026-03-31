package com.xinbida.wukongim.message.type;

/**
 * @version 1.0
 * 2019-7-29 下午2:35:15
 * 发送消息返回结果
 */
public class WKSendMsgResult {
    //不在白名单内
    public static final int not_on_white_list = 13;
    //黑名单
    public static final int black_list = 4;
    //不是好友或不在群内
    public static final int no_relation = 3;
    //发送失败
    public static final int send_fail = 2;
    //成功
    public static final int send_success = 1;
    //发送中
    public static final int send_loading = 0;
}
