package com.chat.login;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.lxj.xpopup.core.CenterPopupView;

/**
 * 2020-12-01 15:16
 * 其他设备登录提示
 */
@SuppressLint("ViewConstructor")
public class OtherDeviceLoginDialogView extends CenterPopupView {
    int from = 1;

    public OtherDeviceLoginDialogView(@NonNull Context context, int from) {
        super(context);
        this.from = from;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView contentTv = findViewById(R.id.contentTv);
        if (from == 2) {
            contentTv.setText(R.string.wk_ban);
        }
        findViewById(R.id.sureTv).setOnClickListener(view -> dismiss());
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.other_device_login_dialog;
    }
}
