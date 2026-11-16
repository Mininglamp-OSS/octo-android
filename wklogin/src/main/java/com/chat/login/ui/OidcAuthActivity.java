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

package com.chat.login.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKReader;
import com.chat.login.R;
import com.chat.login.databinding.ActOidcAuthLayoutBinding;
import com.chat.login.service.LoginModel;
import com.chat.login.service.OidcUrlPolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OidcAuthActivity extends WKBaseActivity<ActOidcAuthLayoutBinding> {

    private String authcode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;
    private String migrationUid;
    private boolean automatic;
    private final Runnable automaticTimeout = this::abortAutomaticLogin;

    private void abortAutomaticLogin() {
        if (!automatic || !polling) return;
        polling = false;
        handler.removeCallbacksAndMessages(null);
        wkVBinding.webView.stopLoading();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected ActOidcAuthLayoutBinding getViewBinding() {
        return ActOidcAuthLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.sso_auth_title);
    }

    @Override
    protected void initPresenter() {
        authcode = getIntent().getStringExtra("authcode");
        migrationUid = getIntent().getStringExtra("migration_uid");
        automatic = !TextUtils.isEmpty(migrationUid);
        String authorizeUrl = getIntent().getStringExtra("authorize_url");
        if (TextUtils.isEmpty(authcode) || !OidcUrlPolicy.isHttpsUrl(authorizeUrl)
                || !OidcUrlPolicy.isHttpsUrl(WKApiConfig.baseUrl)) {
            finish();
            return;
        }
        setupWebView();
        if (automatic) {
            wkVBinding.webView.setVisibility(View.INVISIBLE);
            wkVBinding.progressBar.setVisibility(View.GONE);
            handler.postDelayed(automaticTimeout, 8000);
        }
        wkVBinding.webView.loadUrl(authorizeUrl);
        startPolling();
    }

    @Override
    protected void initView() {
    }

    @Override
    protected void initListener() {
    }

    private void setupWebView() {
        // 从 Intent 取 authorize URL, 用于进入 setupWebView 内部设置 URL 白名单 host
        String authorizeUrl = getIntent().getStringExtra("authorize_url");
        final String authHostResolved =
                authorizeUrl == null ? null : android.net.Uri.parse(authorizeUrl).getHost();
        WebSettings settings = wkVBinding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        //  R3 fix (review): WebView 安全硬化对齐 WKWebViewActivity 基线。
        // SSO 访问授权 token, 对安全要求比普通 WebView 更高。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 拒绝 HTTP 子资源在 HTTPS 授权页中加载, 避免 MITM 风险
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        settings.setAllowFileAccess(false);                  // 禁本地文件任意读
        settings.setAllowUniversalAccessFromFileURLs(false); // 禁 file:// 跨域
        settings.setAllowFileAccessFromFileURLs(false);      // 禁 file:// 互读
        settings.setSavePassword(false);                     // 不写入 Android 系统 autofill
        settings.setSaveFormData(false);                     // 不落盘表单数据

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wkVBinding.webView, true);

        // 记录授权 host 用于下面的 URL 白名单校验
        final String authHost = authHostResolved;

        // 产品允许首次未知 HTTPS 域名自动放行，以兼容不同部署的 SSO 跳转；仅追加一次。
        // 这是兼容策略，不是对目标域名身份的验证，也不代表已有完整的 IdP 白名单。
        // 已知授权/API host + 首次未知 HTTPS host，后续其它未知 host 仍拒绝。
        final Set<String> allowedHosts = new HashSet<>();
        final boolean[] idpDiscovered = {false};
        if (authHost != null) allowedHosts.add(authHost);
        try {
            String apiHost = new java.net.URL(WKApiConfig.baseUrl).getHost();
            if (apiHost != null) allowedHosts.add(apiHost);
        } catch (Exception ignore) { }

        wkVBinding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                wkVBinding.progressBar.setVisibility(View.GONE);
                if (automatic && "1".equals(android.net.Uri.parse(url).getQueryParameter("oidc_error"))) {
                    abortAutomaticLogin();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) abortAutomaticLogin();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) abortAutomaticLogin();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return rejectUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return rejectUrl(android.net.Uri.parse(url));
            }

            /**
             * OIDC 授权流程 URL 安全校验。
             *
             * 安全策略：
             * 1. 强制 HTTPS — 拒绝所有非 HTTPS 请求
             * 2. 静态白名单 — authHost + apiHost
             * 3. 按产品允许的兼容策略放行首次未知 HTTPS host；不将此视为 IdP 身份验证。
             *    追加一次后锁定，不再接受其它未知 host。
             */
            private boolean rejectUrl(android.net.Uri target) {
                if (target == null) return true;
                String scheme = target.getScheme();
                if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                    return true;
                }
                String host = target.getHost();
                if (host == null || host.isEmpty()) return true;
                if (allowedHosts.contains(host)) return false;
                if (!idpDiscovered[0]) {
                    idpDiscovered[0] = true;
                    allowedHosts.add(host);
                    return false;
                }
                return true;
            }
        });
        wkVBinding.webView.setWebChromeClient(new WebChromeClient());
    }

    private void startPolling() {
        polling = true;
        pollOnce();
    }

    private void pollOnce() {
        if (!polling || isFinishing()) return;
        LoginModel.getInstance().pollOidcAuthStatus(authcode, migrationUid,
                () -> polling && !isFinishing() && !isDestroyed(), new LoginModel.IOidcAuthStatusCallback() {
            @Override
            public void onResult(int status, UserInfoEntity userInfo) {
                if (status == 1 && userInfo != null) {
                    polling = false;
                    handler.removeCallbacks(automaticTimeout);
                    onLoginSuccess(userInfo);
                } else {
                    handler.postDelayed(() -> pollOnce(), 1000);
                }
            }

            @Override
            public void onError(int code, String msg) {
                if (automatic) {
                    abortAutomaticLogin();
                    return;
                }
                handler.postDelayed(() -> pollOnce(), 1000);
            }
        });
    }

    private void onLoginSuccess(UserInfoEntity userInfo) {
        if (TextUtils.isEmpty(userInfo.name)) {
            startActivity(new Intent(this, PerfectUserInfoActivity.class));
            finish();
        } else {
            handler.postDelayed(() -> {
                List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
                if (WKReader.isNotEmpty(list)) {
                    for (LoginMenu menu : list) {
                        if (menu.iMenuClick != null) menu.iMenuClick.onClick();
                    }
                }
            }, 200);
        }
    }

    public static String buildAuthorizeUrl(String authorizePath, String authcode) {
        //  R2 fix (review): 用 Uri.Builder + appendQueryParameter 统一编码
        // 避免 authcode / device_id / flag 等 query 参数手拼时的注入 / 日志泄露风险。
        // 同时限制生产 SSO 只接受 HTTPS。
        String baseUrl = OidcUrlPolicy.resolveAuthorizeUrl(WKApiConfig.baseUrl, authorizePath);

        // Uri.Builder 会处理所有 query 参数的 URL 编码 + 纯字器层叠加,
        // 避免手拼 "?k1=v1&k2=v2" 时的 injection / encoding bug。
        android.net.Uri.Builder builder = android.net.Uri.parse(baseUrl).buildUpon();
        builder.appendQueryParameter("authcode", authcode == null ? "" : authcode);
        String deviceFlag = String.valueOf(com.xinbida.wukongim.protocol.WKConnectMsg.CURRENT_DEVICE_FLAG);
        builder.appendQueryParameter("flag", deviceFlag);
        builder.appendQueryParameter("device_id", WKConstants.getDeviceID());
        builder.appendQueryParameter("device_name", WKDeviceUtils.getInstance().getDeviceName());
        builder.appendQueryParameter("device_model", WKDeviceUtils.getInstance().getSystemModel());
        String result = builder.build().toString();
        OidcUrlPolicy.requireHttps(result);
        return result;
    }

    @Override
    protected void onDestroy() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
        if (wkVBinding.webView != null) {
            wkVBinding.webView.stopLoading();
            wkVBinding.webView.destroy();
        }
        super.onDestroy();
    }
}
