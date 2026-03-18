package com.chat.base.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final String DEFAULT_URL = "https://api-test.example.com";

    private static final String[][] PRESET_SERVERS = {
            {"国内版", "api-test.example.com"},
            {"国际版", "api-test.example.com"},
    };

    public interface OnConfirmListener {
        void onConfirm(String url);
    }

    private OnConfirmListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient checkClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private EditText urlEt;
    private View[] presetViews;
    private int selectedPresetIndex = -1;

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

        urlEt = findViewById(R.id.urlEt);
        TextView currentUrlTv = findViewById(R.id.currentUrlTv);
        TextView errorTv = findViewById(R.id.errorTv);
        TextView confirmBtn = findViewById(R.id.confirmBtn);
        View closeBtn = findViewById(R.id.closeBtn);
        View cancelBtn = findViewById(R.id.cancelBtn);
        LinearLayout presetContainer = findViewById(R.id.presetContainer);

        // 显示当前地址
        String currentUrl = WKApiConfig.baseUrl;
        if (!TextUtils.isEmpty(currentUrl)) {
            currentUrlTv.setVisibility(View.VISIBLE);
            currentUrlTv.setText(getContext().getString(R.string.api_url_dialog_current, currentUrl));
        }

        // 构建预设服务器选项
        buildPresetItems(presetContainer);

        // 回填已保存的自定义地址
        String savedUrl = WKSharedPreferencesUtil.getInstance().getSP("api_base_url");
        if (!TextUtils.isEmpty(savedUrl)) {
            // 检查是否匹配预设
            boolean matchedPreset = false;
            for (int i = 0; i < PRESET_SERVERS.length; i++) {
                String presetFullUrl = "https://" + PRESET_SERVERS[i][1] + "/api";
                if (savedUrl.equals(presetFullUrl)) {
                    selectPreset(i);
                    matchedPreset = true;
                    break;
                }
            }
            if (!matchedPreset) {
                urlEt.setText(savedUrl);
                urlEt.setSelection(savedUrl.length());
            }
        } else {
            // 默认选中国内版
            selectPreset(0);
        }

        // 输入时清除错误提示并取消预设选中
        urlEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                errorTv.setVisibility(View.GONE);
                if (s.length() > 0 && selectedPresetIndex != -1) {
                    // 用户手动输入，检查是否仍匹配当前选中预设
                    String inputUrl = s.toString().trim();
                    String presetDomain = PRESET_SERVERS[selectedPresetIndex][1];
                    if (!inputUrl.equals(presetDomain) && !inputUrl.equals("https://" + presetDomain)
                            && !inputUrl.equals("https://" + presetDomain + "/api")) {
                        clearPresetSelection();
                    }
                }
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
            String url = input;
            if (!url.toLowerCase().startsWith("https://") && !url.toLowerCase().startsWith("http://")) {
                url = "https://" + url;
            }
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

    private void buildPresetItems(LinearLayout container) {
        float density = getContext().getResources().getDisplayMetrics().density;
        int itemPadding = (int) (12 * density);
        int itemMarginBottom = (int) (8 * density);
        presetViews = new View[PRESET_SERVERS.length];

        for (int i = 0; i < PRESET_SERVERS.length; i++) {
            String label = PRESET_SERVERS[i][0];
            String domain = PRESET_SERVERS[i][1];

            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
            itemLayout.setBackgroundResource(R.drawable.bg_dialog_api_preset_normal);

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i < PRESET_SERVERS.length - 1) {
                itemParams.bottomMargin = itemMarginBottom;
            }

            // 标签
            TextView labelTv = new TextView(getContext());
            labelTv.setText(label);
            labelTv.setTextSize(14);
            labelTv.setTextColor(0xFF374151);
            labelTv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.setMarginEnd((int) (8 * density));

            // 域名
            TextView domainTv = new TextView(getContext());
            domainTv.setText(domain);
            domainTv.setTextSize(13);
            domainTv.setTextColor(0xFF6B7280);
            domainTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            itemLayout.addView(labelTv, labelParams);
            itemLayout.addView(domainTv);
            container.addView(itemLayout, itemParams);

            presetViews[i] = itemLayout;
            int index = i;
            itemLayout.setOnClickListener(v -> selectPreset(index));
        }
    }

    private void selectPreset(int index) {
        selectedPresetIndex = index;
        for (int i = 0; i < presetViews.length; i++) {
            if (i == index) {
                presetViews[i].setBackgroundResource(R.drawable.bg_dialog_api_preset_selected);
            } else {
                presetViews[i].setBackgroundResource(R.drawable.bg_dialog_api_preset_normal);
            }
        }
        urlEt.setText(PRESET_SERVERS[index][1]);
        urlEt.setSelection(urlEt.getText().length());
    }

    private void clearPresetSelection() {
        selectedPresetIndex = -1;
        for (View v : presetViews) {
            v.setBackgroundResource(R.drawable.bg_dialog_api_preset_normal);
        }
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
