/*
 * Copyright (C) 2019 WuKongIM Team(Singsong).
 *
 * The idea is supported by the WuKong project.
 */
package com.chat.base.entity;

import java.util.List;

public class WKAPPConfig {
    public int rsa_public_key_ver;
    public String rsa_public_key;
    public int phone_search_off;
    public int register_invite_on;
    public int send_welcome_message_on;
    public int invite_system_account_join_group_on;
    public int channel_pinned_message_max_count;
    public int default_search_contact;
    public int register_channel_owner_id;
    public String register_channel_name;
    public int can_modify_api_url;
    public String app_name;
    public String welcome_message;
    public int revoke_second;
    public int offline_text_on;
    public String invite_channel_id;
    public int is_show_short_no;
    public int add_friend_with_verify_method;

    /**
     * 系统 bot UID 列表（YUJ-396 / GH dmwork-android#162）。
     *
     * <p>字段名与后端 JSON key {@code system_bot_uids} 严格对齐；Gson 反序列化会
     * 自动把 {@code null} 或缺字段当成 {@code null}，由消费侧做 fallback。
     * 参见 GH dmwork-android#162。
     */
    public List<String> system_bot_uids;

    /**
     * OIDC provider 列表（YUJ-396 / GH dmwork-web#1174）。
     *
     * <p>后端 {@code modules/common/api.go::oidcAccountURL()} 按环境下发的
     * Aegis 账户中心域名通过此数组的 {@link OidcProviderConfig#account_url} 字段暴露:
     * im-test → {@code accounts-test.imocto.cn}, im-prod → {@code accounts.example.com}。
     * 客户端从这里读 accountUrl, 不再硬编码 prod URL。
     *
     * <p>kian-dev 侧的 SSO 按钮消费 {@link OidcProviderConfig#authorize_path} + {@link OidcProviderConfig#name};
     * realname 模块消费 {@link OidcProviderConfig#account_url}。两边使用同一个强类型 model。
     *
     * <p>字段缺失 / Gson 反序列化 null → 调用侧视为「无可用 provider」, toast 兜底。
     */
    public List<OidcProviderConfig> oidc_providers;
}
