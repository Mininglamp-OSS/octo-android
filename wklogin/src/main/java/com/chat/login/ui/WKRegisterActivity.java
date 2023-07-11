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
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.views.keyboard.SoftKeyboardStateHelper;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.R;
import com.chat.login.databinding.ActRegisterLayoutBinding;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;

/**
 * 2020-06-19 15:42
 * 注册
 */
public class WKRegisterActivity extends WKBaseActivity<ActRegisterLayoutBinding> implements LoginContract.LoginView {
    private String code = "0086";
    private LoginPresenter presenter;
    private SoftKeyboardStateHelper softKeyboardStateHelper;

    @Override
    protected ActRegisterLayoutBinding getViewBinding() {
        return ActRegisterLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {

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

        softKeyboardStateHelper = new SoftKeyboardStateHelper(wkVBinding.mainView);

        wkVBinding.privacyPolicyTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html"));
        wkVBinding.userAgreementTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "user_agreement.html"));
        wkVBinding.registerAppTv.setText(String.format(getString(R.string.register_app), getString(R.string.app_name)));
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
            String phone = wkVBinding.nameEt.getText().toString();
            String verfiCode = wkVBinding.verfiEt.getText().toString();
            String pwd = wkVBinding.pwdEt.getText().toString();
            if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(verfiCode) && !TextUtils.isEmpty(pwd)) {
                if (pwd.length() < 6 || pwd.length() > 16) {
                    showSingleBtnDialog(getString(R.string.pwd_length_error));
                } else {
                    loadingPopup.show();
                    presenter.registerApp(verfiCode, code, "", phone, pwd);
                }
            }
        });
        wkVBinding.getVCodeBtn.setOnClickListener(v -> {
            String phone = wkVBinding.nameEt.getText().toString();
            if (!TextUtils.isEmpty(phone)) {
                if (code.equals("0086") && wkVBinding.nameEt.getText().toString().length() != 11) {
                    showSingleBtnDialog(getString(R.string.phone_error));
                    return;
                }
                presenter.registerCode(code, phone);
            }
        });

        wkVBinding.myTv.setOnClickListener(view1 -> {
            wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true);
        });
        wkVBinding.authCheckBox.setOnClickListener(view1 -> {
            wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true);
        });
    }

    @Override
    protected void initListener() {
        softKeyboardStateHelper.addSoftKeyboardStateListener(new SoftKeyboardStateHelper.SoftKeyboardStateListener() {
            @Override
            public void onSoftKeyboardOpened(int keyboardHeight) {
                WKConstants.setKeyboardHeight(keyboardHeight);
            }

            @Override
            public void onSoftKeyboardClosed() {

            }
        });
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(wkVBinding.pwdEt.getText().length());
        });
    }

    private void checkStatus() {
        String phone = wkVBinding.nameEt.getText().toString();
        String verfiCode = wkVBinding.verfiEt.getText().toString();
        String pwd = wkVBinding.pwdEt.getText().toString();
        if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(verfiCode) && !TextUtils.isEmpty(pwd)) {
            wkVBinding.registerBtn.setAlpha(1f);
            wkVBinding.registerBtn.setEnabled(true);
        } else {
            wkVBinding.registerBtn.setAlpha(0.2f);
            wkVBinding.registerBtn.setEnabled(false);
        }
    }


    ActivityResultLauncher<Intent> intentActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            //此处是跳转的result回调方法
            if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
                CountryCodeEntity entity = result.getData().getParcelableExtra("entity");
                assert entity != null;
                code = entity.code;
                String codeName = code.substring(2);
                wkVBinding.codeTv.setText(String.format("+%s", codeName));
            }
        }
    });

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
        loadingPopup.dismiss();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);
        hideLoading();
        new Handler(Looper.myLooper()).postDelayed(() -> {
            List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
            if (list != null && list.size() > 0) {
                for (LoginMenu menu : list) {
                    if (menu.iMenuClick != null) menu.iMenuClick.onClick();
                }
            }
            finish();
        }, 500);
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
    public void setResetPwdResult(int code, String msg) {
    }

    @Override
    public Button getVerfiCodeBtn() {
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
