package com.chat.uikit.enity;

import com.chat.uikit.group.service.entity.GroupMember;

/**
 * 2019-11-20 10:13
 * 用户信息
 */
public class UserInfo {
    public String uid;
    public String name;
    public String username;
    public int mute;
    public int top;
    public int sex;
    public String category;
    public String short_no;
    public int chat_pwd_on;
    public int screenshot;
    public int revoke_remind;
    public int receipt;
    public int online;
    public int last_offline;
    public int follow;
    public String vercode;
    public String source_desc;
    public String remark;
    public int is_upload_avatar;
    public int status;
    public long version;
    public int is_deleted;
    public int robot;
    public String bot_description;
    public String bot_creator_name;
    // YUJ-238 (对齐 web PR#1092 BotDetailModal)：后端 /users/{uid} 顶层回传
    // 的 bot 创建者 uid；Android 用它在 UserDetailActivity 判定当前登录者
    // 是否为该 bot 的 owner，从而决定是否渲染「编辑头像 / 编辑简介」入口。
    public String bot_creator_uid;
    public String bot_commands;  // JSON: [{"cmd":"xxx","remark":"xxx"}]
    public int be_deleted;
    public int be_blacklist;
    public String updated_at;
    public String created_at;
    public String join_group_invite_uid;
    public String join_group_invite_name;
    public String join_group_time;
    public GroupMember group_member;

    // 外部成员 viewer-relative 标识字段。
    // - YUJ-146-2（对齐 web YUJ-144）：后端在 /users/{uid} 顶层返回 home_space_id
    //   和 legacy is_external，用于 UserInfo 面板隐藏「解除好友 / 拉黑」按钮。
    // - YUJ-155（对齐 Web YUJ-138 PR#1088 & @Mention 候选菜单 YUJ-134）：
    //   `/user/search` 返回的 UserInfo 也携带这些可选字段，搜索 adapter
    //   由此判定「对当前 viewer 而言，这个人是不是外部成员？」并在昵称后
    //   拼 " @SpaceName" 灰紫色后缀。降级兼容旧后端：缺 home_space_id 时
    //   回落到 is_external + source_space_name。
    public String home_space_id;
    public String home_space_name;
    public int is_external;
    public String source_space_name;
}
