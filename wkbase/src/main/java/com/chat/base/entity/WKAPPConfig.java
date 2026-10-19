/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.entity;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
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
     *  I02a：名片手机号/邮箱部署级总开关（appconfig `profile_contact_info_on`）。
     * 默认 false = 关闭。取不到配置 / 无该项 / 请求失败一律按关闭 —— Gson 反序列化
     * 缺字段时此 boolean 保持 false，WKConfig.getAppConfig() 在无缓存时 new WKAPPConfig()
     * 也是 false，两条兜底路径都落到关闭。
     */
    @JsonAdapter(LenientBooleanAdapter.class)
    public boolean profile_contact_info_on;

    /**
     * 系统 Bot UID 列表（-A3 / -B · §4.2 / §6.1 根因 F · 消除三端硬编码漂移）。
     *
     * <p>后端 {@code pkg/space/query.go :: SystemBots} 的权威来源，典型值为
     * {@code ["botfather", "u_10000", "fileHelper"]}（跨 Space 共享的 Bot / 系统账号）。
     * 冷启动尚未拉到 appconfig 时，客户端需走 fallback 默认值
     * （{@code SystemBotsFallback.DEFAULT_SYSTEM_BOT_IDS}）。
     *
     * <p>字段名与后端 JSON key {@code system_bot_uids} 严格对齐；Gson 反序列化会
     * 自动把 {@code null} 或缺字段当成 {@code null}，由消费侧做 fallback。
     * 参见 GH 。
     */
    public List<String> system_bot_uids;

    /**
     * OIDC provider 列表（ / GH ）。
     *
     * <p>后端 {@code modules/common/api.go::oidcAccountURL()} 按环境下发的
     * Aegis 账户中心域名通过此数组的 {@link OidcProviderConfig#account_url} 字段暴露:
     * im-test → {@code accounts-test.example.com}, im-prod → {@code accounts.example.com}。
     * 客户端从这里读 accountUrl, 不再硬编码 prod URL。
     *
     * <p>字段缺失 / Gson 反序列化 null → 调用侧视为「无可用 provider」, toast 兜底。
     */
    public List<OidcProviderConfig> oidc_providers;

    public boolean login_enable = true;
    public boolean register_enable = true;

    /**
     *  I02a：宽容 boolean 适配器。服务端该字段实测下发 JSON boolean（历史曾为 int），
     * 任何异常形态（number/string/null/object/array）一律按关闭解析，且绝不抛异常，
     * 避免 JsonSyntaxException 炸掉整个 WKAPPConfig 反序列化。
     */
    public static final class LenientBooleanAdapter extends TypeAdapter<Boolean> {
        @Override
        public void write(JsonWriter out, Boolean value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value);
        }

        @Override
        public Boolean read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            switch (token) {
                case BOOLEAN:
                    return in.nextBoolean();
                case NUMBER:
                    return in.nextInt() == 1;
                case STRING: {
                    String s = in.nextString();
                    return "1".equals(s) || "true".equalsIgnoreCase(s);
                }
                case NULL:
                    in.nextNull();
                    return false;
                default:
                    in.skipValue();
                    return false;
            }
        }
    }
}
