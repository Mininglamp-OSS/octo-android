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

package com.chat.uikit.group.webhook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.config.WKApiConfig;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActChannelWebhookUrlLayoutBinding;
import com.chat.uikit.group.webhook.service.IncomingWebhook;

import com.chat.base.base.WKBaseActivity;

/**
 * 一次性 URL 页：create / regenerate 之后展示 webhook 推送地址 +
 * native / wecom / github 三种调用方式。token 与 URL 仅此一次返回，关闭后无法再次查看。
 *
 * <p>1:1 对齐 iOS WKChannelWebhookUrlVC：
 * <ul>
 *   <li>顶部红色警示横条 "出于安全考虑..."；</li>
 *   <li>地址卡（点整张可复制 + 「复制」按钮）；</li>
 *   <li>调用示例：通用 native curl / GitHub Payload / 企业微信兼容 curl；</li>
 *   <li>底部「我已复制并保存，关闭」实心 CTA。</li>
 * </ul>
 */
public class ChannelWebhookUrlActivity extends WKBaseActivity<ActChannelWebhookUrlLayoutBinding> {

    private static final String EXTRA_URL = "webhookUrl";
    private static final String EXTRA_URL_NATIVE = "webhookUrlNative";
    private static final String EXTRA_URL_GITHUB = "webhookUrlGithub";
    private static final String EXTRA_URL_WECOM = "webhookUrlWecom";

    public static void start(Context ctx, IncomingWebhook webhook) {
        if (ctx == null || webhook == null) return;
        Intent intent = new Intent(ctx, ChannelWebhookUrlActivity.class);
        intent.putExtra(EXTRA_URL, nullToEmpty(webhook.url));
        intent.putExtra(EXTRA_URL_NATIVE, nullToEmpty(webhook.urlNative));
        intent.putExtra(EXTRA_URL_GITHUB, nullToEmpty(webhook.urlGithub));
        intent.putExtra(EXTRA_URL_WECOM, nullToEmpty(webhook.urlWecom));
        ctx.startActivity(intent);
    }

    private String url;
    private String urlNative;
    private String urlGithub;
    private String urlWecom;

