package com.xinbida.wukongim.entity;

import java.util.List;

/**
 * 2020-10-09 14:59
 * 最近会话
 */
public class WKSyncConvMsg {
    public String channel_id;
    public byte channel_type;
    public String last_client_msg_no;
    public long last_msg_seq;
    public int offset_msg_seq;
    public long timestamp;
    public int unread;
    public long version;
    public List<WKSyncRecent> recents;
    /**
     * YUJ-326 · server 回填的 Space 归属。dmworkim develop @759dd507 已 ship
     * ({@code modules/message/api_conversation.go:1223} · SyncUserConversationResp.SpaceID +
     * {@code :1315} spacepkg.ParseChannelID 兜底)，客户端直接拿来写入本地
     * {@code conversation.space_id} 列，作为"切回已访问 Space 不清 DB"的判据。
     *
     * <p>老 server 或兼容路径下该字段可能为空 —— {@link
     * com.xinbida.wukongim.manager.ConversationManager#saveSyncChat} 写入时会
     * 回落到请求头 {@code X-Space-ID} 对应的 currentSpaceId（本次 sync 结果必定属于
     * 本次请求 Space，见 Phase 3a 方案文档 §5）。
     */
    public String space_id;
}
