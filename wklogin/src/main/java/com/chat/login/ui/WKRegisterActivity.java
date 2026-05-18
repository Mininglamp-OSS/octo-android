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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.login.R;
import com.chat.login.databinding.ActRegisterLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Objects;
import android.util.Patterns;

/**
 * 2020-06-19 15:42
 * 注册
 */
public class WKRegisterActivity extends WKBaseActivity<ActRegisterLayoutBinding> implements LoginContract.LoginView {
    private String code = "0086";
    private LoginPresenter presenter;
    private WKAPPConfig appConfig;

    /** 是否需要邮箱验证码（默认开启） */
    private boolean needsVerificationCode() {
        return true;
    }

    @Override
    protected ActRegisterLayoutBinding getViewBinding() {
        return ActRegisterLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        presenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.getVCodeBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.registerBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.privacyPolicyTv.setTextColor(Theme.colorAccount);
        wkVBinding.userAgreementTv.setTextColor(Theme.colorAccount);
        wkVBinding.loginTv.setTextColor(Theme.colorAccount);
        wkVBinding.authCheckBox.setResId(getContext(), R.mipmap.round_check2);
        wkVBinding.authCheckBox.setDrawBackground(true);
        wkVBinding.authCheckBox.setHasBorder(true);
        wkVBinding.authCheckBox.setStrokeWidth(AndroidUtilities.dp(1));
        wkVBinding.authCheckBox.setBorderColor(ContextCompat.getColor(getContext(), R.color.color999));
        wkVBinding.authCheckBox.setSize(18);
        wkVBinding.authCheckBox.setColor(Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white));
        wkVBinding.authCheckBox.setVisibility(View.VISIBLE);
        wkVBinding.authCheckBox.setEnabled(true);
        wkVBinding.authCheckBox.setChecked(false, true);

