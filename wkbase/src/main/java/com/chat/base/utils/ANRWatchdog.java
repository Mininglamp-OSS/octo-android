package com.chat.base.utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tencent.bugly.crashreport.CrashReport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * ANR 看门狗：子线程每 5 秒检测主线程是否响应，
 * 超时则抓全线程堆栈写入 DiagnosticLogFile + 上报 Bugly。
 */
public final class ANRWatchdog extends Thread {

    private static final String TAG = "ANRWatchdog";
    private static final int CHECK_INTERVAL_MS = 5000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean responded = true;
    private volatile boolean stopped = false;
    private static volatile ANRWatchdog instance;
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        uploadPendingLogs();
        startMonitoring();
    }

    private static void startMonitoring() {
        if (instance == null) {
            synchronized (ANRWatchdog.class) {
                if (instance == null) {
                    instance = new ANRWatchdog();
                    instance.start();
                }
            }
        }
    }

    public static void stopMonitoring() {
        ANRWatchdog w = instance;
        if (w != null) {
            w.stopped = true;
            w.interrupt();
            instance = null;
        }
    }

    private ANRWatchdog() {
        super("ANRWatchdog");
        setDaemon(true);
    }

    @Override
    public void run() {
        while (!stopped && !isInterrupted()) {
            responded = false;
            mainHandler.post(() -> responded = true);

            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }

            if (!responded && !stopped) {
                String fullReport = buildReport();
                String briefReport = buildBriefReport();
                Log.e(TAG, briefReport);

                DiagnosticLogFile.append(appContext, fullReport);

                try {
                    CrashReport.postCatchedException(new ANRError(briefReport));
                    Log.w(TAG, "ANR report posted to Bugly successfully");
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to post to Bugly: " + e.getMessage());
                }

                try {
                    Thread.sleep(CHECK_INTERVAL_MS * 3L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private static String buildBriefReport() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder(2048);

        sb.append("Time: ").append(sdf.format(new Date())).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Main thread state: ").append(Looper.getMainLooper().getThread().getState()).append('\n');

        sb.append("\n--- Main Thread Stack ---\n");
        StackTraceElement[] mainStack = Looper.getMainLooper().getThread().getStackTrace();
        for (StackTraceElement e : mainStack) {
            sb.append("  at ").append(e.toString()).append('\n');
        }

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sb.append("\n--- Memory ---\n");
        sb.append("Used: ").append(usedMB).append("MB / Max: ").append(maxMB).append("MB\n");

        return sb.toString();
    }

    private static String buildReport() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder(4096);

        sb.append("====== ANR DETECTED ======\n");
        sb.append("Time: ").append(sdf.format(new Date())).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Main thread state: ").append(Looper.getMainLooper().getThread().getState()).append('\n');

        sb.append("\n--- Main Thread Stack ---\n");
        StackTraceElement[] mainStack = Looper.getMainLooper().getThread().getStackTrace();
        for (StackTraceElement e : mainStack) {
            sb.append("  at ").append(e.toString()).append('\n');
        }

        sb.append("\n--- All Threads (").append(Thread.activeCount()).append(") ---\n");
        try {
            for (Map.Entry<Thread, StackTraceElement[]> entry :
                    Thread.getAllStackTraces().entrySet()) {
                Thread t = entry.getKey();
                StackTraceElement[] stack = entry.getValue();
                if (stack.length == 0) continue;
                sb.append('\n').append('"').append(t.getName()).append('"')
                        .append(" state=").append(t.getState())
                        .append(" id=").append(t.getId()).append('\n');
                for (StackTraceElement e : stack) {
                    sb.append("  at ").append(e.toString()).append('\n');
                }
            }
        } catch (Throwable ignored) {
        }

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sb.append("\n--- Memory ---\n");
        sb.append("Used: ").append(usedMB).append("MB / Max: ").append(maxMB).append("MB\n");

        sb.append("====== END ANR ======\n\n");
        return sb.toString();
    }

    private static void uploadPendingLogs() {
        if (appContext == null) return;
        AppExecutors.io().execute(() -> {
            try {
                String logs = DiagnosticLogFile.read(appContext);
                if (logs.isEmpty()) return;
                Log.w(TAG, "Uploading pending diagnostic logs");
                // 只截取主线程堆栈部分上报，避免全线程快照超出 Bugly 消息限制
                String toUpload = extractMainThreadSection(logs);
                CrashReport.postCatchedException(new ANRError(toUpload));
                DiagnosticLogFile.clear(appContext);
            } catch (Throwable ignored) {
            }
        });
    }

    private static String extractMainThreadSection(String logs) {
        // 找最后一个 ANR/CRASH 块的主线程部分
        int lastAnr = logs.lastIndexOf("====== ANR DETECTED");
        if (lastAnr < 0) lastAnr = logs.lastIndexOf("====== CRASH DETECTED");
        if (lastAnr < 0) {
            return logs.length() > 4096 ? logs.substring(logs.length() - 4096) : logs;
        }
        String block = logs.substring(lastAnr);
        // 截到 "--- All Threads" 之前，只保留主线程堆栈
        int allThreads = block.indexOf("--- All Threads");
        if (allThreads > 0) {
            block = block.substring(0, allThreads);
        }
        // 加上内存信息
        int memIdx = logs.lastIndexOf("--- Memory ---");
        if (memIdx > lastAnr) {
            int endIdx = logs.indexOf("======", memIdx);
            if (endIdx > memIdx) {
                block = block + logs.substring(memIdx, endIdx);
            }
        }
        return block;
    }

    public static final class ANRError extends Exception {
        public ANRError(String detail) {
            super("ANR Watchdog:\n" + detail);
        }
    }
}
