/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Space 串消息排查专用诊断日志异步写入器.
 *
 * <p><b>设计目标</b>:
 * <ul>
 *   <li><b>业务零侵入</b>: 埋点点位调 {@link #write(String, String)}, 内部 gate + 异步.</li>
 *   <li><b>默认关闭</b>: release/debug 都默认关闭, 用户通过隐藏入口(头像 5 次点击)显式开启.</li>
 *   <li><b>自动失效</b>: 开启后 TTL(默认 2 小时)到期自动关, 避免遗忘.</li>
 *   <li><b>异步写入</b>: 单线程 {@link HandlerThread}, 业务线程仅 post Runnable, 无 IO.</li>
 *   <li><b>复用导出</b>: 写到 {@link DiagnosticLogFile} 同一个文件, 用户从"关于→导出诊断日志"导出.</li>
 * </ul>
 *
 * <p><b>状态机</b>:
 * <ol>
 *   <li>{@link #init(Context)} —— App 启动调一次. 读 SP {@code enabled_until_ms}, 若仍在 TTL 内则
 *       恢复上次会话的诊断模式(用户开启后即使重启 app 也继续采到 TTL 截止).</li>
 *   <li>{@link #enable(Context, long)} —— 用户点头像 5 次后确认开启. 写 SP + 内存状态.</li>
 *   <li>{@link #disable(Context)} —— 用户主动关闭. 清 SP + 内存状态.</li>
 *   <li>{@link #isEnabled()} —— 任意线程检查. TTL 到期会自动 lazy-disable.</li>
 * </ol>
 *
 * <p><b>排查结束后移除步骤</b>:
 * <ol>
 *   <li>删除调用 {@link #write(String, String)} 的所有埋点位置(grep "DiagSink.write")</li>
 *   <li>删除本文件 {@code DiagSink.java}</li>
 *   <li>删除 {@code MyFragment} 中的 5 次点击检测代码</li>
 * </ol>
 */
public final class DiagSink {

    private static final String SP_NAME = "diag_sink";
    private static final String SP_KEY_ENABLED_UNTIL = "enabled_until_ms";

    /** 默认 TTL: 2 小时. 足够用户配合排查走完一遍复现场景, 又不会忘了关. */
    public static final long DEFAULT_TTL_MS = 2L * 60 * 60 * 1000;

    private static final AtomicBoolean enabled = new AtomicBoolean(false);
    private static final AtomicLong enabledUntilMs = new AtomicLong(0L);
    private static volatile Handler writer;
    private static volatile Context appCtx;

    /** 与 ANR/Crash 既有格式对齐, 含毫秒. ThreadLocal 避免多线程 format 竞争. */
    private static final ThreadLocal<SimpleDateFormat> TS_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        }
    };

    private DiagSink() {
    }

    /**
     * App 启动时调一次. 必须在任何 {@link #write} 之前完成(否则 write 直接 no-op 不报错).
     *
     * <p>读 SP {@code enabled_until_ms}: 若仍在 TTL 内 → 恢复 {@code enabled=true}, 让用户
     * 上次开启的诊断模式跨越重启依然有效. 这样用户开启后可以重启 app 复现问题, 不需要
     * 每次重新开启.
     */
    public static synchronized void init(@NonNull Context ctx) {
        if (writer != null) return;
        appCtx = ctx.getApplicationContext();
        HandlerThread thread = new HandlerThread("DiagSinkWriter", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        writer = new Handler(thread.getLooper());
        try {
            SharedPreferences sp = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            long until = sp.getLong(SP_KEY_ENABLED_UNTIL, 0L);
            if (until > 0 && System.currentTimeMillis() < until) {
                enabled.set(true);
                enabledUntilMs.set(until);
            } else if (until > 0) {
                // TTL 已过期, 顺手清掉脏数据
                sp.edit().remove(SP_KEY_ENABLED_UNTIL).apply();
            }
        } catch (Throwable ignored) {
            // SP 不可读时静默, 默认关闭
        }
    }

    /**
     * 用户点击开启诊断采集.
     *
     * @param ctx   任意 Context, 用来访问 SP
     * @param ttlMs 自动关闭时长(毫秒). 传 ≤ 0 时使用 {@link #DEFAULT_TTL_MS}.
     */
    public static void enable(@NonNull Context ctx, long ttlMs) {
        long duration = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
        long until = System.currentTimeMillis() + duration;
        enabled.set(true);
        enabledUntilMs.set(until);
        try {
            ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(SP_KEY_ENABLED_UNTIL, until)
                    .apply();
        } catch (Throwable ignored) {
            // SP 写失败仅影响跨进程恢复, 当前进程仍正常采集
        }
    }

    /** 用户主动关闭采集. */
    public static void disable(@NonNull Context ctx) {
        enabled.set(false);
        enabledUntilMs.set(0L);
        try {
            ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(SP_KEY_ENABLED_UNTIL)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** 当前是否启用. TTL 过期会自动转为 false. 任意线程可调. */
    @AnyThread
    public static boolean isEnabled() {
        if (!enabled.get()) return false;
        long until = enabledUntilMs.get();
        if (until > 0 && System.currentTimeMillis() > until) {
            enabled.set(false);
            // 注意: 这里不清 SP, 让下次 init() 走过期清理路径(避免在 isEnabled 这个高频路径上做 IO)
            return false;
        }
        return true;
    }

    /** TTL 截止时间戳(epoch ms). 0 表示未启用. UI 用来计算"还剩多久". */
    public static long getEnabledUntilMs() {
        return enabledUntilMs.get();
    }

    /**
     * 写一行诊断日志. 任意线程调用, 立即返回(只 post 到 writer 线程), 不做 IO.
     *
     * <p>未启用时直接 return, 是单次 {@code AtomicBoolean.get()} + 可能的过期检查, 纳秒级开销.
     *
     * @param tag     类别标签, 建议常量化(SF / SF-MSG / BOOT / SPACE-SWITCH / CONV-SYNC / MEMBERSHIP / WKIM)
     * @param message 日志正文(不要含换行, 内部会加 {@code \n})
     */
    @AnyThread
    public static void write(@NonNull String tag, @Nullable String message) {
        if (message == null) return;
        if (!isEnabled()) return;
        Handler h = writer;
        Context ctx = appCtx;
        if (h == null || ctx == null) return;
        long ts = System.currentTimeMillis();
        h.post(() -> {
            try {
                SimpleDateFormat fmt = TS_FMT.get();
                String stamp = (fmt != null) ? fmt.format(new Date(ts)) : Long.toString(ts);
                String line = "[" + stamp + "] [" + tag + "] " + message + "\n";
                DiagnosticLogFile.append(ctx, line);
            } catch (Throwable ignored) {
                // 写文件失败不传播
            }
        });
    }
}
