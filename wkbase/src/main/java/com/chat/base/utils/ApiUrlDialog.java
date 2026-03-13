package com.chat.base.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.R;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSharedPreferencesUtil;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiUrlDialog extends Dialog {

    private static final String DEFAULT_URL = "https://api.example.com";

    public interface OnConfirmListener {
        void onConfirm(String url);
    }

    private OnConfirmListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient checkClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public ApiUrlDialog(@NonNull Activity activity) {
        super(activity);
    }

    public void setOnConfirmListener(OnConfirmListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_api_url);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.horizontalMargin = 0.06f;
            getWindow().setAttributes(params);
        }

        EditText urlEt = findViewById(R.id.urlEt);
        TextView currentUrlTv = findViewById(R.id.currentUrlTv);
        TextView errorTv = findViewById(R.id.errorTv);
        TextView confirmBtn = findViewById(R.id.confirmBtn);
        View closeBtn = findViewById(R.id.closeBtn);
        View cancelBtn = findViewById(R.id.cancelBtn);

        // 显示当前地址
        String currentUrl = WKApiConfig.baseUrl;
        if (!TextUtils.isEmpty(currentUrl)) {
            currentUrlTv.setVisibility(View.VISIBLE);
            currentUrlTv.setText(getContext().getString(R.string.api_url_dialog_current, currentUrl));
        }

        // 回填已保存的自定义地址，没有则显示默认地址
        String savedUrl = WKSharedPreferencesUtil.getInstance().getSP("api_base_url");
        if (!TextUtils.isEmpty(savedUrl)) {
            urlEt.setText(savedUrl);
            urlEt.setSelection(savedUrl.length());
        } else {
            urlEt.setText(DEFAULT_URL);
            urlEt.setSelection(DEFAULT_URL.length());
        }

        // 输入时清除错误提示
        urlEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                errorTv.setVisibility(View.GONE);
            }
        });

        closeBtn.setOnClickListener(v -> dismiss());
        cancelBtn.setOnClickListener(v -> dismiss());
        confirmBtn.setOnClickListener(v -> {
            String input = urlEt.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                errorTv.setText(R.string.api_url_dialog_empty);
                errorTv.setVisibility(View.VISIBLE);
                return;
            }
            // 自动补全协议：用户输入了 http:// 则保留（支持本地开发服务器），否则默认 https
            String url = input;
            if (!url.toLowerCase().startsWith("https://") && !url.toLowerCase().startsWith("http://")) {
                url = "https://" + url;
            }
            // 去掉末尾斜杠后，自动补全 /api 路径
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            if (!url.endsWith("/api")) {
                url = url + "/api";
            }

            errorTv.setVisibility(View.GONE);
            confirmBtn.setEnabled(false);
            confirmBtn.setText(R.string.api_url_dialog_checking);
            long startTime = System.currentTimeMillis();
            String finalUrl = url;
            checkUrl(url, reachable -> {
                // 至少显示 1 秒"验证中"，避免一闪而过
                long elapsed = System.currentTimeMillis() - startTime;
                long delay = Math.max(0, 1000 - elapsed);
                mainHandler.postDelayed(() -> {
                    confirmBtn.setEnabled(true);
                    confirmBtn.setText(R.string.api_url_dialog_confirm);
                    if (reachable) {
                        WKSharedPreferencesUtil.getInstance().putSP("api_base_url", finalUrl);
                        dismiss();
                        if (listener != null) {
                            listener.onConfirm(finalUrl);
                        }
                    } else {
                        errorTv.setText(R.string.api_url_dialog_unreachable);
                        errorTv.setVisibility(View.VISIBLE);
                    }
                }, delay);
            });
        });
    }

    private void checkUrl(String baseUrl, OnCheckResult callback) {
        String sep = baseUrl.endsWith("/") ? "" : "/";
        String testUrl = baseUrl + sep + "v1/common/appconfig";
        Request request = new Request.Builder().url(testUrl).get().build();
        checkClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onResult(false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                response.close();
                mainHandler.post(() -> callback.onResult(true));
            }
        });
    }

    private interface OnCheckResult {
        void onResult(boolean reachable);
    }
}
