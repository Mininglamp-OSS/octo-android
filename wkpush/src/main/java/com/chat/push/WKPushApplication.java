package com.chat.push;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.systembar.WKOSUtils;
import com.chat.push.service.PushModel;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.heytap.msp.push.HeytapPushManager;
import com.heytap.msp.push.callback.ICallBackResultService;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;
import com.vivo.push.PushClient;
import com.vivo.push.util.VivoPushException;
import com.xiaomi.mipush.sdk.MiPushClient;

import java.lang.ref.WeakReference;

/**
 * 2020-03-08 22:29
 * 推送管理
 */
public class WKPushApplication {
    private WKPushApplication() {
    }

    private static class PushApplicationBinder {
        static final WKPushApplication push = new WKPushApplication();
    }

    private WeakReference<Context> mContext;
    public String pushBundleID;

    public static WKPushApplication getInstance() {
        return PushApplicationBinder.push;
    }


    //初始化推送服务
    public void init(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        addListener();
        initPush();
        EndpointManager.getInstance().setMethod("", EndpointCategory.loginMenus, object -> new LoginMenu(this::initPush));
    }

    private void initPush() {
        if (mContext == null || mContext.get() == null) return;
        FirebaseApp.initializeApp(mContext.get());
        notifyChannel(WKBaseApplication.getInstance().application);
        getPushToken();
//        if (!TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
//            if (OsUtils.isEmui()) {
//                new Thread(() -> getHuaWeiToken(mContext.get())).start();
//            } else if (OsUtils.isMiui()) {
//                initXiaoMiPush(mContext.get());
//            } else if (OsUtils.isOppo()) {
//                initOPPO();
//            } else if (OsUtils.isVivo()) {
//                initVIVO();
//            }
//        }

    }

    private void getHuaWeiToken(Context context) {
        try {
            // 从agconnect-service.json文件中读取appId
//            String appId = new AGConnectOptionsBuilder().build(context).getString("client/app_id");
//            String appId = AGConnectServicesConfig.fromContext(context).getString("client/app_id");
            // 输入token标识"HCM"
            String tokenScope = "HCM";
            String token = HmsInstanceId.getInstance(context).getToken(PushKeys.huaweiAPPID, tokenScope);
            // 判断token是否为空
            if (!TextUtils.isEmpty(token)) {
                Log.e("华为推送token", token);
                PushModel.getInstance().registerDeviceToken(token, pushBundleID,"");
            }
        } catch (ApiException e) {
        }
    }

    private void initXiaoMiPush(Context context) {
        MiPushClient.registerPush(context, PushKeys.xiaoMiAppID, PushKeys.xiaoMiAppKey);
    }

    private void initOPPO() {
        HeytapPushManager.init(mContext.get(), true);
        new Thread(() -> HeytapPushManager.register(mContext.get(), PushKeys.oppoAppKey, PushKeys.oppoAppSecret, new ICallBackResultService() {
            @Override
            public void onRegister(int i, String s) {
                if (i == 0) {
                    // 注册成功
                    Log.e("tu推送ID", HeytapPushManager.getRegisterID());
                    PushModel.getInstance().registerDeviceToken(s, WKPushApplication.getInstance().pushBundleID,"");
                }
            }

            @Override
            public void onUnRegister(int i) {

            }

            @Override
            public void onSetPushTime(int i, String s) {

            }

            @Override
            public void onGetPushStatus(int i, int i1) {

            }

            @Override
            public void onGetNotificationStatus(int i, int i1) {
            }

            @Override
            public void onError(int i, String s) {

            }
        })).start();

    }

    private void initVIVO() {
        try {
            PushClient.getInstance(mContext.get()).initialize();
            PushClient.getInstance(mContext.get()).turnOnPush(state -> {
                // TODO: 开关状态处理， 0代表成功
                String regId = PushClient.getInstance(mContext.get()).getRegId();
                if (!TextUtils.isEmpty(regId)) {
                    Log.e("获取vivopush", regId);
                    PushModel.getInstance().registerDeviceToken(regId, pushBundleID,"");
                }
            });

        } catch (VivoPushException e) {
            e.printStackTrace();
        }
    }

