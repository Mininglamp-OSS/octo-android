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

package com.chat.base.realname;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.entity.OidcProviderConfig;
import com.chat.base.entity.WKAPPConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 *  / GH  — Aegis「去认证」入口 URL 解析器。
 *
 * <p>把 Aegis verification URL 的拼接逻辑从 {@code SettingActivity} 里抽出来做成
 * 纯函数, 便于 JVM 单测锁合约。与 Web 端
 * {@code web frontend realnameVerifyUrl.ts} /
 * iOS 端 {@code WKRealnameVerifyManager#buildVerifyURLFromAccountUrl:} 口径对齐。
 *
 * <p><b>行为合约（{@link #resolve(WKAPPConfig)}）</b>
 * <ul>
 *     <li>appconfig 里 {@code oidc_providers} 有 entry 且 {@code account_url} 非空
 *         且为 https → {@link Result#ok(String, String)}, url 为
 *         {@code <accountUrl>/profile/info?anchor=verification}, host 为 accountUrl 的 host</li>
 *     <li>{@code oidc_providers} 为 null / 空, 或没有 entry 的 {@code account_url}
 *         通过 https 校验 → {@link Result#noAccountUrl()}（toast 不跳转）</li>
 *     <li>{@code WKAPPConfig} 本身 null（冷启动尚未拉到）→ {@link Result#noAccountUrl()}</li>
 * </ul>
 *
 * <p><b>安全守卫</b>
 * <ul>
 *     <li>只接受 https scheme（Aegis 账户页涉及密码 / OIDC token, 必须 TLS;
 *         与 iOS 端 {@code sanitizeHttpsURL:} 口径一致, 比 Web 端严）</li>
 *     <li>host 必须非空, 防 {@code https://} 无 host URL 误判通过</li>
 *     <li>accountUrl 末尾斜杠去重, 防 {@code //profile/...} 协议相对 URL 泄漏</li>
 * </ul>
 *
 * <p><b>provider 选择策略</b>：Android 当前不跟踪 loginProvider（OIDC 登录流尚未接入）,
 * 选第一个 {@code account_url} 合法的 provider。生产部署 oidc_providers 通常只 1 条,
 * 一致。未来若 Android 接入 OIDC 登录, 这里改为按登录时的 provider id 精确匹配。
 */
public final class AegisVerifyUrlResolver {

    /** Aegis 账户页实名认证锚点路径。与 Web / iOS 固定一致。 */
    static final String VERIFICATION_ANCHOR_PATH = "/profile/info?anchor=verification";

    private AegisVerifyUrlResolver() {
        // static-only
    }

    /** 解析结果, 带明确 reason 便于调用侧 log / toast 分支。 */
    public static final class Result {
        public enum Reason {
            /** 成功拼出了 URL, {@link #url} / {@link #host} 可用。 */
            OK,
            /** appconfig 里没有任何可用的 accountUrl。调用侧应 toast 不跳转。 */
            NO_ACCOUNT_URL
        }

        @NonNull
        public final Reason reason;
        @Nullable
        public final String url;
        @Nullable
        public final String host;

        private Result(@NonNull Reason reason, @Nullable String url, @Nullable String host) {
            this.reason = reason;
            this.url = url;
            this.host = host;
        }

        public boolean isOk() {
            return reason == Reason.OK;
        }

        static Result ok(@NonNull String url, @NonNull String host) {
            return new Result(Reason.OK, url, host);
        }

        static Result noAccountUrl() {
            return new Result(Reason.NO_ACCOUNT_URL, null, null);
        }
    }

    /**
     * 从 appconfig 里挑第一个 account_url 合法的 provider, 拼 Aegis 实名认证 URL。
     * @param appConfig 允许 null（冷启动未拉到）; null → {@link Result#noAccountUrl()}
     */
    @NonNull
    public static Result resolve(@Nullable WKAPPConfig appConfig) {
        if (appConfig == null) {
            return Result.noAccountUrl();
        }
        List<OidcProviderConfig> providers = appConfig.oidc_providers;
        if (providers == null || providers.isEmpty()) {
            return Result.noAccountUrl();
        }
        for (OidcProviderConfig p : providers) {
            if (p == null) continue;
            Result r = buildFromAccountUrl(p.account_url);
            if (r.isOk()) {
                return r;
            }
        }
        return Result.noAccountUrl();
    }

    /**
     * 由单条 accountUrl 拼实名认证 URL 并做安全守卫。accountUrl 非法 → noAccountUrl。
     * 包级可见供单测直接覆盖每种分支, 不通过 {@link WKAPPConfig} 包装。
     */
    @NonNull
    static Result buildFromAccountUrl(@Nullable String accountUrl) {
        if (accountUrl == null || accountUrl.length() == 0) {
            return Result.noAccountUrl();
        }
        // 剥末尾斜杠, 防 //profile/... 协议相对 URL
        int end = accountUrl.length();
        while (end > 0 && accountUrl.charAt(end - 1) == '/') end--;
        if (end == 0) {
            return Result.noAccountUrl();
        }
        String base = accountUrl.substring(0, end);

        // 用 java.net.URI 做 scheme + host 校验 —— 纯 JVM 不依赖 Android framework,
        // JVM 单测（无 Robolectric）可以直接跑。CustomTabs launchUrl 走 Uri.parse,
        // 两边行为在合法 https URL 上一致; 非法 URL 这里先拒掉根本到不了 launchUrl。
        URI uri;
        try {
            uri = new URI(base);
        } catch (URISyntaxException e) {
            return Result.noAccountUrl();
        }
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            return Result.noAccountUrl();
        }
        String host = uri.getHost();
        if (host == null || host.length() == 0) {
            return Result.noAccountUrl();
        }
        return Result.ok(base + VERIFICATION_ANCHOR_PATH, host.toLowerCase());
    }
}
