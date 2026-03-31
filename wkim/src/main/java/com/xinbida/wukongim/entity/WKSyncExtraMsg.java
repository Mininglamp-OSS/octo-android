package com.xinbida.wukongim.entity;

import java.util.Map;

/**
 * 4/8/21 11:22 AM
 * 同步扩展消息
 */
public class WKSyncExtraMsg {
    public String message_id;
    public String message_id_str;
    public int revoke;
    public String revoker;
    public int voice_status;
    public int is_mutual_deleted;
    public int is_pinned;
    public long extra_version;
    public int unread_count;
    public int readed_count;
    public int readed;
    public Map content_edit;
    public long edited_at;
}
