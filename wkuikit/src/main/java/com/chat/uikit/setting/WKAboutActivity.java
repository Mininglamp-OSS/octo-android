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

    private void onDiagIconTap() {
        long now = System.currentTimeMillis();
        if (diagTapCount == 0 || (now - diagLastTapAt) > DIAG_TAP_WINDOW_MS) {
            diagTapCount = 1;
        } else {
            diagTapCount++;
        }
        diagLastTapAt = now;
        android.util.Log.d("DiagSink", "icon tap count=" + diagTapCount);
        if (diagTapCount >= DIAG_TAP_THRESHOLD) {
            diagTapCount = 0;
            diagLastTapAt = 0L;
            showDiagDialog();
        }
    }

    private void showDiagDialog() {
        boolean enabled = com.chat.base.utils.DiagSink.isEnabled();
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        // positive 按钮颜色: 开启用品牌紫(primary), 关闭用提醒红(destructive); negative 一律用灰.
        // 用项目已有的色值, 不引入新资源, 排查结束后整段代码删干净.
        final int positiveColor;
        if (enabled) {
            long until = com.chat.base.utils.DiagSink.getEnabledUntilMs();
            long leftMin = Math.max(0L, (until - System.currentTimeMillis()) / 60000L);
            builder.setTitle("诊断采集进行中")
                    .setMessage("剩余约 " + leftMin + " 分钟。\n复现问题后点本页\"导出诊断日志\"分享。")
                    .setPositiveButton("立即关闭", (d, w) -> {
                        com.chat.base.utils.DiagSink.disable(this);
                        WKToastUtils.getInstance().showToastSuccess("诊断采集已关闭");
                    })
                    .setNegativeButton("继续采集", null);
            positiveColor = androidx.core.content.ContextCompat.getColor(this, com.chat.base.R.color.reminderColor);
        } else {
            builder.setTitle("开启诊断日志采集")
                    .setMessage("将开启 2 小时诊断采集来定位 Space 消息串台问题。\n开启后请复现问题，再点本页\"导出诊断日志\"分享给我们。\n2 小时后自动关闭。")
                    .setPositiveButton("开启", (d, w) -> {
                        com.chat.base.utils.DiagSink.enable(this, com.chat.base.utils.DiagSink.DEFAULT_TTL_MS);
                        WKToastUtils.getInstance().showToastSuccess("诊断采集已开启");
                    })
                    .setNegativeButton("取消", null);
            positiveColor = androidx.core.content.ContextCompat.getColor(this, com.chat.base.R.color.colorAccent);
        }
        try {
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            // 不允许点空白处关闭, 否则用户继续点图标(落在 dialog 外)会把 dialog 误关.
            // 返回键仍可关(避免完全锁死), 强制走两个按钮做选择.
            dialog.setCanceledOnTouchOutside(false);
            // 按钮颜色必须在 show 之后才能拿到 view, 用 OnShowListener 兜底.
            int negColor = androidx.core.content.ContextCompat.getColor(this, com.chat.base.R.color.color999);
            dialog.setOnShowListener(d -> {
                android.widget.Button pos = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                android.widget.Button neg = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                if (pos != null) pos.setTextColor(positiveColor);
                if (neg != null) neg.setTextColor(negColor);
            });
            dialog.show();
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
