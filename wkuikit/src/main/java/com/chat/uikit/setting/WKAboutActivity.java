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

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.utils.DiagnosticLogFile;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.BuildConfig;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActAboutLayoutBinding;

/**
 * 5/26/21 3:03 PM
 * 关于
 */
public class WKAboutActivity extends WKBaseActivity<ActAboutLayoutBinding> {

    private int versionClickCount = 0;
    private long lastClickTime = 0;

    @Override
    protected ActAboutLayoutBinding getViewBinding() {
        return ActAboutLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(String.format("%s%s", getString(R.string.about), getString(R.string.app_name)));
    }

    @Override
    protected void initView() {

        SingleClickUtil.onSingleClick(wkVBinding.icpTV, view1 -> {
            // 隐私政策
            showWebView("https://beian.miit.gov.cn/#/home");
        });
        SingleClickUtil.onSingleClick(wkVBinding.privacyPolicyLayout, view1 -> {
            // 隐私政策
            showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html");
        });
        SingleClickUtil.onSingleClick(wkVBinding.userAgreementLayout, view1 -> {
            // 用户协议
            showWebView(WKApiConfig.baseWebUrl + "user_agreement.html");
        });
        SingleClickUtil.onSingleClick(wkVBinding.checkNewVersionLayout, view1 -> checkNewVersion(true));
        SingleClickUtil.onSingleClick(wkVBinding.exportDiagLogLayout, view1 -> DiagnosticLogFile.share(this));
        checkNewVersion(false);
        String v = WKDeviceUtils.getInstance().getVersionName(this);
        wkVBinding.versionTv.setText(String.format("version %s", v));
        wkVBinding.appNameTv.setText(R.string.app_name);

        // Debug 模式：连续点击版本号 5 次模拟 ANR（测试 ANRWatchdog 采集）
        if (BuildConfig.DEBUG) {
            wkVBinding.versionTv.setOnClickListener(view1 -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime > 2000) versionClickCount = 0;
                lastClickTime = now;
                versionClickCount++;
                if (versionClickCount >= 5) {
                    versionClickCount = 0;
                    WKToastUtils.getInstance().showToastNormal("模拟 ANR：主线程阻塞 10 秒...");
                    wkVBinding.versionTv.postDelayed(() -> {
                        try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
                    }, 500);
                }
            });
        }
    }

    @Override
    protected void initListener() {
    }

    private void checkNewVersion(boolean isShowDialog) {
        WKCommonModel.getInstance().getAppNewVersion(isShowDialog, version -> {
            String v = WKDeviceUtils.getInstance().getVersionName(WKAboutActivity.this);
            if (version != null && !TextUtils.isEmpty(version.url) && WKDeviceUtils.getInstance().isNewerVersion(version.version, v)) {
                if (isShowDialog) {
                    WKDialogUtils.getInstance().showNewVersionDialog(WKAboutActivity.this, version);
                } else {
                    wkVBinding.newVersionIv.setVisibility(View.VISIBLE);
                }
            } else {
                wkVBinding.newVersionIv.setVisibility(View.GONE);
                if (isShowDialog) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.is_new_version));
                }
            }
        });
    }


}
