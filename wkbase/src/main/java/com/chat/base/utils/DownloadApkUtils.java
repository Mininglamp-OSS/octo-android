/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.utils;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.File;


/**
 * DownloadManager工具类
 * APK 存储在 app 私有目录 (getExternalFilesDir) 下，避免 scoped storage 的权限问题
 */
public class DownloadApkUtils {

    /**
     * 下载回调，通知调用方当前走了哪条路径
     */
    public interface IDownloadCallback {
        /** APK 已存在，直接发起安装 */
        void onInstallDirectly();

        /** 开始下载新 APK */
        void onDownloadStarted();

        /** 下载任务已存在（重复点击） */
        void onDownloadAlreadyRunning();

        /** 出错（URL 解析失败等） */
        void onError(String msg);
    }

    private static final String APK_DIR = "apk_update";

    //下载器
    private DownloadManager downloadManager;
    //下载的ID
    private long downloadId;
    //下载url
    private String downloadUrl;
    private File localFiles;
    private boolean isdownload = false;
    private Context registeredContext;

    private DownloadApkUtils() {

    }

    private static class DownloadApkUtilsBinder {
        final static DownloadApkUtils download = new DownloadApkUtils();
    }

    public static DownloadApkUtils getInstance() {
        return DownloadApkUtilsBinder.download;
    }

