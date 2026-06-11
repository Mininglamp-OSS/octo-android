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

package com.chat.uikit.setting;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.realname.AegisVerifyUrlResolver;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.AppExecutors;
import com.chat.base.utils.DataCleanManager;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActSettingLayoutBinding;

/**
 * 2020-03-22 21:11
 * 设置页面
 */
public class SettingActivity extends WKBaseActivity<ActSettingLayoutBinding> {

    /**
     *  · Aegis OIDC v3 Phase 2b：去认证按钮直跳 Aegis 账户页。
     *  / GH ：Aegis 域名改为按环境从后端 appconfig 下发的
     *   {@code oidc_providers[].account_url} 字段读, 不再硬编码 prod URL。
     *
     * <p>客户端打开 Aegis 的 verification anchor; Aegis 完成身份验证后会 302 回
     * {@code dmwork://verified} deeplink, 由 {@code VerifyLandingActivity}
     * 刷新本地 {@code user_verification} 状态后 finish。
     *
     * <p>URL 由 {@link AegisVerifyUrlResolver#resolve(com.chat.base.entity.WKAPPConfig)}
     * 从 appconfig 解析; 未下发可用 {@code account_url} 时弹 toast 不跳转,
     * 绝不回退任何硬编码 prod 域。合约见该类 Javadoc。
     *
     * <p>老版本 App（未升级到 Phase 2b）仍然依赖 端的 verify-token
     * 翻译层已处理, 后端会返回按环境下发的 Aegis URL。
     */
    private String str;

