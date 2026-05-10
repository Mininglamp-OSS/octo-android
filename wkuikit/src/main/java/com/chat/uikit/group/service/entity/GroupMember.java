package com.chat.uikit.group.service.entity;

/**
 * 2020-07-20 21:42
 */
public class GroupMember {
    public int id;
    public String uid;
    public String name;
    public String username;
    public String remark;
    public String group_no;
    public int role;
    public int status;
    public int is_deleted;
    public int robot;
    public int version;
    public String created_at;
    public String updated_at;
    public String invite_uid;
    public long forbidden_expir_time;
    public String vercode;
    // 外部成员标识（来自其他 Space 的成员）
    public int is_external;
    public String source_space_id;
    public String source_space_name;
    // 成员 Home Space（YUJ-63 / YUJ-87，web #997 / dmworkim #1208）— viewer-relative 外部判定用。
    // 与 source_space_* 的语义区分：
    // - source_space_*: 成员通过哪个 Space 加入了当前群（可能为空，表示同 Space）。
    // - home_space_*:   成员真正归属的 Space（始终有值）。外部成员 home = source_space_id；
    //                   内部成员 home = group.space_id。
    // 前端用 home_space_id 与当前 viewer 的 Space 比较，决定是否渲染「@SpaceName」后缀。
    public String home_space_id;
    public String home_space_name;
    // YUJ-380 · 实名徽章 Phase A（方案 J v3 消费）：后端 group_members / users/{uid}
    // 的 group_member 节点返回的 realname_verified。true 时 UI 侧在昵称右侧渲染
    // 12dp 迷你蓝勾；不做未认证用户的负向标识。
    //
    // YUJ-395 P0-2：字段改为装箱 Boolean（非 primitive），区分三态：
    //   - Boolean.TRUE  → 后端显式 true（已实名）
    //   - Boolean.FALSE → 后端显式 false（已取消实名；必须写进 extraMap 覆盖
    //                     WKChannel.remoteExtraMap 里可能残留的 stale true，
    //                     否则 fallback 链会把勾错加回去）
    //   - null          → 后端没下发 key，应 fallback 到 channel 侧 profile
    // 上层调用 {@link com.chat.base.realname.RealnameBadgeResolver#isVerifiedTriState}
    // 读取时可直接区分「显式 false」vs「缺失」。
    public Boolean realname_verified;
}
