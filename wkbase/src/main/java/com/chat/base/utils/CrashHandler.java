package com.chat.base.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Java 层未捕获异常处理：写本地文件 + 链式调用原 handler（Bugly）。
 *
 * 日志写到 DiagnosticLogFile 统一文件，和 ANRWatchdog 共用，
 * 用户通过"关于 → 导出诊断日志"一并发送。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final CrashHandler INSTANCE = new CrashHandler();

    private Thread.UncaughtExceptionHandler previousHandler;
    private WeakReference<Context> contextRef;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 必须在 Bugly init 之后调用，这样 previousHandler 就是 Bugly 的 handler，
     * 我们写完本地文件后再交给 Bugly 上报。
     */
    public void init(Context context) {
        contextRef = new WeakReference<>(context.getApplicationContext());
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
        try {
            String report = buildCrashReport(thread, ex);
            Log.e(TAG, report);
            DiagnosticLogFile.append(contextRef != null ? contextRef.get() : null, report);
        } catch (Throwable ignored) {
        }

        // 链式调用：交给 Bugly（或系统默认 handler）处理
        if (previousHandler != null) {
            previousHandler.uncaughtException(thread, ex);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private String buildCrashReport(Thread thread, Throwable ex) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder(4096);

        sb.append("====== CRASH DETECTED ======\n");
        sb.append("Time: ").append(sdf.format(new Date())).append('\n');
        sb.append("Thread: ").append(thread.getName()).append(" (id=").append(thread.getId()).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");

        Context ctx = contextRef != null ? contextRef.get() : null;
        if (ctx != null) {
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                sb.append("App: ").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        sb.append("\n--- Exception ---\n");
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        sb.append(sw.toString());

        // 内存信息
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sb.append("\n--- Memory ---\n");
        sb.append("Used: ").append(usedMB).append("MB / Max: ").append(maxMB).append("MB\n");

        sb.append("====== END CRASH ======\n\n");
        return sb.toString();
    }
}
