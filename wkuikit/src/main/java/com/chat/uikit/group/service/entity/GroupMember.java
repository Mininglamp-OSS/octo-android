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
    // 成员 Home Space（YUJ-63，web #997）— viewer-relative 外部判定用，
    // 与 source_space_* 区分：home_space_* 是成员真正归属的 Space，
    // 与 viewer 所在 Space 比较即可判断是否外部。
    public String home_space_id;
    public String home_space_name;
}
