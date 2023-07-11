package com.chat.login;

import android.content.Context;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.lxj.xpopup.core.CenterPopupView;

/**
 * 7/21/21 11:28 AM
 */
public class UpdateApiDialog extends CenterPopupView {
    String ip, port;
    private final IUpdateAPI iUpdateAPI;

    public UpdateApiDialog(@NonNull Context context, String ip, String port, IUpdateAPI iUpdateAPI) {
        super(context);
        this.ip = ip;
        this.port = port;
        this.iUpdateAPI = iUpdateAPI;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        EditText portEt = findViewById(R.id.portEt);
        EditText ipEt = findViewById(R.id.ipEt);
        if (!TextUtils.isEmpty(ip)) {
            ipEt.setText(ip);
            ipEt.setSelection(ip.length());
            ipEt.requestFocus();
        }
        if (!TextUtils.isEmpty(port)) portEt.setText(port);
        findViewById(R.id.sureTv).setOnClickListener(v -> {
            dismiss();
            iUpdateAPI.onResult(ipEt.getText().toString(), portEt.getText().toString());
        });
        findViewById(R.id.cancelTv).setOnClickListener(v -> {
            dismiss();
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.update_api_dialog_view;
    }

    public interface IUpdateAPI {
        void onResult(String ip, String port);
    }
}
