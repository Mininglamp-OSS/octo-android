package com.chat.base.utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.tencent.bugly.crashreport.CrashReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ANR 看门狗：子线程每 5 秒检测主线程是否响应，
 * 超时则抓全线程堆栈写入 DiagnosticLogFile + 上报 Bugly。
 */
public final class ANRWatchdog extends Thread {

    private static final String TAG = "ANRWatchdog";
    private static final int CHECK_INTERVAL_MS = 5000;
    /**
     * 实际睡眠超过这个时长说明看门狗线程自己被冻结/挂起（cached-app freezer、Doze 深睡），
     * 此时 responded=false 不代表主线程卡住，本轮判定作废。
     */
    private static final long FREEZE_THRESHOLD_MS = CHECK_INTERVAL_MS * 2L;
    /** 从日志文本还原堆栈时最多取多少帧，够 Bugly 分组即可。 */
    private static final int MAX_PARSED_FRAMES = 32;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean responded = true;
    private volatile boolean stopped = false;
    /** 因冻结跳过的轮数，随下一次真实 ANR 一起上报，不静默丢弃。 */
    private volatile int freezeSkipCount = 0;
    private volatile String lastFreezeSkipDetail = null;
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

            long startRealtime = SystemClock.elapsedRealtime();
            long startUptime = SystemClock.uptimeMillis();
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            long elapsedRealtime = SystemClock.elapsedRealtime() - startRealtime;
            long elapsedUptime = SystemClock.uptimeMillis() - startUptime;

            if (!responded && !stopped) {
                if (elapsedRealtime > FREEZE_THRESHOLD_MS) {
                    freezeSkipCount++;
                    lastFreezeSkipDetail = "realtime=" + elapsedRealtime + "ms uptime=" + elapsedUptime
                            + "ms (expected " + CHECK_INTERVAL_MS + "ms)";
                    Log.w(TAG, "Skip round #" + freezeSkipCount + ", watchdog itself was frozen: "
                            + lastFreezeSkipDetail);
                    continue;
                }

                StackTraceElement[] mainStack = Looper.getMainLooper().getThread().getStackTrace();
                String fullReport = buildReport(mainStack, freezeSkipCount, lastFreezeSkipDetail);
                String briefReport = buildBriefReport(mainStack, freezeSkipCount, lastFreezeSkipDetail);
                Log.e(TAG, briefReport);

                DiagnosticLogFile.append(appContext, fullReport);

                try {
                    ANRError error = new ANRError(briefReport);
                    // 让 Bugly 按主线程真实堆栈聚合，否则所有 ANR 都折叠到 ANRWatchdog.run 一个 issue 里
                    if (mainStack.length > 0) {
                        error.setStackTrace(mainStack);
                    }
                    CrashReport.postCatchedException(error);
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

    private static String buildBriefReport(StackTraceElement[] mainStack,
                                           int freezeSkipCount, String lastFreezeSkipDetail) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder(2048);

        sb.append("Time: ").append(sdf.format(new Date())).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Main thread state: ").append(Looper.getMainLooper().getThread().getState()).append('\n');
        appendFreezeSkips(sb, freezeSkipCount, lastFreezeSkipDetail);

        // Memory 放在堆栈之前: Bugly 的异常信息字段有长度上限, 超了从尾部截断。堆栈在「堆栈」页签里
        // 有完整一份(setStackTrace), 这里被截无所谓; 内存数据只有这一处, 不能被截掉。
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sb.append("\n--- Memory ---\n");
        sb.append("Used: ").append(usedMB).append("MB / Max: ").append(maxMB).append("MB\n");

        sb.append("\n--- Main Thread Stack ---\n");
        for (StackTraceElement e : mainStack) {
            sb.append("  at ").append(e.toString()).append('\n');
        }

        return sb.toString();
    }

    private static String buildReport(StackTraceElement[] mainStack,
                                      int freezeSkipCount, String lastFreezeSkipDetail) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder(4096);

        sb.append("====== ANR DETECTED ======\n");
        sb.append("Time: ").append(sdf.format(new Date())).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Main thread state: ").append(Looper.getMainLooper().getThread().getState()).append('\n');
        appendFreezeSkips(sb, freezeSkipCount, lastFreezeSkipDetail);

        sb.append("\n--- Main Thread Stack ---\n");
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

    private static void appendFreezeSkips(StringBuilder sb, int freezeSkipCount, String lastFreezeSkipDetail) {
        if (freezeSkipCount <= 0) return;
        sb.append("Watchdog freeze-skipped rounds: ").append(freezeSkipCount);
        if (lastFreezeSkipDetail != null) {
            sb.append(" (last: ").append(lastFreezeSkipDetail).append(')');
        }
        sb.append('\n');
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
                ANRError error = new ANRError(toUpload);
                // 与实时上报一致：把文本里的主线程堆栈还原成真实栈，避免全部折叠到 uploadPendingLogs 一个 issue
                StackTraceElement[] parsed = parseMainThreadStack(toUpload);
                if (parsed.length > 0) {
                    error.setStackTrace(parsed);
                }
                CrashReport.postCatchedException(error);
                DiagnosticLogFile.clear(appContext);
            } catch (Throwable ignored) {
            }
        });
    }

    /** 把 "--- Main Thread Stack ---" 段落里的 "  at cls.method(File.java:12)" 还原成 StackTraceElement。 */
    private static StackTraceElement[] parseMainThreadStack(String block) {
        List<StackTraceElement> frames = new ArrayList<>();
        try {
            int start = block.indexOf("--- Main Thread Stack ---");
            if (start < 0) return new StackTraceElement[0];
            String[] lines = block.substring(start).split("\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (!line.startsWith("at ")) {
                    // 堆栈段结束（遇到下一个 "--- xxx ---" 或空行之后的非 at 行）就停
                    if (frames.isEmpty()) continue;
                    if (line.isEmpty()) continue;
                    break;
                }
                StackTraceElement e = parseFrame(line.substring(3));
                if (e != null) frames.add(e);
                if (frames.size() >= MAX_PARSED_FRAMES) break;
            }
        } catch (Throwable ignored) {
            return new StackTraceElement[0];
        }
        return frames.toArray(new StackTraceElement[0]);
    }

    private static StackTraceElement parseFrame(String s) {
        int paren = s.indexOf('(');
        if (paren <= 0 || !s.endsWith(")")) return null;
        String qualified = s.substring(0, paren);
        int lastDot = qualified.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == qualified.length() - 1) return null;
        String className = qualified.substring(0, lastDot);
        String methodName = qualified.substring(lastDot + 1);

        String location = s.substring(paren + 1, s.length() - 1);
        String fileName = null;
        // -2 是 StackTraceElement 对 native 帧的约定编码，保证还原后 toString 仍打印 "(Native Method)"
        int lineNumber = "Native Method".equals(location) ? -2 : -1;
        int colon = location.lastIndexOf(':');
        if (colon > 0) {
            fileName = location.substring(0, colon);
            try {
                lineNumber = Integer.parseInt(location.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                fileName = location;
            }
        } else if (!location.isEmpty() && location.indexOf(' ') < 0) {
            fileName = location;
        }
        return new StackTraceElement(className, methodName, fileName, lineNumber);
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