    private void addListener() {
        EndpointManager.getInstance().setMethod("show_open_notification_dialog", object -> {
            Context context = (Context) object;
            WKDialogUtils.getInstance().showDialog(context, context.getString(R.string.open_notification_title), context.getString(R.string.open_notification_content), true, "", context.getString(R.string.open_setting), 0, Theme.colorAccount, index -> {
                if (index == 1) {
                    WKOSUtils.openChannelSetting(context, WKConstants.newMsgChannelID);
                }
            });
            return null;
        });
        //注销推送
        EndpointManager.getInstance().setMethod("wk_logout", object -> {
            OsUtils.setBadge(WKBaseApplication.getInstance().getContext(), 0);
//            PushModel.getInstance().unRegisterDeviceToken((code, msg) -> {
//                if (code != HttpResponseCode.success) {
//                    WKToastUtils.getInstance().showToastNormal(msg);
//                }
//            });
            return null;
        });

        //设置桌面红点数量
        EndpointManager.getInstance().setMethod("push_update_device_badge", object -> {
            int num = (int) object;
            PushModel.getInstance().registerBadge(num);
            OsUtils.setBadge(WKBaseApplication.getInstance().getContext(), num);
            return null;
        });
    }

    private void getPushToken() {
        int statusCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext.get());
        Log.e("google play services", statusCode + "");
        if (statusCode == ConnectionResult.SUCCESS) {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task1 -> {
                if (!task1.isSuccessful()) {
                    Log.e("获取FCM令牌错误", "-->");
                    Log.w("Firebase", "Fetching FCM registration token failed", task1.getException());
                    return;
                }
                // Get new FCM registration token
                String token = task1.getResult();
                Log.e("获取到FCM令牌", token);
                PushModel.getInstance().registerDeviceToken(token, pushBundleID,"FIREBASE");
            });
        }else {
            if (!TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
                if (OsUtils.isEmui()) {
                    new Thread(() -> getHuaWeiToken(mContext.get())).start();
                } else if (OsUtils.isMiui()) {
                    initXiaoMiPush(mContext.get());
                } else if (OsUtils.isOppo()) {
                    initOPPO();
                } else if (OsUtils.isVivo()) {
                    initVIVO();
                }
            }
        }
//        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(mContext.get()).addOnCompleteListener(new OnCompleteListener<Void>() {
//            @Override
//            public void onComplete(@NonNull Task<Void> task) {
//                if (task.isSuccessful()) {
//                    Log.d("Firebase", "onComplete: Play services OKAY");
//                    //firebase 推送 （FCM）获取token（FCM令牌）
//                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task1 -> {
//                        if (!task1.isSuccessful()) {
//                            Log.e("获取FCM令牌错误", "-->");
//                            Log.w("Firebase", "Fetching FCM registration token failed", task1.getException());
//                            return;
//                        }
//                        // Get new FCM registration token
//                        String token = task1.getResult();
//                        Log.e("获取到FCM令牌", token);
//                    });
//
//                } else {
//                    Log.e("获取FCM令牌错误11", "-->");
//                    // Show the user some UI explaining that the needed version
//                    // of Play services Could not be installed and the app can't run.
//                }
//            }
//        });
    }

    private static void notifyChannel(Application context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = WKConstants.newMsgChannelID;
            String channelName = "Default_Channel";
            String channelDescription = "this is default channel!";
            NotificationChannel mNotificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationChannel.setDescription(channelDescription);
            ((NotificationManager) context.getSystemService(Activity.NOTIFICATION_SERVICE)).createNotificationChannel(mNotificationChannel);
        }
    }
}
