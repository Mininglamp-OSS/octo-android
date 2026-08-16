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

package com.chat.base.act;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.chat.base.R;
import com.chat.base.app.WKAppModel;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKBinder;
import com.chat.base.config.WKConfig;
import com.chat.base.databinding.ActWebvieiwLayoutBinding;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.entity.AppInfo;
import com.chat.base.entity.AuthInfo;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.GlideUtils;
import com.chat.base.jsbrigde.CallBackFunction;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.space.SpaceFilter;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.BottomSheet;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKToastUtils;
import com.google.gson.JsonObject;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-21 13:25
 */

@SuppressLint("JavascriptInterface")
public class WKWebViewActivity extends WKBaseActivity<ActWebvieiwLayoutBinding> {
    TextView titleTv;
    private final int FILE_CHOOSER_RESULT_CODE = 101;
    ValueCallback<Uri> mUploadMessage;
    ValueCallback<Uri[]> mUploadCallbackAboveL;
    private String channelID;
    private byte channelType;

    @Override
    protected ActWebvieiwLayoutBinding getViewBinding() {
        return ActWebvieiwLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        this.titleTv = titleTv;
    }

    @Override
    protected void initPresenter() {
        if (getIntent().hasExtra("channelID"))
            channelID = getIntent().getStringExtra("channelID");
        if (getIntent().hasExtra("channelType"))
            channelType = getIntent().getByteExtra("channelType", (byte) 0);
    }

