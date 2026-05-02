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
     * YUJ-219 · A3：后端下发的系统 Bot UID 白名单（跨 Space 共享的 Bot / 系统账号）。
     *
     * <p>典型值：{@code ["botfather", "u_10000", "fileHelper"]}。appconfig 未返回（null / 空）
     * 时由客户端 {@code SystemBotsFallback} 走本地 fallback。参见 GH dmwork-android#162。
     */
    public List<String> system_bot_uids;
}
