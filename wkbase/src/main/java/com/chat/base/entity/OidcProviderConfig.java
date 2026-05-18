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

import com.google.gson.annotations.SerializedName;

/**
 *  / GH  — 后端 /v1/common/appconfig 返回的
 * oidc_providers[] 数组的 entry 模型。字段跟 
 * modules/common/api.go 的下发口径对齐:
 *
 * <pre>{@code
 * {
 *   "id": "oidc-provider",
 *   "name": "oidc-provider",
 *   "authorize_path": "/auth/oidc/provider/authorize",
 *   "account_url": "https://accounts-test.example.com",    // 按环境不同
 *   "reset_password_url": "https://accounts-test.example.com/.../reset"
 * }
 * }</pre>
 *
 * <p>{@link #account_url} 会因环境不同而不同（im-test → accounts-test.example.com;
 * im-prod → accounts.example.com）。客户端把 Aegis 账户页 / 实名认证入口
 * 的域名读点全部收敛到这个模型, 不再允许任何 hardcoded prod 域。
 *
 * <p>Gson 反序列化：字段名与后端 JSON key 严格对齐; 缺字段默认 null, 由消费侧做 fallback。
 */
public class OidcProviderConfig {

    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("authorize_path")
    public String authorize_path;

    /**
     * Aegis 账户中心 URL 基址（如 "https://accounts-test.example.com"）。按环境下发。
     * 非 https / 空 → 调用侧视为无可用 provider, toast 兜底不跳转。
     */
    @SerializedName("account_url")
    public String account_url;

    @SerializedName("reset_password_url")
    public String reset_password_url;
}
