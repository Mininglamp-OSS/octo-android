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

    // octo-server PR #154 新增：会话同步响应里 resolved 的 Space 信息（GH dmwork-android#251）。
    //
    // 服务端 /v1/conversation/sync 在群类型会话上回填两个字段：
    //   space_id            — 群归属 Space 的真实 id（从 group 表查出，绕开 channel_id 裸 group_no
    //                         没有 s{spaceId}_ 前缀导致 ParseChannelID 永远返回 "" 的根因）
    //   my_source_space_id  — 当前用户在该群的 source_space_id（外部成员通过哪个 Space 加入）
    //
    // 老后端未部署时字段缺失（fastjson 反序列化结果为 null），SDK 解析后不会写入本地缓存，
    // 走原有 fail-open 分支保持向后兼容。
    public String space_id;
    public String my_source_space_id;
}