        wkVBinding.privacyPolicyTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html"));
        wkVBinding.userAgreementTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "user_agreement.html"));
        wkVBinding.registerAppTv.setText(String.format(getString(R.string.register_app), getString(R.string.app_name)));

        // 正式环境：显示验证码输入区域，账号输入提示改为邮箱
        if (needsVerificationCode()) {
            wkVBinding.verCodeLayout.setVisibility(View.VISIBLE);
            wkVBinding.verCodeLineView.setVisibility(View.VISIBLE);
            wkVBinding.nameEt.setHint(R.string.input_email);
            wkVBinding.nameEt.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }

        wkVBinding.nameEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    wkVBinding.getVCodeBtn.setAlpha(1f);
                    wkVBinding.getVCodeBtn.setEnabled(true);
                } else {
                    wkVBinding.getVCodeBtn.setEnabled(false);
                    wkVBinding.getVCodeBtn.setAlpha(0.2f);
                }
                checkStatus();
            }
        });
        wkVBinding.verfiEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.pwdEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.loginTv.setOnClickListener(v -> startActivity(new Intent(this, WKLoginActivity.class)));
        wkVBinding.chooseCodeTv.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseAreaCodeActivity.class);
            intentActivityResultLauncher.launch(intent);
        });
        wkVBinding.registerBtn.setOnClickListener(v -> {
            if (!wkVBinding.authCheckBox.isChecked()) {
                showToast(R.string.agree_auth_tips);
                return;
            }

            String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
            String verCode = needsVerificationCode() ? wkVBinding.verfiEt.getText().toString().trim() : "";
            String nickname = Objects.requireNonNull(wkVBinding.nicknameEt.getText()).toString().trim();
            String pwd = Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString();
            String inviteCode = Objects.requireNonNull(wkVBinding.inviteCodeTv.getText()).toString();
            if (needsVerificationCode() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showSingleBtnDialog(getString(R.string.email_format_error));
                return;
            }
            if (needsVerificationCode() && TextUtils.isEmpty(verCode)) {
                showSingleBtnDialog(getString(R.string.hint_verfi));
                return;
            }
            if (TextUtils.isEmpty(nickname)) {
                showSingleBtnDialog(getString(R.string.nickname_not_null));
                return;
            }
            if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(pwd)) {
                if (pwd.length() < 6 || pwd.length() > 16) {
                    showSingleBtnDialog(getString(R.string.pwd_length_error));
                } else {
                    if (appConfig != null && appConfig.register_invite_on == 1 && TextUtils.isEmpty(inviteCode)) {
                        showSingleBtnDialog(getString(R.string.invite_code_not_null));
                        return;
                    }
                    loadingPopup.show();
                    presenter.emailRegister(email, verCode, nickname, pwd, inviteCode);
                }
            }
        });
        wkVBinding.getVCodeBtn.setOnClickListener(v -> {
            String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
            if (!TextUtils.isEmpty(email)) {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    showSingleBtnDialog(getString(R.string.email_format_error));
                    return;
                }
                wkVBinding.getVCodeBtn.setEnabled(false);
                wkVBinding.getVCodeBtn.setAlpha(0.5f);
                presenter.emailSendCode(email, 0);
            }
        });

        wkVBinding.myTv.setOnClickListener(view1 -> wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true));
        wkVBinding.authCheckBox.setOnClickListener(view1 -> wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true));
    }

    @Override
    protected void initListener() {
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(Objects.requireNonNull(wkVBinding.pwdEt.getText()).length());
        });
    }

    @Override
    protected void initData() {
        WKCommonModel.getInstance().getAppConfig((code, msg, wkappConfig) -> {
            if (code == HttpResponseCode.success) {
                appConfig = wkappConfig;
                if (appConfig != null && appConfig.register_invite_on == 1) {
                    wkVBinding.inviteCodeTv.setHint(R.string.input_invite_code_must);
                    wkVBinding.inviteLayout.setVisibility(View.VISIBLE);
                    wkVBinding.inviteLineView.setVisibility(View.VISIBLE);
                } else {
                    wkVBinding.inviteCodeTv.setHint(R.string.input_invite_code_not_must);
                    wkVBinding.inviteLayout.setVisibility(View.GONE);
                    wkVBinding.inviteLineView.setVisibility(View.GONE);
                }
            } else {
                showToast(msg);
            }
        });
    }

    private void checkStatus() {
        String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
        String pwd = Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString();
        boolean ready = !TextUtils.isEmpty(email) && !TextUtils.isEmpty(pwd);
        // 正式环境额外需要验证码
        if (needsVerificationCode()) {
            String verCode = wkVBinding.verfiEt.getText().toString();
            ready = ready && !TextUtils.isEmpty(verCode);
        }
        wkVBinding.registerBtn.setAlpha(ready ? 1f : 0.2f);
        wkVBinding.registerBtn.setEnabled(ready);
    }


    ActivityResultLauncher<Intent> intentActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        //此处是跳转的result回调方法
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            CountryCodeEntity entity = result.getData().getParcelableExtra("entity");
            assert entity != null;
            code = entity.code;
            String codeName = code.substring(2);
            wkVBinding.codeTv.setText(String.format("+%s", codeName));
        }
    });

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
        loadingPopup.dismiss();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);
        hideLoading();

        if (TextUtils.isEmpty(userInfoEntity.name)) {
            Intent intent = new Intent(this, PerfectUserInfoActivity.class);
            startActivity(intent);
            finish();
        } else {
            new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(() -> {
                // 注册场景：通知 loginMenus 跳过页面导航
                EndpointManager.getInstance().invoke("set_skip_navigation", true);
                List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
                if (WKReader.isNotEmpty(list)) {
                    for (LoginMenu menu : list) {
                        if (menu.iMenuClick != null) menu.iMenuClick.onClick();
                    }
                }
                EndpointManager.getInstance().invoke("show_space_guide", null);
                finish();
            }, 500);
        }
    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {

    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {
        if (code == HttpResponseCode.success) {
            if (exist == 1) {
                showSingleBtnDialog(getString(R.string.account_exist));
            } else {
                wkVBinding.nameEt.setEnabled(false);
                presenter.startTimer();
            }
        } else {
            showToast(msg);
        }
    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {

    }

    @Override
    public void setSendCodeResult(int code, String msg) {

    }

    @Override
    public void setEmailSendCodeResult(int code, String msg) {
        if (code == HttpResponseCode.success) {
            wkVBinding.nameEt.setEnabled(false);
            presenter.startTimer();
        } else {
            wkVBinding.getVCodeBtn.setEnabled(true);
            wkVBinding.getVCodeBtn.setAlpha(1f);
            showToast(msg);
        }
    }

    @Override
    public void setResetPwdResult(int code, String msg) {
    }

    @Override
    public Button getVerificationCodeBtn() {
        return wkVBinding.getVCodeBtn;
    }

    @Override
    public EditText getNameEt() {
        return wkVBinding.nameEt;
    }

    @Override
    public void showError(String msg) {
        showSingleBtnDialog(msg);
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
    }


    @Override
    public Context getContext() {
        return this;
    }

}