    @Override
    protected int getBackResourceID(ImageView backIv) {
        return R.mipmap.ic_close_white;
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_other;
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.copy_url), R.mipmap.search_links, () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", wkVBinding.webView.getUrl());
            assert cm != null;
            cm.setPrimaryClip(mClipData);
            WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed));
        }));
        list.add(new PopupMenuItem(getString(R.string.forward), R.mipmap.msg_forward, () -> {
            WKTextContent textContent = new WKTextContent(wkVBinding.webView.getUrl());
            EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView, new ChooseChatMenu(new ChatChooseContacts(new ChatChooseContacts.IChoose() {
                @Override
                public void onResult(List<WKChannel> list) {
                    for (WKChannel channel : list) {
                        WKSendOptions options = new WKSendOptions();
                        options.setting.receipt = channel.receipt;
                        WKIM.getInstance().getMsgManager().sendWithOptions(textContent, channel,options);
                    }
                }
            }), textContent));
        }));

        list.add(new PopupMenuItem(getString(R.string.refresh), R.mipmap.tool_rotate, () -> {
            wkVBinding.webView.reload();
        }));
        list.add(new PopupMenuItem(getString(R.string.open_system_browser), R.mipmap.msg_openin, () -> {
            Uri uri = Uri.parse(wkVBinding.webView.getUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }));
        ImageView rightIV = findViewById(R.id.titleRightIv);
        WKDialogUtils.getInstance().showScreenPopup(rightIV, list);
    }

    @Override
    protected void initView() {
        initWebViewSetting();
        String url = getIntent().getStringExtra("url");
        if (TextUtils.isEmpty(url)) {
            WKToastUtils.getInstance().showToast(getString(R.string.nodata));
            finish();
            return;
        }
        // Prefer HTTPS and block unexpected schemes to avoid MITM/file injection
        if (!url.toLowerCase().startsWith("http")) {
            url = "https://" + url;
        } else if (url.toLowerCase().startsWith("http://")) {
            url = url.replaceFirst("(?i)^http://", "https://");
        }
//        wkVBinding.webView.loadUrl("file:///android_asset/web/report.html");
        if (url.equals(WKApiConfig.baseWebUrl + "report.html")) {
            String wk_theme_pref = Theme.getTheme();
            url = String.format("%s?uid=%s&token=%s&mode=%s", url, WKConfig.getInstance().getUid(), WKConfig.getInstance().getToken(), wk_theme_pref);
        }
        // .md 文件使用 Markdown 原生渲染
        String urlPath = Uri.parse(url).getPath();
        if (urlPath != null && urlPath.toLowerCase().endsWith(".md")) {
            Intent mdIntent = new Intent(this, WKMarkdownViewActivity.class);
            mdIntent.putExtra("url", url);
            startActivity(mdIntent);
            finish();
            return;
        }
        // .pdf 文件通过独立 PdfViewActivity 渲染
        if (urlPath != null && urlPath.toLowerCase().endsWith(".pdf")) {
            Intent pdfIntent = new Intent(this, WKPdfViewActivity.class);
            pdfIntent.putExtra("url", url);
            startActivity(pdfIntent);
            finish();
            return;
        }

        Log.e("加载的URL", url);
        loadUrlWithHandoff(url);
    }

    /**
     * 自家域名下的 URL：注入 App 端 token/uid/name 到 WebView localStorage，实现免登。
     * Web 端登录数据存 localStorage 的 keys（见 web 的 App.tsx save()）：
     *   token{sid}, uid{sid}, name{sid}, short_no{sid}, role{sid}, is_work{sid}, sex{sid}
     * sid 从 URL ?sid=xxx 参数或 sessionStorage 或 localStorage 取；不给的话 web 会随机生成
     * → 我们注入的 key 就命中不上。所以强制拼 ?sid=android 让 web 用固定 bucket。
     * <p>
     * 通过 loadDataWithBaseURL 加载一个 bootstrap 页（baseURL = 自家 origin，保证 localStorage
     * 写在正确的 origin），bootstrap 里 <script> 先 setItem 再 location.replace 真 URL。
     * 这比 WebViewClient.onPageStarted + evaluateJavascript 时序更可控（避免 web app 的 boot
     * 代码抢跑到 localStorage 读取前）。
     * <p>
     * 外链（非自家域名）不动，保持原 loadUrl。
     */
    private void loadUrlWithHandoff(String url) {
        String webOrigin = DocsViewerUrlPolicy.originOfConfiguredBases(
                WKApiConfig.baseUrl, WKApiConfig.baseWebUrl);
        if (webOrigin == null || !DocsViewerUrlPolicy.isTrustedOriginUrl(url, webOrigin)) {
            // 外链 / origin 解析失败：走原有 loadUrl，不注入任何 App 状态（防 token 泄露给第三方）。
            wkVBinding.webView.loadUrl(url);
            return;
        }
        boolean docsViewer = DocsViewerUrlPolicy.isTrustedViewerUrl(url, webOrigin);
        // Capture once for this launch, before any credential-dependent branch. Even an anonymous
        // Docs launch must clear stale pooled storage when the native selection is empty.
        String currentSpaceId = docsViewer ? SpaceFilter.getCurrentSpaceId() : null;

        // report.html already uses its URL-parameter handshake. A query value containing that text
        // must not bypass an otherwise valid Docs viewer handoff.
        if (!docsViewer && url.contains("report.html")) {
            wkVBinding.webView.loadUrl(url);
            return;
        }
        String token = WKConfig.getInstance().getToken();
        String uid = WKConfig.getInstance().getUid();
        String name = WKConfig.getInstance().getUserInfo() != null ? WKConfig.getInstance().getUserInfo().name : "";
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(uid)) {
            if (docsViewer) {
                String bootstrap = buildDocsViewerSpaceBootstrapHtml(url, currentSpaceId);
                wkVBinding.webView.loadDataWithBaseURL(
                        webOrigin + "/", bootstrap, "text/html", "UTF-8", null);
            } else {
                // 未登录或缺关键身份：不做身份注入，走原路径（web 端会自己跳登录）。
                wkVBinding.webView.loadUrl(url);
            }
            return;
        }
        String urlWithSid = appendSidParam(url, "android");
        // sid 必须从 effective URL 反读，而不是硬编码 "android" —— appendSidParam 遇到
        // 已有 ?sid=xxx 时会保留原值不覆盖。若这里仍用 "android" 写 localStorage，
        // URL 带 ?sid=web 时 web 端会读 tokenweb / uidweb / nameweb（都不存在），
        // 但我们只写了 tokenandroid → 免登失效。
        String effectiveSid = extractSid(urlWithSid, "android");
        // Other first-party pages keep the existing identity handoff without gaining or clearing
        // Docs authorization context.
        String bootstrap = buildHandoffBootstrapHtml(
                urlWithSid, effectiveSid, token, uid, name, currentSpaceId);
        // baseURL 用自家 origin：localStorage 写入的是这个 origin 的存储，跳转到 origin 内任意页面都能读到。
        wkVBinding.webView.loadDataWithBaseURL(webOrigin + "/", bootstrap, "text/html", "UTF-8", null);
    }

    /**
     * URL 已有 ?sid=xxx 时保留原值；否则追加 ?sid={sid}。保留其它已有 query 参数（如 ?sp=xxx）。
     */
    private static String appendSidParam(String url, String sid) {
        try {
            Uri parsed = Uri.parse(url);
            if (!TextUtils.isEmpty(parsed.getQueryParameter("sid"))) {
                return url;
            }
            return parsed.buildUpon().appendQueryParameter("sid", sid).build().toString();
        } catch (Exception ignored) {
            return url;
        }
    }

    /**
     * 从 URL 的 {@code ?sid=xxx} 提取实际生效的 sid，缺失或解析失败回退到 {@code fallback}。
     * bootstrap 必须用此值写 localStorage，才能与 web 端 App.tsx 从 URL 读的 sid 对齐。
     */
    @VisibleForTesting
    static String extractSid(String url, String fallback) {
        try {
            String sid = Uri.parse(url).getQueryParameter("sid");
            return TextUtils.isEmpty(sid) ? fallback : sid;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 生成 bootstrap HTML：内联 script 里 localStorage.setItem 全 web 端约定的 keys（带 {sid} 后缀），
     * 然后 window.location.replace 到真 URL。所有 JS 值走 JSON.stringify 转义，避免 token 含特殊字符时 XSS。
     * <p>
     * {@code sid} 必须与 realUrl 内 {@code ?sid=xxx} 参数一致 —— web 端 App.tsx 从 URL 读 sid 后
     * 到 localStorage 找 {@code token{sid}} 等 key，两侧不一致就免登失效。见调用点的
     * {@link #extractSid(String, String)}。
     * <p>
     * Web 端 App.tsx save() 的完整 key 集合：app_id / short_no / uid / token / name / role /
     * is_work / sex / login_provider / realname_verified / real_name / realname_verified_at。
     * 我们只有 uid / token / name（App 端存的字段），其它由 web 自己后续 API 拉取补齐。
     * <p>
     * 刻意不加 console.log：uid / name / token 前缀等身份信息不应写进 WebView console
     * （release 里 remote debugging 通常关但仍是不良实践）。boot 失败时依赖 web 自身
     * 的登录跳转来暴露问题，不靠自打日志。
     */
    @VisibleForTesting
    static String buildHandoffBootstrapHtml(String realUrl, String sid, String token, String uid,
                                            String name, @Nullable String currentSpaceId) {
        return DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                realUrl, sid, token, uid, name, currentSpaceId);
    }

    @VisibleForTesting
    static String buildDocsViewerSpaceBootstrapHtml(String realUrl, @Nullable String currentSpaceId) {
        return DocsViewerUrlPolicy.buildSpaceBootstrapHtml(realUrl, currentSpaceId);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebViewSetting() {
        WebSettings webSettings = wkVBinding.webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // 设置支持javascript脚本
        webSettings.setUseWideViewPort(true);
        webSettings.setPluginState(WebSettings.PluginState.ON);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDefaultTextEncodingName("UTF-8");
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
//        webSettings.setAppCacheEnabled(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false); // 禁止保存表单
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false); // 防止本地文件被任意读取
//        webSettings.setAppCacheMaxSize(1024 * 1024 * 8);
        //webSettings.setAllowFileAccess(true);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (WKBinder.isDebug && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        //支持屏幕缩放
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        wkVBinding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // wkVBinding.webView.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
    }

    @Override
    protected boolean supportSlideBack() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (wkVBinding.webView.canGoBack()) {
                wkVBinding.webView.goBack();
                return true;
            } else return super.onKeyDown(keyCode, event);
        } else
            return super.onKeyDown(keyCode, event);
    }


