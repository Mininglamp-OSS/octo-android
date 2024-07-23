package com.chat.login;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ScanResultMenu;
import com.chat.login.ui.ChooseAreaCodeActivity;
import com.chat.login.ui.PCLoginViewActivity;
import com.chat.login.ui.WKResetLoginPwdActivity;
import com.chat.login.ui.WKWebLoginActivity;
import com.chat.login.ui.WKWebLoginConfirmActivity;

import org.json.JSONObject;

import java.util.Objects;

/**
 * 2020-03-05 19:53
 * 登录模块
 */
public class WKLoginApplication {
    private WKLoginApplication() {

    }

    private static class LoginApplicationBinder {
        static final WKLoginApplication LOGIN = new WKLoginApplication();
    }

    public static WKLoginApplication getInstance() {
        return LoginApplicationBinder.LOGIN;
    }

    public void init(Context context) {
        //监听扫一扫
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkScan, object -> new ScanResultMenu(hashMap -> {
            String type = Objects.requireNonNull(hashMap.get("type")).toString();
            if (type.equals("loginConfirm")) {
                JSONObject dataJson = (JSONObject) hashMap.get("data");
                if (dataJson != null && dataJson.has("auth_code")) {
                    String authCode = dataJson.optString("auth_code");
                    if (!TextUtils.isEmpty(authCode)) {
                        Intent intent = new Intent(context, WKWebLoginConfirmActivity.class);
                        intent.putExtra("auth_code", authCode);
                        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                }
                return true;
            } else {
                return false;
            }

        }));

        EndpointManager.getInstance().setMethod("chow_reset_login_pwd_view", object -> {
            Intent intent = new Intent(context, WKResetLoginPwdActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return null;
        });

        // 监听打开pc登录view
        EndpointManager.getInstance().setMethod("show_pc_login_view", object -> {
            Activity activity = (Activity) object;
            Intent intent = new Intent(activity, PCLoginViewActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return null;
        });
        EndpointManager.getInstance().setMethod("show_web_login_desc", object -> {
            Context activity = (Context) object;
            Intent intent = new Intent(activity, WKWebLoginActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return null;
        });
        EndpointManager.getInstance().setMethod("choose_area_code", object -> {
            Context activity = (Context) object;
            Intent intent = new Intent(activity, ChooseAreaCodeActivity.class);
            intent.putExtra("set_result", true);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return null;
        });
    }
}
