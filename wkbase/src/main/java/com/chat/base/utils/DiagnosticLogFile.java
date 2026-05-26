package com.chat.base.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

/**
 * 统一的本地诊断日志文件。
 *
 * ANRWatchdog + CrashHandler 共用同一个文件，
 * 用户通过"关于 → 导出诊断日志"一键分享。
 */
public final class DiagnosticLogFile {

    private static final String TAG = "DiagnosticLog";
    private static final String FILE_NAME = "diagnostic.log";
    private static final int MAX_SIZE = 1024 * 1024; // 1MB

    private DiagnosticLogFile() {
    }

    public static synchronized void append(Context context, String content) {
        if (context == null || content == null) return;
        try {
            File file = getFile(context);
            if (file.exists() && file.length() > MAX_SIZE) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    public static String read(Context context) {
        if (context == null) return "";
        try {
            File file = getFile(context);
            if (!file.exists() || file.length() == 0) return "";
            StringBuilder sb = new StringBuilder((int) file.length());
            BufferedReader reader = new BufferedReader(new FileReader(file));
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    public static boolean hasLogs(Context context) {
        if (context == null) return false;
        File file = getFile(context);
        return file.exists() && file.length() > 0;
    }

    public static void share(Context context) {
        File file = getFile(context);
        if (!file.exists() || file.length() == 0) {
            WKToastUtils.getInstance().showToastNormal("暂无诊断日志");
            return;
        }
        try {
            String authority = context.getPackageName() + ".fileProvider";
            android.net.Uri uri = FileProvider.getUriForFile(context, authority, file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Octo 诊断日志");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "发送诊断日志"));
        } catch (Throwable e) {
            Log.e(TAG, "share failed", e);
            WKToastUtils.getInstance().showToastNormal("分享失败");
        }
    }

    public static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static void clear(Context context) {
        File file = getFile(context);
        if (file.exists()) {
            file.delete();
        }
    }
}