    @Override
    protected ActSettingLayoutBinding getViewBinding() {
        return ActSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.currency);
    }

    @Override
    protected void initPresenter() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
    }

    @Override
    protected void initView() {
        getCacheSize();
        renderRealnameStatus();
        updateLanguageDisplay();
        // 显示版本号
        String versionName = WKDeviceUtils.getInstance().getVersionName(this);
        int versionCode = 0;
        try {
            versionCode = (int) getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) {}
        wkVBinding.aboutVersionTv.setText("v" + versionName + " (" + versionCode + ")");
    }

    private void updateLanguageDisplay() {
        int langType = com.chat.base.utils.language.WKMultiLanguageUtil.getInstance().getLanguageType();
        String langName;
        switch (langType) {
            case com.chat.base.utils.language.WKLanguageType.LANGUAGE_EN:
                langName = "English";
                break;
            case com.chat.base.utils.language.WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED:
                langName = "简体中文";
                break;
            case com.chat.base.utils.language.WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL:
                langName = "繁體中文";
                break;
            default:
                // 跟随系统时显示当前实际语言
                String sysLang = getResources().getConfiguration().locale.getLanguage();
                langName = "zh".equals(sysLang) ? "简体中文" : "English";
                break;
        }
        wkVBinding.languageValueTv.setText(langName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //  (#227)：从 Custom Tabs 回来后 VerifyLandingActivity 会刷新 profile，
        // 这里 onResume 把最新状态重新渲染到行上。
        //  · Aegis Phase 2b：切到 Aegis 直跳后路径不变 —— deeplink 回跳
        // 依然触发 VerifyLandingActivity → refreshCurrentUser → 栈回到这里 onResume。
        renderRealnameStatus();
    }

    /**
     *  (#227)：实名认证行的状态渲染。
     * - 未认证：显示「去认证」，点击直跳 Aegis 账户页（ · Phase 2b）。
     * - 已认证：显示「已认证 · YYYY-MM」，隐藏箭头，不可点。
     */
    private void renderRealnameStatus() {
        UserInfoEntity me = WKConfig.getInstance().getUserInfo();
        if (me.realname_verified) {
            wkVBinding.realnameStatusTv.setText(getString(
                    R.string.realname_verified_at_fmt, formatVerifiedAt(me.realname_verified_at)));
            wkVBinding.realnameArrowIv.setVisibility(View.INVISIBLE);
            wkVBinding.realnameLayout.setClickable(false);
            wkVBinding.realnameLayout.setOnClickListener(null);
        } else {
            wkVBinding.realnameStatusTv.setText(R.string.realname_goto_verify);
            wkVBinding.realnameArrowIv.setVisibility(View.VISIBLE);
            wkVBinding.realnameLayout.setClickable(true);
            SingleClickUtil.onSingleClick(wkVBinding.realnameLayout, v -> startVerifyFlow());
        }
    }

    private static String formatVerifiedAt(String iso) {
        // 后端回 ISO-8601（如 2026-05-05T12:34:56Z）。只截年-月即可；
        // 空值或格式不符时回落到空串，避免 NPE。
        if (TextUtils.isEmpty(iso)) return "";
        if (iso.length() >= 7) return iso.substring(0, 7);
        return iso;
    }

    private void startVerifyFlow() {
        //  · Aegis OIDC v3 Phase 2b：去认证入口改为直跳 Aegis 账户页。
        //  · Aegis 域名改为按环境从后端 appconfig 下发的
        //   oidc_providers[].account_url 字段读, 不再硬编码 prod URL。
        //
        // 与 Phase 2a（走 `verify-token` 翻译接口）对比：
        // 1. 不再经过 OCTO 后端多一跳。
        // 2. 不再需要异步回调 / Activity 生命周期守卫 —— launchUrl 是同步操作。
        // 3. `dmwork://verified` deeplink 回跳兜底保留：Aegis 验证完成后 302
        //    回该 scheme，由 VerifyLandingActivity 刷 /v1/user/current 同步本地
        //    realname_verified 状态，栈回到本页 onResume 重绘徽章。
        // 4. 老版本 App 继续依赖 的 verify-token 翻译层（已处理）。
        //
        // URL 解析合约 + 安全守卫（scheme=https + host 非空 + 末尾斜杠剥离）
        // 由 AegisVerifyUrlResolver 兜底, 见 AegisVerifyUrlResolverTest。
        AegisVerifyUrlResolver.Result resolved =
                AegisVerifyUrlResolver.resolve(WKConfig.getInstance().getAppConfig());
        if (!resolved.isOk()) {
            // appconfig 未下发可用 account_url（冷启动未到 / 后端没配 / 配的 URL 非法）。
            // 绝不回退到任何硬编码 prod 域 —— 弹原有 toast 提示用户稍后重试。
            showToast(getString(R.string.realname_verify_token_fail));
            return;
        }
        Uri verifyUri = Uri.parse(resolved.url);
        // 双保险: Custom Tabs launchUrl 前再校验一次 scheme + host 一致性。
        // host 必须来自 accountUrl 本身, 严禁与 resolver 给的不符（防未来有人在
        // resolver 出来以后又拼一次外部输入）。
        if (verifyUri == null
                || !"https".equalsIgnoreCase(verifyUri.getScheme())
                || verifyUri.getHost() == null
                || !verifyUri.getHost().equalsIgnoreCase(resolved.host)) {
            showToast(getString(R.string.realname_verify_token_fail));
            return;
        }
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder()
                    .setToolbarColor(ContextCompat.getColor(this, R.color.colorAccent))
                    .setShowTitle(true)
                    .build();
            intent.launchUrl(this, verifyUri);
        } catch (Exception e) {
            // 没装任何可以处理 http(s) intent 的包（极少见，CI 模拟器可能遇到）。
            showToast(getString(R.string.realname_verify_no_browser));
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.loginOutTv.setOnClickListener(v -> WKDialogUtils.getInstance().showDialog(this, getString(R.string.login_out), getString(R.string.login_out_dialog), true, "", getString(R.string.login_out), 0, 0, index -> {
            if (index == 1) {
                WKUIKitApplication.getInstance().exitLogin(0);
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.languageLayout, view1 -> startActivity(new Intent(this, WKLanguageActivity.class)));
        wkVBinding.clearImgCacheLayout.setOnClickListener(v -> showDialog(getString(R.string.clear_img_cache_tips), index -> {
            if (index == 1) {
                DataCleanManager.clearAllCache(SettingActivity.this);
                str = "0.00M";
                wkVBinding.imageCacheTv.setText(str);
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.destroyAccountLayout, view1 -> startActivity(new Intent(this, DestroyAccountActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.aboutLayout, view1 -> startActivity(new Intent(this, WKAboutActivity.class)));
        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            String v = WKDeviceUtils.getInstance().getVersionName(this);
            if (version != null && !TextUtils.isEmpty(version.url) && WKDeviceUtils.getInstance().isNewerVersion(version.version, v)) {
                wkVBinding.newVersionIv.setVisibility(View.VISIBLE);
            } else {
                wkVBinding.newVersionIv.setVisibility(View.GONE);
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.thirdShareLayout, view1 -> {
            Intent intent = new Intent(this, WKWebViewActivity.class);
            intent.putExtra("url", WKApiConfig.privacyUrl);
            startActivity(intent);
        });
    }


    //获取缓存大小
    private void getCacheSize() {
        //  P-11: AppExecutors.io() 替代 new Thread()（磁盘统计属 I/O）
        AppExecutors.io().execute(() -> {
            try {
                str = DataCleanManager.getTotalCacheSize(SettingActivity.this);
                if (str.equalsIgnoreCase("0.0Byte")) {
                    str = "0.00M";
                }
                AndroidUtilities.runOnUIThread(() -> wkVBinding.imageCacheTv.setText(str));
            } catch (Exception e) {
                WKLogUtils.e("获取图片缓存大小错误");
            }
        });

    }

}