    /**
     * 获取 APK 下载目录（app 私有目录，不受 scoped storage 限制）
     */
    private File getApkDir(Context context) {
        File externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (externalDir == null) {
            // 外部存储不可用，降级到内部存储
            externalDir = new File(context.getFilesDir(), Environment.DIRECTORY_DOWNLOADS);
        }
        File dir = new File(externalDir, APK_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 获取 APK 文件路径
     */
    private File getApkFile(Context context, String versionName) {
        return new File(getApkDir(context), "update_" + versionName.replace(".", "_") + ".apk");
    }

    /**
     * 清理旧版本 APK 文件（保留当前版本）
     */
    private void cleanOldApks(Context context, String currentVersion) {
        File dir = getApkDir(context);
        String currentFileName = getApkFile(context, currentVersion).getName();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".apk") && !f.getName().equals(currentFileName)) {
                    f.delete();
                }
            }
        }
    }

    /**
     * 检查本地是否已有可用的 APK 文件
     */
    public boolean hasLocalApk(Context context, String versionName) {
        if (TextUtils.isEmpty(versionName)) return false;
        File file = getApkFile(context, versionName);
        return file.exists() && isApkValid(context, versionName, file);
    }

    /**
     * 检查 DownloadManager 中是否真的有活跃的下载任务，
     * 防止 isdownload 标记因广播未触发而卡住
     */
    @SuppressLint("Range")
    private boolean isReallyDownloading() {
        if (downloadManager == null || downloadId == 0) {
            return false;
        }
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            try (Cursor c = downloadManager.query(query)) {
                if (c != null && c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    return status == DownloadManager.STATUS_RUNNING
                            || status == DownloadManager.STATUS_PENDING;
                }
            }
        } catch (Exception e) {
            WKLogUtils.e("下载apk", "查询下载状态异常: " + e.getMessage());
        }
        return false;
    }

    /**
     * 下载apk
     */
    public void downloadAPK(Context context, String versionName, String url, IDownloadCallback callback) {
        if (TextUtils.isEmpty(versionName)) {
            WKLogUtils.e("下载apk", "版本名称错误---versionName=" + versionName);
            if (callback != null) callback.onError("版本名称错误");
            return;
        }
        downloadUrl = url;
        // 使用 app 私有目录，可靠删除和检测
        localFiles = getApkFile(context, versionName);
        WKLogUtils.e("下载apk", "目标文件路径=" + localFiles.getAbsolutePath());

        if (localFiles.exists() && isApkValid(context, versionName, localFiles)) {
            // 文件存在且有效，直接安装
            WKLogUtils.e("下载apk", "本地已有有效 APK，直接安装");
            if (callback != null) callback.onInstallDirectly();
            installAPK(localFiles);
        } else {
            // 防止 isdownload 因广播丢失而卡死：向 DownloadManager 确认是否真的在下载
            if (isdownload && !isReallyDownloading()) {
                WKLogUtils.e("下载apk", "isdownload 标记异常，重置");
                isdownload = false;
            }
            if (isdownload) {
                if (callback != null) callback.onDownloadAlreadyRunning();
                return;
            }
            isdownload = true;
            // 清理上一次的下载任务，防止 DownloadManager 内部残留导致重试失败
            if (downloadId != 0 && downloadManager != null) {
                downloadManager.remove(downloadId);
                downloadId = 0;
            }
            unregisterReceiverSafely();
            // 删除旧文件（app 私有目录，delete 一定成功）
            if (localFiles.exists()) {
                localFiles.delete();
            }
            // 清理其他旧版本 APK
            cleanOldApks(context, versionName);
            // 创建下载任务
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setAllowedOverRoaming(false);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                request.setTitle(context.getString(R.string.app_name) + "新版本下载");
                request.setDescription("下载中...");
                request.setVisibleInDownloadsUi(true);
                // 使用 setDestinationUri 指定 app 私有目录
                request.setDestinationUri(Uri.fromFile(localFiles));
                WKLogUtils.e("新版本下载网络url地址=", url);
                WKLogUtils.e("新版本下载本地文件地址=", localFiles.getAbsolutePath());
                // 获取DownloadManager
                if (null == downloadManager) {
                    downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                }
                if (downloadManager == null) {
                    isdownload = false;
                    if (callback != null) callback.onError("下载服务不可用");
                    return;
                }
                // 将下载请求加入下载队列
                downloadId = downloadManager.enqueue(request);
                // 注册广播接收者，监听下载状态
                registeredContext = context;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(receiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
                if (callback != null) callback.onDownloadStarted();
            } catch (Exception e) {
                CrashReport.postCatchedException(e);
                isdownload = false;
                if (callback != null) callback.onError("下载失败，请重试");
            }
        }
    }

    //广播监听下载的各个状态
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkStatus(context);
        }
    };


    //检查下载状态
    private void checkStatus(Context mContext) {
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            //通过下载的id查找
            query.setFilterById(downloadId);
            Cursor c = downloadManager.query(query);
            if (null == c) {
                return;
            }
            if (c.moveToFirst()) {
                @SuppressLint("Range") int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                switch (status) {
                    //下载暂停
                    case DownloadManager.STATUS_PAUSED:
                        WKLogUtils.e("新版本下载状态", "下载暂停");
                        isdownload = false;
                        unregisterReceiverSafely();
                        break;
                    //下载延迟
                    case DownloadManager.STATUS_PENDING:
                        WKLogUtils.e("新版本下载状态", "下载延迟");
                        break;
                    //正在下载
                    case DownloadManager.STATUS_RUNNING:
                        WKLogUtils.e("新版本下载状态", "正在下载");
                        break;
                    //下载完成
                    case DownloadManager.STATUS_SUCCESSFUL:
                        //下载完成安装APK
                        isdownload = false;
                        unregisterReceiverSafely();
                        installAPK(localFiles);
                        break;
                    //下载失败
                    case DownloadManager.STATUS_FAILED:
                        isdownload = false;
                        unregisterReceiverSafely();
                        @SuppressLint("Range") int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                        CrashReport.postCatchedException(new RuntimeException(
                                "OTA download failed: reason=" + reason + ", url=" + downloadUrl));
                        break;
                }
            }
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public boolean isDownloading() {
        return isdownload;
    }

    @SuppressLint("Range")
    public int getDownloadProgress() {
        if (downloadManager == null || downloadId == 0) {
            return 0;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        try (Cursor c = downloadManager.query(query)) {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_FAILED) {
                    return -1;
                }
                long downloaded = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (total > 0) {
                    return (int) (downloaded * 100 / total);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 检测本地 APK 文件是否有效且版本匹配
     */
    private static boolean isApkValid(Context context, String version, File apkFile) {
        if (!apkFile.exists() || apkFile.length() == 0) {
            return false;
        }
        try {
            PackageInfo pkgInfo = context.getPackageManager()
                    .getPackageArchiveInfo(apkFile.getAbsolutePath(),
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
            if (pkgInfo == null || TextUtils.isEmpty(pkgInfo.versionName)) {
                // APK 解析失败，文件可能损坏
                WKLogUtils.e("下载apk", "APK 解析失败，文件可能损坏: " + apkFile.getAbsolutePath());
                return false;
            }
            // 确认包名一致（防止误判其他 APK）
            String currentPkg = context.getPackageName();
            if (!currentPkg.equals(pkgInfo.packageName)) {
                WKLogUtils.e("下载apk", "APK 包名不匹配: " + pkgInfo.packageName);
                return false;
            }
            int apkVersionCode = Integer.parseInt(pkgInfo.versionName.replaceAll("\\.", ""));
            int netVersion = Integer.parseInt(version.replaceAll("\\.", "").trim());
            if (netVersion <= apkVersionCode) {
                return true;
            }
        } catch (Exception e) {
            WKLogUtils.e("下载apk", "APK 校验异常: " + e.getMessage());
        }
        return false;
    }

    //下载到本地后执行安装
    public void installAPK(File file) {
        try {
            Context context = WKBaseApplication.getInstance().getContext();
            if (!checkPermissions()) {
                requestPermissions(context);
                return;
            }

            if (null != context && file != null && file.exists()) {
                PackageManager pm = context.getPackageManager();
                PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
                if (info != null) {
                    Intent intent;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        intent = new Intent(Intent.ACTION_VIEW);
                    } else {
                        intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Uri apkUri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider", file);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        apkUri = Uri.fromFile(file);
                    }
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    context.startActivity(intent);
                }
            } else {
                WKLogUtils.e("文件异常=", "downloadFileUri=" + file);
                //获取不到文件信息，跳转到浏览器下载
                openBrowser(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
            WKToastUtils.getInstance().showToastNormal("安装失败");
        }
    }

    private void unregisterReceiverSafely() {
        try {
            if (registeredContext != null) {
                registeredContext.unregisterReceiver(receiver);
            }
        } catch (Exception ignored) {
        }
        registeredContext = null;
    }

    /**
     * 调用第三方浏览器打开
     *
     * @param context
     */
    public void openBrowser(Context context) {
        if (TextUtils.isEmpty(downloadUrl)) {
            return;
        }
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(downloadUrl));
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(Intent.createChooser(intent, "请选择浏览器"));
        } else {
            Toast.makeText(context.getApplicationContext(), "请下载浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检测安装权限
     */
    public boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WKBaseApplication.getInstance().getContext().getPackageManager().canRequestPackageInstalls();
        } else {
            return true;
        }
    }

    /**
     * 申请权限
     */
    public void requestPermissions(Context activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            Uri packageURI = Uri.parse("package:" + activity.getPackageName());
            intent.setData(packageURI);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
