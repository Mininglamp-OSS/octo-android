package com.xinbida.wukongim;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;

import com.xinbida.wukongim.db.WKDBHelper;
import com.xinbida.wukongim.entity.WKSyncMsgMode;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

/**
 * 5/20/21 5:27 PM
 */
public class WKIMApplication {
    private final String sharedName = "wk_account_config";
    //协议版本
    public byte protocolVersion = 4;

    private WKIMApplication() {
    }

    private static class WKApplicationBinder {
        static final WKIMApplication app = new WKIMApplication();
    }

    public static WKIMApplication getInstance() {
        return WKApplicationBinder.app;
    }

    private WeakReference<Context> mContext;

    public Context getContext() {
        if (mContext == null) {
            return null;
        }
        return mContext.get();
    }

    void initContext(Context context) {
        this.mContext = new WeakReference<>(context);
    }

    //    private String tempUid;
    private String tempRSAPublicKey;
    private WKDBHelper mDbHelper;
    public boolean isCanConnect = true;
    private String fileDir = "wkIM";
    private WKSyncMsgMode syncMsgMode;

    public WKSyncMsgMode getSyncMsgMode() {
        if (syncMsgMode == null) syncMsgMode = WKSyncMsgMode.READ;
        return syncMsgMode;
    }

    // 同步消息模式
    public void setSyncMsgMode(WKSyncMsgMode mode) {
        this.syncMsgMode = mode;
    }

    public String getRSAPublicKey() {
        if (mContext == null) {
            WKLoggerUtils.getInstance().e("The passed in context is null");
            return "";
        }
        if (TextUtils.isEmpty(tempRSAPublicKey)) {
            SharedPreferences setting = mContext.get().getSharedPreferences(
                    sharedName, Context.MODE_PRIVATE);
            tempRSAPublicKey = setting.getString("wk_tempRSAPublicKey", "");
        }
        return tempRSAPublicKey;
    }

    public void setRSAPublicKey(String key) {
        if (mContext == null) return;
        tempRSAPublicKey = key;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putString("wk_tempRSAPublicKey", key);
        editor.apply();
    }

    public String getUid() {
        if (mContext == null) {
            WKLoggerUtils.getInstance().e("The passed in context is null");
            return "";
        }
        String tempUid = "";
        if (TextUtils.isEmpty(tempUid)) {
            SharedPreferences setting = mContext.get().getSharedPreferences(
                    sharedName, Context.MODE_PRIVATE);
            tempUid = setting.getString("wk_UID", "");
        }
        return tempUid;
    }

    public void setUid(String uid) {
        if (mContext == null) return;
        // tempUid = uid;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putString("wk_UID", uid);
        editor.apply();
    }

    public String getToken() {
        if (mContext == null) return "";
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        return setting.getString("wk_Token", "");
    }

    public void setToken(String token) {
        if (mContext == null) return;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putString("wk_Token", token);
        editor.apply();
    }


    public synchronized WKDBHelper getDbHelper() {
        if (mDbHelper == null) {
            String uid = getUid();
            if (!TextUtils.isEmpty(uid)) {
                mDbHelper = WKDBHelper.getInstance(mContext.get(), uid);
            } else {
                WKLoggerUtils.getInstance().e("get DbHelper uid is null");
            }
        }
        return mDbHelper;
    }

    public void closeDbHelper() {
        if (mDbHelper != null) {
            mDbHelper.close();
            mDbHelper = null;
        }
    }

    public long getDBUpgradeIndex() {
        if (mContext == null) return 0;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        return setting.getLong(getUid() + "_db_upgrade_index", 0);
    }

    public void setDBUpgradeIndex(long index) {
        if (mContext == null) return;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putLong(getUid() + "_db_upgrade_index", index);
        editor.apply();
    }

    private void setDeviceId(String deviceId) {
        if (mContext == null) return;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        if (TextUtils.isEmpty(deviceId))
            deviceId = UUID.randomUUID().toString().replaceAll("-", "");
        editor.putString(getUid() + "_wk_device_id", deviceId);
        editor.apply();
    }

    public String getDeviceId() {
        if (mContext == null) return "";
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        String deviceId = setting.getString(getUid() + "_wk_device_id", "");
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = UUID.randomUUID().toString().replaceAll("-", "");
            setDeviceId(deviceId);
        }
        return deviceId + "ad";
    }

    public boolean isNetworkConnected() {
        if (mContext == null) {
            WKLoggerUtils.getInstance().e("check network status The passed in context is null");
            return false;
        }
        ConnectivityManager manager = (ConnectivityManager) mContext.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities networkCapabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
                if (networkCapabilities != null) {
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                }
            } else {
                NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }

    public void setFileCacheDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public String getFileCacheDir() {
        if (TextUtils.isEmpty(fileDir)) {
            fileDir = "wkIM";
        }
        return Objects.requireNonNull(getContext().getExternalFilesDir(fileDir)).getAbsolutePath();
    }
}
