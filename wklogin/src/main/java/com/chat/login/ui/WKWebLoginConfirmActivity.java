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

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.login.R;
import com.chat.login.databinding.ActWebLoginAuthLayoutBinding;
import com.chat.login.service.LoginModel;

/**
 * 2020-04-19 19:00
 * web登录确认
 */
public class WKWebLoginConfirmActivity extends WKBaseActivity<ActWebLoginAuthLayoutBinding> {
    private String auth_code;

    @Override
    protected ActWebLoginAuthLayoutBinding getViewBinding() {
        return ActWebLoginAuthLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        Theme.setColorFilter(this, wkVBinding.closeIv,R.color.popupTextColor);
    }

    @Override
    protected void initView() {
        wkVBinding.loginBtn.getBackground().setTint(Theme.colorAccount);
        auth_code = getIntent().getStringExtra("auth_code");
    }

    @Override
    protected void initListener() {
        wkVBinding.webLoginDescTv.setText(String.format(getString(R.string.web_login_desc), getString(R.string.app_name)));
        wkVBinding.closeIv.setOnClickListener(v -> finish());
        wkVBinding.closeTv.setOnClickListener(v -> finish());
        wkVBinding.loginBtn.setOnClickListener(v -> LoginModel.getInstance().webLogin(auth_code, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                finish();
            } else showToast(msg);
        }));
    }

}
