package com.chat.base.entity;

import android.text.TextUtils;

/**
 * 2020-06-30 16:41
 * 用户信息
 */
public class UserInfoEntity {
    public String token;
    public String uid;
    public String username;
    public String name;
    public String im_token;
    public String short_no;//显示的id号
    public int short_status;//是否已经设置ID
    public int sex;
    public String zone;//区号
    public String phone;//手机号
    public String avatar;
    public int server_id;
    public String chat_pwd;//聊天密码
    public String lock_screen_pwd;//锁屏密码
    public int lock_after_minute;
    public String rsa_public_key;
    public int msg_expire_second;
    public UserInfoSetting setting;

    // YUJ-361 (#227) · OCTO 实名认证 v3 — displayName 合并 + 资料页勾
    // 方案 J v3：实名态下全局展示 realname，未认证态继续用 nickname（name）。
    // 字段对齐 verify-service README 与 dmworkim PR#1301 的 `users/{uid}` 回包：
    //   realname_verified      : boolean — 是否已通过 CAS 实名
    //   realname               : string  — 权威真名（来自 CAS/企微/飞书）
    //   realname_verified_at   : string  — ISO-8601；设置页渲染「已认证 · YYYY-MM」
    public boolean realname_verified;
    public String realname;
    public String realname_verified_at;

    /**
     * YUJ-361：displayName 合并策略 —— 实名态下全局用真名，未认证态回落到昵称。
     * 这是唯一的「展示名」出口；UI 代码一律读它，不要再读 {@link #name}。
     */
    public String getDisplayName() {
        if (realname_verified && !TextUtils.isEmpty(realname)) {
            return realname;
        }
        return name;
    }
}
