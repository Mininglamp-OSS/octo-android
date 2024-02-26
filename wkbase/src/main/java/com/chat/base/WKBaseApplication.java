package com.chat.base;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.DBHelper;
import com.chat.base.emoji.EmojiManager;
import com.chat.base.entity.AppModule;
import com.chat.base.glide.OkHttpUrlLoader;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.CrashHandler;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKFileUtils;
import com.chat.base.utils.WKReader;
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;

import org.telegram.ui.Components.RLottieApplication;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;


/**
 * 2020-02-26 09:52
 */
public class WKBaseApplication {
    private WeakReference<Context> context;
    private DBHelper mDbHelper;
    private String fileDir = "wkIM";// 缓存目录

    public boolean disconnect = true;

    public String versionName;
    public String appID = "wukongchat";

    public static volatile Handler applicationHandler;

    private WKBaseApplication() {
    }

    private static class WApplicationBinder {
        final static WKBaseApplication wb = new WKBaseApplication();
    }

    public static WKBaseApplication getInstance() {
        return WApplicationBinder.wb;
    }

    public String packageName;
    public Application application;
    private List<AppModule> appModules;

    public void init(@NonNull String packageName, Application context) {
        applicationHandler = new Handler(context.getMainLooper());
        this.packageName = packageName;
        this.application = context;
        this.context = new WeakReference<>(context);
        float density = context.getResources().getDisplayMetrics().density;
        AndroidUtilities.setDensity(density);
        boolean isShowDialog = WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog");
        if (isShowDialog) {
            return;
        }
        String json = WKSharedPreferencesUtil.getInstance().getSPWithUID("app_module");
        if (!TextUtils.isEmpty(json)) {
            appModules = JSON.parseArray(json, AppModule.class);
        }
        versionName = WKDeviceUtils.getInstance().getVersionName(context);
        Glide.get(context).getRegistry().replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
        initCacheDir();
        new Thread(() -> {
            EmojiManager.getInstance().init();
            RLottieApplication.getInstance().init(context);
            CrashHandler.getInstance().init(context);
            //158638
//            HttpsUtils.SSLParams sslParams1 = HttpsUtils.getSslSocketFactory();
            initX5WebView();

        }).start();

    }

    public Context getContext() {
        return context.get();
    }

    /**
     * 获取数据库
     *
     * @return dbHelper
     */
    public synchronized DBHelper getDbHelper() {
        if (mDbHelper == null) {
            String uid = WKConfig.getInstance().getUid();
            if (!TextUtils.isEmpty(uid) && context != null && context.get() != null) {
                mDbHelper = DBHelper.getInstance(context.get(), uid);
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

    public String getFileDir() {
        if (TextUtils.isEmpty(fileDir))
            fileDir = "wkIM";
        if (!TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
            fileDir = String.format("%s/%s", fileDir, WKConfig.getInstance().getUid());
        }
        return fileDir;
    }

    private void initX5WebView() {
        boolean isShowDialog = WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog");
        if (isShowDialog) {
            return;
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(map);

//        QbSdk.PreInitCallback cb = new QbSdk.PreInitCallback() {
//
//            @Override
//            public void onViewInitFinished(boolean arg0) {
//                //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
//            }
//
//            @Override
//            public void onCoreInitFinished() {
//            }
//        };
//        //x5内核初始化接口
//        QbSdk.initX5Environment(context, cb);
    }

    private void initCacheDir() {
        WKConstants.avatarCacheDir = Objects.requireNonNull(getContext().getExternalFilesDir("wkAvatars")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.avatarCacheDir);
        WKConstants.imageDir = Objects.requireNonNull(getContext().getExternalFilesDir("wkImages")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.imageDir);
        WKConstants.videoDir = Objects.requireNonNull(getContext().getExternalFilesDir("wkVideos")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.videoDir);
        WKConstants.voiceDir = Objects.requireNonNull(getContext().getExternalFilesDir("wkVoices")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.voiceDir);
        WKConstants.chatBgCacheDir = Objects.requireNonNull(getContext().getExternalFilesDir("wkChatBg")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.chatBgCacheDir);
        WKConstants.messageBackupDir = Objects.requireNonNull(getContext().getExternalFilesDir("messageBackup")).getAbsolutePath() + "/";
        WKFileUtils.getInstance().createFileDir(WKConstants.messageBackupDir);
        WKConstants.chatDownloadFileDir = Objects.requireNonNull(getContext().getExternalFilesDir("chatDownloadFile")).getAbsolutePath() + "/";
    }

    public AppModule getAppModuleWithSid(String sid) {
        AppModule appModule = null;
        if (WKReader.isNotEmpty(appModules)) {
            for (AppModule appModule1 : appModules) {
                if (appModule1.getSid().equals(sid)) {
                    appModule = appModule1;
                    break;
                }
            }
        }
        return appModule;
    }

    public boolean appModuleIsInjection(AppModule appModule) {
        if (appModule == null) {
            return true;
        }
        return appModule.getStatus() != 0 && appModule.getChecked();
    }
}
