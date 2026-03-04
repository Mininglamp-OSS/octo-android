package com.chat.login.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.login.R;
import com.chat.login.databinding.ActResetLoginPwdLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Objects;

/**
 * 2020-11-25 11:21
 * 重置登录密码（邮箱）
 */
public class WKResetLoginPwdActivity extends WKBaseActivity<ActResetLoginPwdLayoutBinding> implements LoginContract.LoginView {

    private LoginPresenter presenter;

    @Override
    protected ActResetLoginPwdLayoutBinding getViewBinding() {
        return ActResetLoginPwdLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        presenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.getVerCodeBtn.getBackground().setTint(Theme.colorAccount);
        Theme.setPressedBackground(wkVBinding.backIv);
        wkVBinding.backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.colorDark), PorterDuff.Mode.MULTIPLY));

        wkVBinding.nameEt.setEnabled(true);
        wkVBinding.registerAppTv.setText(R.string.auth_email);
        wkVBinding.resetLoginPwdTv.setText(String.format(getString(R.string.auth_email_tips), getString(R.string.app_name)));
    }

    @Override
    protected void initListener() {
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
                    wkVBinding.getVerCodeBtn.setEnabled(true);
                    wkVBinding.getVerCodeBtn.setAlpha(1f);
                } else {
                    wkVBinding.getVerCodeBtn.setEnabled(false);
                    wkVBinding.getVerCodeBtn.setAlpha(0.2f);
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
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(Objects.requireNonNull(wkVBinding.pwdEt.getText()).length());
        });
        wkVBinding.sureBtn.setOnClickListener(v -> {
            String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
            String verCode = wkVBinding.verfiEt.getText().toString();
            String pwd = wkVBinding.pwdEt.getText().toString();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast(R.string.email_format_error);
                return;
            }
            if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(verCode) && !TextUtils.isEmpty(pwd)) {
                if (pwd.length() < 6 || pwd.length() > 16) {
                    showToast(R.string.pwd_length_error);
                } else {
                    loadingPopup.show();
                    presenter.emailForgetPwd(email, verCode, pwd);
                }
            }
        });
        wkVBinding.getVerCodeBtn.setOnClickListener(v -> {
            String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
            if (!TextUtils.isEmpty(email)) {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    showToast(R.string.email_format_error);
                    return;
                }
                presenter.emailSendCode(email, 2);
            }
        });
        wkVBinding.backIv.setOnClickListener(v -> finish());
    }

    private void checkStatus() {
        String email = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
        String verCode = wkVBinding.verfiEt.getText().toString();
        String pwd = wkVBinding.pwdEt.getText().toString();
        if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(verCode) && !TextUtils.isEmpty(pwd)) {
            wkVBinding.sureBtn.setAlpha(1f);
            wkVBinding.sureBtn.setEnabled(true);
        } else {
            wkVBinding.sureBtn.setAlpha(0.2f);
            wkVBinding.sureBtn.setEnabled(false);
        }
    }

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {
    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {
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
            showToast(msg);
        }
    }

    @Override
    public void setResetPwdResult(int code, String msg) {
        if (code == HttpResponseCode.success) {
            finish();
        }
    }

    @Override
    public Button getVerificationCodeBtn() {
        return wkVBinding.getVerCodeBtn;
    }

    @Override
    public EditText getNameEt() {
        return wkVBinding.nameEt;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void showError(String msg) {
        showToast(msg);
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
    }
}