    @Override
    protected ActChannelWebhookUrlLayoutBinding getViewBinding() {
        return ActChannelWebhookUrlLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_webhook_url_title);
    }

    @Override
    protected void initView() {
        url = nullToEmpty(getIntent().getStringExtra(EXTRA_URL));
        urlNative = nullToEmpty(getIntent().getStringExtra(EXTRA_URL_NATIVE));
        urlGithub = nullToEmpty(getIntent().getStringExtra(EXTRA_URL_GITHUB));
        urlWecom = nullToEmpty(getIntent().getStringExtra(EXTRA_URL_WECOM));

        renderContent();

        SingleClickUtil.onSingleClick(wkVBinding.doneBtn, v -> finish());
    }

    private void renderContent() {
        LinearLayout container = wkVBinding.contentLayout;
        container.removeAllViews();

        // 警示横条
        container.addView(buildWarningBanner());
        container.addView(buildSpace(12));

        // Webhook 地址卡片
        String nativeAbs = absoluteUrl(!TextUtils.isEmpty(urlNative) ? urlNative : url);
        container.addView(buildUrlCard(getString(R.string.str_webhook_url_address), nativeAbs));
        container.addView(buildSpace(18));

        // 调用示例标题
        TextView examplesTitle = new TextView(this);
        examplesTitle.setText(R.string.str_webhook_url_examples);
        examplesTitle.setTextSize(13);
        examplesTitle.setTextColor(ContextCompat.getColor(this, R.color.color999));
        examplesTitle.setPadding(dp(8), 0, 0, dp(8));
        container.addView(examplesTitle);

        // 通用 native curl 卡片
        String nativeSample = getString(R.string.str_webhook_native_sample);
        String nativeCurl = buildCurl("native", nativeAbs, nativeSample);
        container.addView(buildCurlCard(
                getString(R.string.str_webhook_curl_native_title),
                nativeCurl,
                getString(R.string.str_webhook_curl_native_note)));
        container.addView(buildSpace(12));

        // GitHub Payload 卡片
        String githubAbs = absoluteUrl(urlGithub);
        if (!TextUtils.isEmpty(githubAbs)) {
            container.addView(buildGithubCard(githubAbs));
            container.addView(buildSpace(12));
        }

        // 企业微信 curl 卡片
        String wecomAbs = absoluteUrl(urlWecom);
        if (!TextUtils.isEmpty(wecomAbs)) {
            String wecomCurl = buildCurl("wecom", wecomAbs, getString(R.string.str_webhook_wecom_sample));
            container.addView(buildCurlCard(
                    getString(R.string.str_webhook_curl_wecom_title),
                    wecomCurl,
                    getString(R.string.str_webhook_curl_wecom_note)));
        }
    }

    private View buildWarningBanner() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundResource(R.drawable.bg_webhook_warning_banner);
        layout.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView icon = new TextView(this);
        icon.setText("⚠️");
        icon.setTextSize(16);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.rightMargin = dp(8);
        layout.addView(icon, iconLp);

        TextView text = new TextView(this);
        text.setText(R.string.str_webhook_warning);
        text.setTextSize(13);
        text.setTextColor(0xFFD33A2C);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        layout.addView(text, textLp);
        return layout;
    }

    private View buildUrlCard(String title, String urlText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.radian_normal_layout);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        // 标题行：title + 复制按钮
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleLbl = new TextView(this);
        titleLbl.setText(title);
        titleLbl.setTextSize(13);
        titleLbl.setTextColor(ContextCompat.getColor(this, R.color.color999));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(titleLbl, titleLp);

        TextView copyBtn = buildCopyButton(getString(R.string.str_webhook_copy));
        copyBtn.setOnClickListener(v -> copy(urlText));
        titleRow.addView(copyBtn);
        card.addView(titleRow);

        // URL 文本（可选中、等宽）
        AppCompatTextView urlView = new AppCompatTextView(this);
        urlView.setText(urlText);
        urlView.setTextIsSelectable(true);
        urlView.setTypeface(Typeface.MONOSPACE);
        urlView.setTextSize(12);
        urlView.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        urlView.setBackgroundResource(R.drawable.bg_webhook_code_block);
        urlView.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = dp(8);
        card.addView(urlView, urlLp);

        // 整张卡片点击触发复制
        card.setOnClickListener(v -> copy(urlText));
        return card;
    }

    private View buildCurlCard(String title, String codeText, String note) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.radian_normal_layout);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView titleLbl = new TextView(this);
        titleLbl.setText(title);
        titleLbl.setTextSize(14);
        titleLbl.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        card.addView(titleLbl);

        AppCompatTextView codeView = new AppCompatTextView(this);
        codeView.setText(codeText);
        codeView.setTextIsSelectable(true);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextSize(12);
        codeView.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        codeView.setBackgroundResource(R.drawable.bg_webhook_code_block);
        codeView.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.topMargin = dp(10);
        card.addView(codeView, codeLp);

        TextView noteLbl = new TextView(this);
        noteLbl.setText(note);
        noteLbl.setTextSize(12);
        noteLbl.setTextColor(ContextCompat.getColor(this, R.color.color999));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(8);
        card.addView(noteLbl, noteLp);

        // 「复制示例」描边按钮
        TextView copyBtn = buildOutlineCopyButton(getString(R.string.str_webhook_copy_example));
        copyBtn.setOnClickListener(v -> copy(codeText));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                dp(100), dp(32));
        copyLp.topMargin = dp(10);
        copyLp.gravity = Gravity.END;
        card.addView(copyBtn, copyLp);
        return card;
    }

    private View buildGithubCard(String urlText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.radian_normal_layout);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView titleLbl = new TextView(this);
        titleLbl.setText(R.string.str_webhook_github_title);
        titleLbl.setTextSize(14);
        titleLbl.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        card.addView(titleLbl);

        TextView intro = new TextView(this);
        intro.setText(R.string.str_webhook_github_intro);
        intro.setTextSize(13);
        intro.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(6);
        card.addView(intro, introLp);

        AppCompatTextView codeView = new AppCompatTextView(this);
        codeView.setText(urlText);
        codeView.setTextIsSelectable(true);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextSize(12);
        codeView.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        codeView.setBackgroundResource(R.drawable.bg_webhook_code_block);
        codeView.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.topMargin = dp(10);
        card.addView(codeView, codeLp);

        TextView copyBtn = buildOutlineCopyButton(getString(R.string.str_webhook_copy_address));
        copyBtn.setOnClickListener(v -> copy(urlText));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(100), dp(32));
        copyLp.topMargin = dp(8);
        copyLp.gravity = Gravity.END;
        card.addView(copyBtn, copyLp);

        // 三步说明
        String[] steps = new String[]{
                getString(R.string.str_webhook_github_step1),
                getString(R.string.str_webhook_github_step2),
                getString(R.string.str_webhook_github_step3),
        };
        for (int i = 0; i < steps.length; i++) {
            View row = buildGithubStepRow(i + 1, steps[i]);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            card.addView(row, rlp);
        }
        return card;
    }

    private View buildGithubStepRow(int index, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(index));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(11);
        badge.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
        badge.setBackgroundResource(R.drawable.bg_webhook_step_badge);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        badgeLp.rightMargin = dp(10);
        badgeLp.topMargin = dp(1);
        row.addView(badge, badgeLp);

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t, tLp);
        return row;
    }

    private TextView buildCopyButton(String title) {
        TextView btn = new TextView(this);
        btn.setText(title);
        btn.setTextSize(13);
        btn.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundResource(R.drawable.bg_webhook_outline_btn);
        btn.setPadding(dp(12), 0, dp(12), 0);
        btn.setMinHeight(dp(26));
        return btn;
    }

    private TextView buildOutlineCopyButton(String title) {
        TextView btn = new TextView(this);
        btn.setText(title);
        btn.setTextSize(13);
        btn.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundResource(R.drawable.bg_webhook_outline_btn);
        return btn;
    }

    private View buildSpace(int dpValue) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpValue)));
        return v;
    }

    private void copy(String text) {
        if (TextUtils.isEmpty(text)) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("webhook", text));
        }
        WKToastUtils.getInstance().showToast(getString(R.string.copied));
    }

    /** 与 web buildWebhookCurlExample 对齐 */
    private String buildCurl(String key, String urlText, String sample) {
        if (TextUtils.isEmpty(urlText)) return "";
        JSONObject body = new JSONObject();
        if ("wecom".equals(key)) {
            body.put("msgtype", "text");
            JSONObject text = new JSONObject();
            text.put("content", sample == null ? "" : sample);
            body.put("text", text);
        } else {
            body.put("content", sample == null ? "" : sample);
        }
        String bodyJson = body.toJSONString();
        return "curl -X POST " + shellQuote(urlText)
                + " \\\n  -H 'Content-Type: application/json'"
                + " \\\n  -d " + shellQuote(bodyJson);
    }

    private static String shellQuote(String s) {
        if (s == null) s = "";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String absoluteUrl(String relative) {
        return IncomingWebhook.absoluteURL(relative, WKApiConfig.baseUrl);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