//    @Override
//    protected void backListener(int type) {
//        // super.backListener(type);
//        if (wkVBinding.webView.canGoBack()) {
//            wkVBinding.webView.goBack();
//        } else {
//            super.onBackPressed();
//        }
//    }

    @Override
    protected void initListener() {
        wkVBinding.webView.registerHandler("quit", (var1, var2) -> {
            finish();
        });
        wkVBinding.webView.registerHandler("auth", (data, function) -> {
            if (!TextUtils.isEmpty(data)) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String appId = jsonObject.optString("app_id");
                    if (!TextUtils.isEmpty(appId)) {
                        getAppInfo(appId, function);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

            }
            Log.e("需要授权的信息", data);
        });
        wkVBinding.webView.registerHandler("getChannel", (data, function) -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("channelID", channelID);
            jsonObject.addProperty("channelType", channelType);
            function.onCallBack(jsonObject.toString());
        });
        wkVBinding.webView.registerHandler("showConversation", (data, function) -> {
            if (!TextUtils.isEmpty(data)) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String channelID = jsonObject.optString("channel_id");
                    byte channelType = (byte) jsonObject.optInt("channel_type");
                    EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(WKWebViewActivity.this, channelID, channelType, 0, true));
                    finish();
                } catch (JSONException e) {
                    WKLogUtils.e("显示最近会话页面错误");
                }
            }
        });

        wkVBinding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView webView, String s) {
                super.onReceivedTitle(webView, s);
                if (!TextUtils.isEmpty(s) && !"about:blank".equals(s)) {
                    titleTv.setText(s);
                }
            }

            @Override
            public void onProgressChanged(WebView webView, int i) {
                super.onProgressChanged(webView, i);
                if (i > 99) {
                    wkVBinding.progress.setVisibility(View.GONE);
//                    hideLoadingDialog();
                } else {
                    wkVBinding.progress.setVisibility(View.VISIBLE);
                    wkVBinding.progress.setProgress(i);
                }
            }

