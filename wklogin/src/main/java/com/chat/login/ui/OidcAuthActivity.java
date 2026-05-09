package com.chat.login.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
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

import java.util.List;

public class OidcAuthActivity extends WKBaseActivity<ActOidcAuthLayoutBinding> {

    private String authcode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

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
        String authorizeUrl = getIntent().getStringExtra("authorize_url");
        if (TextUtils.isEmpty(authcode) || TextUtils.isEmpty(authorizeUrl)) {
            finish();
            return;
        }
        setupWebView();
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
        WebSettings settings = wkVBinding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wkVBinding.webView, true);

        wkVBinding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                wkVBinding.progressBar.setVisibility(View.GONE);
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
        LoginModel.getInstance().pollOidcAuthStatus(authcode, new LoginModel.IOidcAuthStatusCallback() {
            @Override
            public void onResult(int status, UserInfoEntity userInfo) {
                if (status == 1 && userInfo != null) {
                    polling = false;
                    onLoginSuccess(userInfo);
                } else {
                    handler.postDelayed(() -> pollOnce(), 1000);
                }
            }

            @Override
            public void onError(int code, String msg) {
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
        String baseUrl;
        if (authorizePath.startsWith("http://") || authorizePath.startsWith("https://")) {
            baseUrl = authorizePath;
        } else if (authorizePath.startsWith("/")) {
            String apiUrl = WKApiConfig.baseUrl;
            try {
                java.net.URL url = new java.net.URL(apiUrl);
                baseUrl = url.getProtocol() + "://" + url.getHost()
                        + (url.getPort() > 0 ? ":" + url.getPort() : "")
                        + authorizePath;
            } catch (Exception e) {
                baseUrl = apiUrl + authorizePath;
            }
        } else {
            baseUrl = WKApiConfig.baseUrl + authorizePath;
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + "authcode=" + authcode
                + "&flag=0"
                + "&device_id=" + WKConstants.getDeviceID()
                + "&device_name=" + android.net.Uri.encode(WKDeviceUtils.getInstance().getDeviceName())
                + "&device_model=" + android.net.Uri.encode(WKDeviceUtils.getInstance().getSystemModel());
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
