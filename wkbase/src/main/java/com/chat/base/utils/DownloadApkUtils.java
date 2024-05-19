package com.chat.base.utils;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;

import java.io.File;


/**
 * DownloadManager工具类
 */
public class DownloadApkUtils {
    //下载器
    private DownloadManager downloadManager;
    //下载的ID
    private long downloadId;
    //下载url
    private String downloadUrl;
    private File localFiles;
    private boolean isdownload = false;

    private DownloadApkUtils() {

    }

    private static class DownloadApkUtilsBinder {
        final static DownloadApkUtils download = new DownloadApkUtils();
    }

    public static DownloadApkUtils getInstance() {
        return DownloadApkUtilsBinder.download;
    }


    /**
     * 下载apk
     */
    public void downloadAPK(Context context, String versionName, String url) {
        if (TextUtils.isEmpty(versionName)) {
            WKLogUtils.e("下载apk", "版本名称错误---versionName=" + versionName);
            return;
        }
        downloadUrl = url;
        File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        WKFileUtils.getInstance().createFileDir(downloadPath);
        //本地完整路径
        localFiles = new File(downloadPath, context.getPackageName() + versionName + ".apk");
        boolean isoknew = getAPKInfo(context, versionName, localFiles);
        if (isoknew && null != localFiles && localFiles.exists()) {
            //文件存在，检测了直接安装
            installAPK(localFiles);
        } else {
            if (isdownload) {
                WKToastUtils.getInstance().showToastNormal("下载任务已经存在，可在通知栏中查看状态");
                return;
            }
            isdownload = true;
            //在线下载
            WKFileUtils.delFileOrFolder(localFiles);
            //创建下载任务
            DownloadManager.Request request;
            try {
                request = new DownloadManager.Request(Uri.parse(url));
            } catch (Exception e) {
                return;
            }
            //移动网络情况下是否允许漫游
            request.setAllowedOverRoaming(false);
            //在通知栏中显示，默认就是显示的
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setTitle(context.getString(R.string.app_name) + "新版本下载");
            request.setDescription("下载中...");
            request.setVisibleInDownloadsUi(true);
            //设置下载的路径
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, localFiles.getName());
            WKLogUtils.e("新版本下载网络url地址=", url);
            WKLogUtils.e("新版本下载本地文件夹地址=", localFiles.getAbsolutePath());
            ///storage/emulated/0
            //获取DownloadManager
            if (null == downloadManager) {
                downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            }
            //将下载请求加入下载队列，加入下载队列后会给该任务返回一个long型的id，通过该id可以取消任务，重启任务、获取下载的文件等等
            downloadId = downloadManager.enqueue(request);

            //注册广播接收者，监听下载状态
            context.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            WKToastUtils.getInstance().showToastNormal("后台下载中，可在通知栏中查看状态");
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
                        WKToastUtils.getInstance().showToastNormal("下载成功");
                        installAPK(localFiles);
                        break;
                    //下载失败
                    case DownloadManager.STATUS_FAILED:
                        isdownload = false;
                        WKLogUtils.e("新版本下载状态", "下载失败");
                        WKToastUtils.getInstance().showToastNormal("下载失败");
                        break;
                }
            }
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 检测本地apk 包是否正确
     */
    private static boolean getAPKInfo(Context context, String version, File apk_folder) {
        if (!apk_folder.exists()) {
            return false;
        }
        try {
            PackageInfo pkgInfo = null;
            pkgInfo = context.getPackageManager().
                    getPackageArchiveInfo(apk_folder.getAbsolutePath(), PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
            int appVersionCode = Integer.valueOf(pkgInfo.versionName.replaceAll("\\.", ""));
            int netVersion = Integer.valueOf(version.replaceAll("\\.", "").trim());
            if (netVersion >= appVersionCode) {
                //服务器的版本号等于下载的apk的版本号，可更新
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            try {
                WKBaseApplication.getInstance().getContext().unregisterReceiver(receiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            WKToastUtils.getInstance().showToastNormal("安装失败");
        }
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
        // 注意此处的判断intent.resolveActivity()可以返回显示该Intent的Activity对应的组件名
        // 官方解释 : Name of the component implementing an activity that can display the intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            final ComponentName componentName = intent.resolveActivity(context.getPackageManager());
            // 打印Log   ComponentName到底是什么 L.d("componentName = " + componentName.getClassName());
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
            //先获取是否有安装未知来源应用的权限
            return WKBaseApplication.getInstance().getContext().getPackageManager().canRequestPackageInstalls();
            //   return activity.getPackageManager().canRequestPackageInstalls();
        } else {
            return true;
        }
    }

    /**
     * 申请权限
     */
    public void requestPermissions(Context activity) {
        //注意这个是8.0新API
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            Uri packageURI = Uri.parse("package:" + activity.getPackageName());
            intent.setData(packageURI);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);

//            Uri packageURI = Uri.parse("package:" + activity.getPackageName());
//            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
//            activityResult.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
