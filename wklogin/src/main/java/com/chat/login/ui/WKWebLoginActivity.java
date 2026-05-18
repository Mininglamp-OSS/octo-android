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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKToastUtils;
import com.chat.login.R;
import com.chat.login.databinding.ActWebLoginLayoutBinding;

import com.octoim.rlottie.RLottieDrawable;

public class WKWebLoginActivity extends WKBaseActivity<ActWebLoginLayoutBinding> {
    @Override
    protected ActWebLoginLayoutBinding getViewBinding() {
        return ActWebLoginLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText("");
    }


    @Override
    protected void initView() {
        wkVBinding.urlTv.setText(WKConfig.getInstance().getAppConfig().web_url);
        wkVBinding.webLoginDescTv.setText(String.format(getString(R.string.web_scan_login_desc), WKConfig.getInstance().getAppConfig().web_url));
        wkVBinding.nameTv.setText(String.format(getString(R.string.web_side), getString(R.string.app_name)));
        Theme.setPressedBackground(wkVBinding.copyIv);

        RLottieDrawable drawable = new RLottieDrawable(this, R.raw.qrcode_web, "", AndroidUtilities.dp(180), AndroidUtilities.dp(180), false, null);
        wkVBinding.imageView.setAutoRepeat(false);
        wkVBinding.imageView.setAnimation(drawable);
        wkVBinding.imageView.playAnimation();
    }

    @Override
    protected void initListener() {
        wkVBinding.copyIv.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", WKConfig.getInstance().getAppConfig().web_url);
            assert cm != null;
            cm.setPrimaryClip(mClipData);
            WKToastUtils.getInstance().showToastNormal(getString(R.string.copied));
        });
        wkVBinding.scanLayout.setOnClickListener(v -> {
            EndpointManager.getInstance().invoke("wk_scan_show", null);
        });
    }
}
