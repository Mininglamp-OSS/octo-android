package com.xinbida.wukongim.entity;

import java.util.List;
import java.util.Map;

/**
 * 2020-10-09 15:11
 * 最近消息
 */
public class WKSyncRecent {
    public String message_id;
    public int message_seq;
    public String client_msg_no;
    public String from_uid;
    public String channel_id;
    public byte channel_type;
    public long timestamp;
    public int voice_status;
    public int is_deleted;
    public int revoke;
    public String revoker;
    public long extra_version;
    public int unread_count;
    public int readed_count;
    public int readed;
    public int receipt;
    public int setting;
    public int expire;
    public Map payload;
    public String signal_payload;
    public List<WKSyncMsgReaction> reactions;
    public WKSyncExtraMsg message_extra;

    // 外部群来源字段（YUJ-183 诊断 + 修复 / 对齐 wkuikit SyncMsg + web PR #981/#982/#997）。
    //
    // /conversation/sync 返回的 recents 数组每条 JSON 对象在外部群场景下会带下列字段，
    // 客户端用它们渲染 "@SpaceName" 后缀（ExternalSourceResolver）。之前 DTO 缺字段
    // 被 retrofit/fastjson 在反序列化阶段静默丢弃，WKMsg.localExtraMap 永远是空。
    //
    // wire 约定（与 SyncMsg 完全一致，无 from_ 前缀）：
    //   is_external (Integer, 0/1/缺失)
    //   source_space_id / source_space_name (外部成员通过哪个 Space 加入 group)
    //   home_space_id   / home_space_name   (成员归属的 Space，viewer-relative 外部判定用)
    public Integer is_external;
    public String source_space_id;
    public String source_space_name;
    public String home_space_id;
    public String home_space_name;
}
