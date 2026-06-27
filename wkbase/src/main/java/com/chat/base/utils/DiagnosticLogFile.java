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
import android.content.Intent;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.chat.base.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;

/**
 * 统一的本地诊断日志文件。
 *
 * <p>三类写入共用同一份文件，用户从"关于 → 导出诊断日志"一键分享：
 * <ul>
 *     <li>{@code CrashHandler} / {@code ANRWatchdog} —— 崩溃栈, 同步写入(崩溃前必须落盘)</li>
 *     <li>{@link DiagSink} —— Space 串消息排查埋点, 异步写入(用户隐藏入口开启时才采)</li>
 * </ul>
 *
 * <p><b>Ring buffer (5MB)</b>: 文件超过 {@link #MAX_SIZE} 时保留最新的 {@link #KEEP_SIZE} 字节,
 * 避免冷启动早期日志被"超限即 delete"抹掉(老逻辑的硬伤)。
 */
public final class DiagnosticLogFile {

    private static final String TAG = "DiagnosticLog";
    private static final String FILE_NAME = "diagnostic.log";

    /** Ring buffer 上限: 5 MB. Crash 栈通常 2-10KB / 次, 5MB 容量足够装数百次崩溃 + 诊断采集. */
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    /** 超限后保留的尾段大小: 2.5 MB (= MAX_SIZE / 2). */
    private static final long KEEP_SIZE = MAX_SIZE / 2;

    private DiagnosticLogFile() {
    }

    /**
     * 追加原始文本到日志文件. 不加时间戳前缀(调用方自行格式化).
     *
     * <p>线程安全: {@code synchronized} + {@link FileOutputStream}, 多线程写不会撕裂.
     * Crash 路径(同步调用)与 {@link DiagSink} writer 线程(异步调用)共用此方法.
     */
    public static synchronized void append(Context context, String content) {
        if (context == null || content == null) return;
        try {
            File file = getFile(context);
            rotateIfOversized(file);
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 文件超 {@link #MAX_SIZE} 时保留最新 {@link #KEEP_SIZE} 字节.
     *
     * <p>实现: 读尾段到临时文件再 rename 覆盖, 避免长时间持有写锁. rename 失败时降级为
     * 删整个文件(老行为), 保证文件不会无限增长.
     */
    private static void rotateIfOversized(File file) {
        try {
            if (!file.exists() || file.length() <= MAX_SIZE) return;
            File tmp = new File(file.getParentFile(), FILE_NAME + ".rotate.tmp");
            if (tmp.exists()) tmp.delete();
            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 FileOutputStream out = new FileOutputStream(tmp)) {
                long start = Math.max(0L, file.length() - KEEP_SIZE);
                raf.seek(start);
                // 跳过半行: 找下一个 '\n' 之后开始保留, 避免日志中间被切断不可读
                int b;
                while ((b = raf.read()) != -1 && b != '\n') { /* skip */ }
                String banner = "[ROTATE] ring-buffer truncated " + file.length()
                        + " -> keep " + KEEP_SIZE + " bytes\n";
                out.write(banner.getBytes("UTF-8"));
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = raf.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            if (!tmp.renameTo(file)) {
                // rename 失败兜底
                file.delete();
                tmp.delete();
            }
        } catch (Throwable e) {
            Log.w(TAG, "rotate failed: " + e.getMessage());
            try {
                file.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String read(Context context) {
        if (context == null) return "";
        try {
            File file = getFile(context);
            if (!file.exists() || file.length() == 0) return "";
            StringBuilder sb = new StringBuilder((int) Math.min(file.length(), Integer.MAX_VALUE));
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                char[] buf = new char[4096];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
            }
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
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.diagnostic_no_logs));
            return;
        }
        try {
            String authority = context.getPackageName() + ".fileProvider";
            android.net.Uri uri = FileProvider.getUriForFile(context, authority, file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_diag_log));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.diagnostic_share_title)));
        } catch (Throwable e) {
            Log.e(TAG, "share failed", e);
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.diagnostic_share_failed));
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
