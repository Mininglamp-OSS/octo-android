package com.chat.scan;

import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.ScanResultMenu;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.scan.entity.ScanResult;
import com.xinbida.wukongim.entity.WKChannelType;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/**
 * 2020-04-19 16:05
 * 扫描处理
 */
class ScanUtils extends WKBaseModel {

    private IHandleScanResult iHandleScanResult;

    private ScanUtils() {

    }

    private static class ScanUtilsBinder {
        static final ScanUtils scanUtils = new ScanUtils();
    }

    static ScanUtils getInstance() {
        return ScanUtilsBinder.scanUtils;
    }

    void handleScanResult(AppCompatActivity activity, String result, @NonNull final IHandleScanResult iHandleScanResult) {
        this.iHandleScanResult = iHandleScanResult;
        try {
            if (result.startsWith("HTTP") || result.startsWith("http") || result.startsWith("www") || result.startsWith("WWW")) {
                URL resultURL = new URL(result);
                URL baseURL = new URL(WKApiConfig.baseUrl + "qrcode/");
                if (resultURL.getHost().equals(baseURL.getHost()) && resultURL.getPath().contains(baseURL.getPath())) {
                    requestScanResult(activity, result);
                } else {
                    iHandleScanResult.showWebView(result);
                }
            } else if (result.startsWith("mtp://")) {
            } else {
                iHandleScanResult.showOtherContent(result);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    private void requestScanResult(AppCompatActivity activity, String url) {
        request(createService(ScanService.class).getScanResult(url), new IRequestResultListener<>() {
            @Override
            public void onSuccess(ScanResult result) {
                handleResult(activity, result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (!TextUtils.isEmpty(msg)) {
                    WKToastUtils.getInstance().showToast(msg);
                }
            }
        });
    }

    interface IHandleScanResult {
        void showOtherContent(String content);

        void showWebView(String url);

        void dismissView();
    }

    private void handleResult(AppCompatActivity activity, ScanResult result) {

        if (result.forward.equals("h5")) {
            Intent intent = new Intent(WKScanApplication.getInstance().mContext.get(), WKWebViewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("url", String.valueOf(result.data.get("url")));
            WKScanApplication.getInstance().mContext.get().startActivity(intent);
            iHandleScanResult.dismissView();
        } else {
            String type = result.type;
            JSONObject dataJson = new JSONObject(result.data);
            if (type.equals("group")) {
                if (dataJson.has("group_no")) {
                    String groupNo = dataJson.optString("group_no");
                    String authCode = dataJson.optString("auth_code");
                    String groupName = dataJson.optString("name", "");
                    String avatar = dataJson.optString("avatar", "");
                    int memberCount = dataJson.optInt("member_count", 0);
                    boolean isMember = dataJson.optBoolean("is_member", false);
                    openJoinGroupPage(activity, groupNo, authCode, groupName, avatar, memberCount, isMember);
                }

            } else {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put("type", type);
                hashMap.put("data", dataJson);
                List<ScanResultMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.wkScan, hashMap);
                if (WKReader.isNotEmpty(list)) {
                    for (int i = 0, size = list.size(); i < size; i++) {
                        boolean canHandle = list.get(i).iResultClick.invoke(hashMap);
                        if (canHandle) {
                            iHandleScanResult.dismissView();
                            break;
                        }
                    }
                }
            }
        }
    }

    private void openJoinGroupPage(AppCompatActivity activity, String groupNo, String authCode, String groupName, String avatar, int memberCount, boolean isMember) {
        Intent intent = new Intent(activity, ScanJoinGroupActivity.class);
        intent.putExtra("group_no", groupNo);
        intent.putExtra("auth_code", authCode);
        intent.putExtra("group_name", groupName);
        intent.putExtra("avatar", avatar);
        intent.putExtra("member_count", memberCount);
        intent.putExtra("is_member", isMember);
        activity.startActivity(intent);
        iHandleScanResult.dismissView();
    }
}
