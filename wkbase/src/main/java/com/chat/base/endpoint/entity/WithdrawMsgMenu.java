package com.chat.base.endpoint.entity;

/**
 * 4/2/21 12:29 PM
 * 撤回消息
 */
public class WithdrawMsgMenu {
    public String message_id;
    public String channel_id;
    public String client_msg_no;
    public byte channel_type;

    public WithdrawMsgMenu(String message_id, String channel_id, String client_msg_no, byte channel_type) {
        this.message_id = message_id;
        this.channel_id = channel_id;
        this.client_msg_no = client_msg_no;
        this.channel_type = channel_type;
    }
}
