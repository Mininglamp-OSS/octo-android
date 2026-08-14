package com.xinbida.wukongim.utils;

import android.os.SystemClock;
import android.util.Log;

import com.xinbida.wukongim.BuildConfig;

/**
 * 临时诊断工具：给会话同步路径打 Java 堆水位，定位线上 OOM（256MB 顶格）。
 *
 * <p>只在 debug 构建里输出，release 每个方法首行就 return。
 *
 * <p>关键的不是各阶段边界的水位，而是 {@link #begin} / {@link #end} 之间的 <b>峰值</b>——
 * OOM 发生在 {@code queryWithMsgIds} 构建列表的<b>途中</b>，边界采样看不到。所以 begin 会拉起
 * 一个 50ms 采样的守护线程记录 peak。
 *
 * <p>静态状态只服务「同一时刻一次会话同步」这一个场景（ConversationManager 的 syncInFlight
 * 保证单飞）。别的线程调 mark 会打乱 delta，因此每行都带线程名以便识别。
 *
 * <p>OOM 闭环后整类删除。
 */
public final class WKHeapProbe {
    public static final String TAG = "WKHeapProbe";

    private static final long SAMPLE_INTERVAL_MS = 50L;
    private static final long MB = 1024L * 1024L;

    private static volatile boolean sampling;
    private static volatile long peakUsed;
    private static long baseUsed;
    private static long lastUsed;
    private static long beginAt;
    private static String scopeName;

    private WKHeapProbe() {
    }

    private static long used() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String mb(long bytes) {
        // 小账号上增量可能只有几十 KB，整数 MB 分辨率会把一切显示成 0，必须留小数
        return String.format(java.util.Locale.US, "%.2fMB", bytes / (double) MB);
    }

    private static String signedMb(long bytes) {
        return (bytes >= 0 ? "+" : "-")
                + String.format(java.util.Locale.US, "%.2fMB", Math.abs(bytes) / (double) MB);
    }

    public static void begin(String scope) {
        if (!BuildConfig.DEBUG) return;
        if (sampling) {
            Log.w(TAG, "begin(" + scope + ") while '" + scopeName + "' still active, force-closing");
            end();
        }
        scopeName = scope;
        beginAt = SystemClock.elapsedRealtime();
        baseUsed = used();
        lastUsed = baseUsed;
        peakUsed = baseUsed;
        sampling = true;
        Thread t = new Thread(() -> {
            // 兜底：万一 end() 没被调到（回调没回来），别让采样线程一直转
            long deadline = SystemClock.elapsedRealtime() + 120_000L;
            while (sampling && SystemClock.elapsedRealtime() < deadline) {
                long u = used();
                if (u > peakUsed) peakUsed = u;
                SystemClock.sleep(SAMPLE_INTERVAL_MS);
            }
        }, "WKHeapProbe-sampler");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        Log.d(TAG, "── " + scope + " BEGIN used=" + mb(baseUsed)
                + " footprint=" + mb(Runtime.getRuntime().totalMemory())
                + " max=" + mb(Runtime.getRuntime().maxMemory()));
    }

    public static void mark(String stage) {
        mark(stage, null);
    }

    /**
     * 与 scope 无关的独立水位快照，用于 begin/end 之外的时点（例如 HTTP 响应体解析前后）。
     * 不影响 Δstage 的基准。
     */
    public static void snapshot(String label) {
        if (!BuildConfig.DEBUG) return;
        long u = used();
        if (sampling && u > peakUsed) peakUsed = u;
        Log.d(TAG, "·  " + label + " used=" + mb(u)
                + "  [" + Thread.currentThread().getName() + "]");
    }

    /** 取当前已用堆，供调用方自己夹住一段代码测增量。release 返回 0。 */
    public static long usedNow() {
        return BuildConfig.DEBUG ? used() : 0L;
    }

    private static byte[][] ballast;

    /**
     * 压舱：占住 mb 兆堆空间并一直持有，把本机的可用堆压到和线上大账号等效的紧张程度，
     * 用来在自己的账号上<b>确定性复现</b> OOM，并量化每一处优化到底买到多少 MB 余量。
     *
     * <p>用 1MB 一块而不是一整块，是为了落在普通 AllocSpace 而非 LOS，更接近真实数据的形态。
     *
     * <p><b>尽力而为</b>：拿不动了就停在那里，已拿到的继续持有，并把实际拿到的量打出来。
     * 绝不能让压舱自己抛 OOM —— 那样崩溃栈会停在本方法上，看着像被测代码崩了，
     * 其实是道具崩了，整场实验的结论都是错的。「实际拿到 N MB」这个数本身也是有用的信息：
     * 它等于「当前时刻这台机器还剩多少可用堆」。
     *
     * <p>幂等：只分配一次。debug-only，问题闭环后删。
     */
    public static void ensureBallast(int mb) {
        if (!BuildConfig.DEBUG || mb <= 0 || ballast != null) return;
        java.util.List<byte[]> blocks = new java.util.ArrayList<>(mb);
        int got = 0;
        try {
            for (int i = 0; i < mb; i++) {
                blocks.add(new byte[(int) MB]);
                got++;
            }
        } catch (OutOfMemoryError ignored) {
            // 拿不到就停，不往上抛
        }
        ballast = blocks.toArray(new byte[0][]);
        long max = Runtime.getRuntime().maxMemory();
        long u = used();
        Log.d(TAG, "★ ballast requested " + mb + "MB, actually got " + got + "MB"
                + " → used=" + mb(u)
                + " max=" + mb(max)
                + " 剩余可用≈" + mb(max - u)
                + (got < mb ? "  ⚠️ 没拿满，说明这台机器此刻只剩这么多" : ""));
    }

    /** 报告一段被 {@link #usedNow()} 夹住的代码的堆增量。 */
    public static void span(String label, String detail, long before, long after) {
        if (!BuildConfig.DEBUG) return;
        if (sampling && after > peakUsed) peakUsed = after;
        Log.d(TAG, "·  " + label + " " + detail
                + " used " + mb(before) + " → " + mb(after)
                + " Δ=" + signedMb(after - before)
                + "  [" + Thread.currentThread().getName() + "]");
    }

    public static void mark(String stage, String detail) {
        if (!BuildConfig.DEBUG) return;
        long u = used();
        if (u > peakUsed) peakUsed = u;
        Log.d(TAG, "   " + stage
                + " used=" + mb(u)
                + " Δstage=" + signedMb(u - lastUsed)
                + " Δbase=" + signedMb(u - baseUsed)
                + (detail == null ? "" : "  " + detail)
                + "  [" + Thread.currentThread().getName() + "]");
        lastUsed = u;
    }

    public static void end() {
        if (!BuildConfig.DEBUG) return;
        if (!sampling) return;
        sampling = false;
        long u = used();
        Log.d(TAG, "── " + scopeName + " END used=" + mb(u)
                + " peak=" + mb(peakUsed)
                + " peakΔbase=" + signedMb(peakUsed - baseUsed)
                + " retained=" + signedMb(u - baseUsed)
                + " +" + (SystemClock.elapsedRealtime() - beginAt) + "ms");
    }
}
