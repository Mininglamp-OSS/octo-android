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

package com.chat.uikit.enity;

/**
 *  (#227) · OCTO 实名认证 —— {@code POST /v1/internal/verify-token} 响应。
 *
 * <p>客户端发起实名时走 verify-side-channel（方案 J v3）：App 先拿一次性 token，
 * 再用 {@code verify_url} 在 Custom Tabs 打开 CAS 验证页，CAS 完成后 redirect 回
 * {@code dmwork://verified}，客户端 refresh 个人资料即可看到 {@code realname_verified=true}。
 *
 * <p>所有字段均来自 PR#1301（后端实装）。客户端不解析 token 本身。
 */
public class VerifyTokenResponse {
    public String verify_url;
    public String token;
    public long expires_at;
}
