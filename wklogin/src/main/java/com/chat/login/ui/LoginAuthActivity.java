package com.chat.login.ui;

import android.app.Activity;
import android.content.Intent;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.login.R;
import com.chat.login.databinding.ActLoginAuthLayoutBinding;
import com.chat.login.service.LoginModel;

/**
 * 2020-10-26 15:41
 * 登录验证
 */
public class LoginAuthActivity extends WKBaseActivity<ActLoginAuthLayoutBinding> {
    private String uid, phone;

    @Override
    protected ActLoginAuthLayoutBinding getViewBinding() {
        return ActLoginAuthLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.login_auth);
    }

    @Override
    protected void initView() {
        wkVBinding.loginAuthBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.loginAuthDescTv.setText(String.format(getString(R.string.login_auth_desc), getString(R.string.app_name)));
        phone = getIntent().getStringExtra("phone");
        wkVBinding.phoneNumTv.setText(phone);
        uid = getIntent().getStringExtra("uid");
    }

    @Override
    protected void initListener() {
        wkVBinding.loginAuthBtn.setOnClickListener(v -> LoginModel.getInstance().sendLoginAuthVerifCode(uid, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                Intent intent = new Intent(this, InputLoginAuthVerifCodeActivity.class);
                intent.putExtra("uid", uid);
                intent.putExtra("phone", phone);
                resultLac.launch(intent);
            } else showToast(msg);
        }));
    }

    ActivityResultLauncher<Intent> resultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result != null && result.getResultCode() == Activity.RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    });


}
