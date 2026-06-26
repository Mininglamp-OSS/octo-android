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

    // Space 串消息诊断模式隐藏入口: 连点 App 图标 5 次开启 2 小时采集.
    // 选 aboutIconIv 是因为它独立、不在任何 clickable 容器内, 也没既有点击处理,
    // 误触零副作用. 同页就有"导出诊断日志"按钮, 流程闭环.
    //
    // 窗口策略: 滑动窗口 — 每次点击都把 deadline 顺延 5 秒, 用户慢点也能凑齐.
    // 后两次点击给 Toast 反馈, 避免"是不是没点中"的疑惑.
    private static final int DIAG_TAP_THRESHOLD = 5;
    private static final long DIAG_TAP_WINDOW_MS = 5000L;
    private int diagTapCount = 0;
    private long diagLastTapAt = 0L;

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
        android.util.Log.d("DiagSink", "WKAboutActivity.initView() — diag-icon-tap installed");

        SingleClickUtil.onSingleClick(wkVBinding.icpTV, view1 -> {
            // 隐私政策
            showWebView("https://beian.miit.gov.cn/#/home");
        });
        SingleClickUtil.onSingleClick(wkVBinding.privacyPolicyLayout, view1 -> {
            showWebView(WKApiConfig.privacyUrl);
        });
        SingleClickUtil.onSingleClick(wkVBinding.userAgreementLayout, view1 -> {
            showWebView(WKApiConfig.termsUrl);
        });
        SingleClickUtil.onSingleClick(wkVBinding.checkNewVersionLayout, view1 -> checkNewVersion(true));
        SingleClickUtil.onSingleClick(wkVBinding.exportDiagLogLayout, view1 -> DiagnosticLogFile.share(this));

        // Space 串消息诊断: 连点 App 图标 5 次(滑动窗口 5 秒)弹开关 dialog. 后 3 击给 Toast
        // 反馈, 用户不会怀疑没点中. 排查结束后整段可删.
        wkVBinding.aboutIconIv.setClickable(true);
        wkVBinding.aboutIconIv.setFocusable(true);
        wkVBinding.aboutIconIv.setOnClickListener(v -> onDiagIconTap());

        checkNewVersion(false);
        String v = WKDeviceUtils.getInstance().getVersionName(this);
        // 临时标记 — 装上新版后版本号会显示 "version 1.3.3-diag"; 旧版仍显示 "version 1.3.3".
        // 排查结束后这个 -diag 后缀和上面整段诊断采集代码一起删.
        wkVBinding.versionTv.setText(String.format("version %s-diag", v));
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

    private void onDiagIconTap() {
        long now = System.currentTimeMillis();
        if (diagTapCount == 0 || (now - diagLastTapAt) > DIAG_TAP_WINDOW_MS) {
            diagTapCount = 1;
        } else {
            diagTapCount++;
        }
        diagLastTapAt = now;
        android.util.Log.d("DiagSink", "icon tap count=" + diagTapCount);
        // 倒数 3 击开始给反馈, 避免用户怀疑没点中
        int remaining = DIAG_TAP_THRESHOLD - diagTapCount;
        if (diagTapCount >= DIAG_TAP_THRESHOLD) {
            diagTapCount = 0;
            diagLastTapAt = 0L;
            showDiagDialog();
        } else if (remaining <= 3) {
            WKToastUtils.getInstance().showToastNormal("还差 " + remaining + " 次");
        }
    }

    private void showDiagDialog() {
        boolean enabled = com.chat.base.utils.DiagSink.isEnabled();
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        if (enabled) {
            long until = com.chat.base.utils.DiagSink.getEnabledUntilMs();
            long leftMin = Math.max(0L, (until - System.currentTimeMillis()) / 60000L);
            builder.setTitle("诊断采集进行中")
                    .setMessage("剩余约 " + leftMin + " 分钟。\n复现问题后点本页\"导出诊断日志\"分享。")
                    .setPositiveButton("立即关闭", (d, w) -> com.chat.base.utils.DiagSink.disable(this))
                    .setNegativeButton("继续采集", null);
        } else {
            builder.setTitle("开启诊断日志采集")
                    .setMessage("将开启 2 小时诊断采集来定位 Space 消息串台问题。\n开启后请复现问题，再点本页\"导出诊断日志\"分享给我们。\n2 小时后自动关闭。")
                    .setPositiveButton("开启", (d, w) -> {
                        com.chat.base.utils.DiagSink.enable(this, com.chat.base.utils.DiagSink.DEFAULT_TTL_MS);
                        WKToastUtils.getInstance().showToastNormal("诊断采集已开启");
                    })
                    .setNegativeButton("取消", null);
        }
        try {
            builder.show();
        } catch (Throwable ignored) {
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
