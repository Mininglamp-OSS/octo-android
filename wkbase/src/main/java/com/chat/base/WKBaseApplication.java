package com.chat.base;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;

import com.alibaba.fastjson.JSON;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.chat.base.act.PlayVideoActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.DBHelper;
import com.chat.base.emoji.EmojiManager;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.PlayVideoMenu;
import com.chat.base.entity.AppModule;
import com.chat.base.glide.OkHttpUrlLoader;
import com.chat.base.net.OkHttpUtils;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.AppExecutors;
import com.chat.base.utils.CrashHandler;
import com.tencent.bugly.crashreport.CrashReport;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKFileUtils;
import com.chat.base.utils.WKReader;

import org.telegram.ui.Components.RLottieApplication;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;


/**
 * 2020-02-26 09:52
 */
public class WKBaseApplication {
    private WeakReference<Context> context;
    private DBHelper mDbHelper;
    private String fileDir = "wkIM";// 缓存目录

    // 进程级启动时间戳，供各模块计算绝对耗时
    public static final long PROCESS_START = android.os.SystemClock.elapsedRealtime();

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

        // YUJ-284 (P-01) · 冷启预热 —— 必须在 this.application 赋值之后、主线程
        // 首次触达 WKSharedPreferencesUtil 之前调用（本方法尾部
        // getBoolean("show_agreement_dialog") 即首个主线程 SP 访问点）。
        // 在后台线程上预建 EncryptedSharedPreferences 单例，把 MasterKey
        // AES256-GCM / KeyStore 握手（50-150ms）搬出主线程。
        WKSharedPreferencesUtil.prewarm();

        float density = context.getResources().getDisplayMetrics().density;
        AndroidUtilities.setDensity(density);

        // YUJ-248 (#176) — L1 stale-cache fix:
        // After the P0 patch (#175) unlocked landscape + configChanges for TabActivity /
        // ChatActivity, configuration changes no longer destroy those Activities, so the
        // one-shot AndroidUtilities.setDensity() above never re-ran and cached
        // density/screenWidth values could go stale (e.g. after unfold on a Pixel Fold).
        // Registering a ComponentCallbacks2 on the Application refreshes them globally
        // whenever the system config changes, which covers every getScreenWidth()
        // consumer (including the 6 audited sites) provided they read at use-time.
        context.registerComponentCallbacks(new android.content.ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
                try {
                    float d = application.getResources().getDisplayMetrics().density;
                    AndroidUtilities.setDensity(d);
                } catch (Throwable ignored) {
                    // defensive — never let a config-change callback crash the app
                }
            }

            @Override
            public void onLowMemory() {
            }

            @Override
            public void onTrimMemory(int level) {
            }
        });

        versionName = WKDeviceUtils.getInstance().getVersionName(context);

        // Bugly + Emoji + RLottie 合并到一个后台线程，减少 CPU 争抢
        // YUJ-283 P-11: 走 AppExecutors.io() 统一调度（带 app-io-N 命名 + daemon）
        AppExecutors.io().execute(() -> {
            CrashReport.initCrashReport(context, "6129cd9cf2", BuildConfig.DEBUG);
            if (!TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
                UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
                if (userInfo != null && !TextUtils.isEmpty(userInfo.short_no)) {
                    CrashReport.setUserId(userInfo.short_no);
                } else {
                    CrashReport.setUserId(WKConfig.getInstance().getUid());
                }
                CrashReport.putUserData(context, "uid", WKConfig.getInstance().getUid());
                if (userInfo != null && !TextUtils.isEmpty(userInfo.name)) {
                    CrashReport.putUserData(context, "name", userInfo.name);
                }
            }
            EmojiManager.getInstance().init();
            RLottieApplication.getInstance().init(context);
        });

        // Glide + cacheDir 不依赖 SP，先执行，给 EncryptedSP 后台线程更多时间
        Glide.get(context).getRegistry().replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());

        initCacheDir();

        boolean isShowDialog = WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog");

        if (isShowDialog) {
            return;
        }
        String json = WKSharedPreferencesUtil.getInstance().getSPWithUID("app_module");
        if (!TextUtils.isEmpty(json)) {
            appModules = JSON.parseArray(json, AppModule.class);
        }
        //监听视频播放
        EndpointManager.getInstance().setMethod("play_video", object -> {
            if (object instanceof PlayVideoMenu playVideoMenu) {
                @SuppressWarnings("unchecked") ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(Objects.requireNonNull(playVideoMenu.activity), new Pair<>(playVideoMenu.view, "coverIv"));
                Intent intent = new Intent(playVideoMenu.activity, PlayVideoActivity.class);
                intent.putExtra("coverImg", playVideoMenu.coverUrl);
                intent.putExtra("url", playVideoMenu.playUrl);
                intent.putExtra("title", playVideoMenu.videoTitle);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                playVideoMenu.activity.startActivity(intent, activityOptions.toBundle());
                playVideoMenu.activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
            return null;
        });
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
