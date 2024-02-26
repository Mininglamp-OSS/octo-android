package com.chat.base.config;

import android.annotation.SuppressLint;
import android.provider.Settings;
import android.text.TextUtils;

import com.chat.base.WKBaseApplication;

import java.util.UUID;

public class WKConstants {
    public static final String refreshContacts = "refresh_contacts";
    public static final String newMsgChannelID = "wk_new_msg_notification";
    public static final String newRTCChannelID = "wk_new_rtc_notification";
    public static String imageDir;
    public static String videoDir;
    public static String voiceDir;
    public static String avatarCacheDir;
    public static String chatBgCacheDir;
    public static String messageBackupDir;
    public static String chatDownloadFileDir;
    private static int keyboardHeight = 0;

    // 软键盘高度
    public static int getKeyboardHeight() {
        if (keyboardHeight == 0)
            keyboardHeight = WKSharedPreferencesUtil.getInstance().getInt("keyboardHeight");
        return keyboardHeight;
    }

    public static void setKeyboardHeight(int mKeyboardHeight) {
        if (mKeyboardHeight != keyboardHeight) {
            keyboardHeight = mKeyboardHeight;
            WKSharedPreferencesUtil.getInstance().putInt("keyboardHeight", mKeyboardHeight);
        }
    }

    public static String getDeviceUUID() {
        String uid = WKConfig.getInstance().getUid();
        String deviceUUID = WKSharedPreferencesUtil.getInstance().getSP(uid + "device_uuid");
        if (TextUtils.isEmpty(deviceUUID)) {
            deviceUUID = UUID.randomUUID().toString().replaceAll("-", "");
            WKSharedPreferencesUtil.getInstance().putSP(uid + "device_uuid", deviceUUID);
        }
        return deviceUUID;
    }

    @SuppressLint("HardwareIds")
    public static String getDeviceID() {
        String deviceUUID;
        deviceUUID = Settings.Secure.getString(WKBaseApplication.getInstance().getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        if (TextUtils.isEmpty(deviceUUID)) {
            deviceUUID = WKSharedPreferencesUtil.getInstance().getSP("device_id");
            if (TextUtils.isEmpty(deviceUUID)) {
                deviceUUID = UUID.randomUUID().toString().replaceAll("-", "");
                WKSharedPreferencesUtil.getInstance().putSP("device_id", deviceUUID);
            }
        }
        return deviceUUID;
    }


    public static boolean isLogin() {
        return !TextUtils.isEmpty(WKConfig.getInstance().getUid());
    }

    public static float getFontScale() {
        float fontScale = WKSharedPreferencesUtil.getInstance().getFloat("font_scale", 1);
        if (fontScale <= 0) {
            fontScale = 1;
        }
        return fontScale;
    }
    public static void setFontScale(float fontSizeScale){
        WKSharedPreferencesUtil.getInstance().putFloat("font_scale",fontSizeScale);
    }
}
