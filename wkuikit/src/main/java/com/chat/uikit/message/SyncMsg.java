package com.chat.uikit.message;

import java.util.Map;

/**
 * 2020-07-21 09:41
 * 同步消息对象
 */
public class SyncMsg {
    public String message_id;
    public int message_seq;
    public String client_msg_no;
    public String from_uid;
    public String channel_id;
    public byte channel_type;
    public int voice_status;
    public long timestamp;
    public int is_delete;
    public int unread_count;
    public int readed_count;
    public long extra_version;
    public Map payload;
    public SyncMsgHeader header;
}
