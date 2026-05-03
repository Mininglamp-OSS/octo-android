package com.chat.base.entity;

import java.util.List;

public class WKAPPConfig {
    public int version;
    public String web_url;
    public int phone_search_off;
    public int shortno_edit_off;
    public int revoke_second;
    public int register_invite_on;
    public int send_welcome_message_on;
    public int invite_system_account_join_group_on;
    public int register_user_must_complete_info_on;
    public int can_modify_api_url;
    public int thread_on;

    /**
     * 系统 Bot UID 列表（YUJ-219-A3 / YUJ-219-B · §4.2 / §6.1 根因 F · 消除三端硬编码漂移）。
     *
     * <p>后端 {@code pkg/space/query.go :: SystemBots} 的权威来源，典型值为
     * {@code ["botfather", "u_10000", "fileHelper"]}（跨 Space 共享的 Bot / 系统账号）。
     * 冷启动尚未拉到 appconfig 时，客户端需走 fallback 默认值
     * （{@code SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS}）。
     *
     * <p>字段名与后端 JSON key {@code system_bot_uids} 严格对齐；Gson 反序列化会
     * 自动把 {@code null} 或缺字段当成 {@code null}，由消费侧做 fallback。
     * 参见 GH dmwork-android#162。
     */
    public List<String> system_bot_uids;
}