//            @Override
//            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
//                mUploadMessage = uploadMsg;
//                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
//                i.addCategory(Intent.CATEGORY_OPENABLE);
//                i.setType("*/*");
//                WKWebViewActivity.this.startActivityForResult(Intent.createChooser(i, "File Browser"), FILE_CHOOSER_RESULT_CODE);
//            }

            // For Android 5.0+
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {

                mUploadCallbackAboveL = filePathCallback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(
                        Intent.createChooser(i, "File Browser"),
                        FILE_CHOOSER_RESULT_CODE);
                return true;
            }

        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (null == mUploadMessage && null == mUploadCallbackAboveL) return;
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            if (mUploadCallbackAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(result);
                mUploadMessage = null;
            }
        }

    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE
                || mUploadCallbackAboveL == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
            } else {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null)
                    results = new Uri[]{Uri.parse(dataString)};
            }
        }
        mUploadCallbackAboveL.onReceiveValue(results);
        mUploadCallbackAboveL = null;
    }


    @SuppressLint("NewApi")
    @Override
    protected void onPause() {
        wkVBinding.webView.onPause();
        super.onPause();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onResume() {
        wkVBinding.webView.onResume();
        super.onResume();
    }

    private void getAppInfo(String appId, CallBackFunction function) {
        WKAppModel.Companion.getInstance().getAppInfo(appId, (code, msg, appInfo) -> {
            if (code == HttpResponseCode.success) {
                authDialog(appInfo, function);
            } else {
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
            }
        });
    }

    private void authDialog(AppInfo appInfo, CallBackFunction function) {
        View authView = LayoutInflater.from(this).inflate(R.layout.auth_dialog_layout, getViewBinding().webView, false);
        TextView appName = authView.findViewById(R.id.appNameTv);
        AvatarView appIV = authView.findViewById(R.id.appIV);
        TextView nameTv = authView.findViewById(R.id.nameTv);
        TextView descTv = authView.findViewById(R.id.descTv);
        AvatarView avatarView = authView.findViewById(R.id.avatarView);
        descTv.setText(String.format(getString(R.string.str_request_desc), getString(R.string.app_name)));
        appIV.setSize(30f);
        appName.setText(appInfo.getApp_name());
        GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(appInfo.getApp_logo()), appIV.imageView);
        avatarView.setSize(40f);
        WKChannel loginChannel = WKIM.getInstance().getChannelManager().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        avatarView.showAvatar(loginChannel);
        nameTv.setText(loginChannel.channelName);
        BottomSheet bottomSheet = new BottomSheet(this, true);
        bottomSheet.setCustomView(authView);
        authView.findViewById(R.id.cancelBtn).setOnClickListener(v -> {
            bottomSheet.setDelegate(null);
            bottomSheet.dismiss();
        });
        authView.findViewById(R.id.sureBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WKAppModel.Companion.getInstance().getAuthCode(appInfo.getApp_id(), new WKAppModel.IAuth() {
                    @Override
                    public void onResult(int code, @Nullable String msg, @Nullable AuthInfo authInfo) {
                        if (authInfo != null) {
                            JSONObject json = new JSONObject();
                            try {
                                json.put("code", authInfo.getAuthcode());
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            function.onCallBack(json.toString());
                            bottomSheet.setDelegate(null);
                            bottomSheet.dismiss();
                        }
                    }
                });
            }
        });
        bottomSheet.setOpenNoDelay(false);
        bottomSheet.setDelegate(new BottomSheet.BottomSheetDelegateInterface() {

            @Override
            public void onOpenAnimationStart() {

            }

            @Override
            public void onOpenAnimationEnd() {

            }

            @Override
            public boolean canDismiss() {
                return false;
            }
        });
        bottomSheet.show();
    }
}
