/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.xinbida.wukongim.diag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SDK 内部诊断日志桥. wkim 模块无法直接依赖 wkbase 的 DiagSink(依赖方向相反),
 * 通过此 install-pattern 让 wkbase 在 Application 启动时注入实现.
 *
 * <p>未注入时所有 write 调用是 no-op, 不会抛异常. wkim 内的 conv-sync / membership
 * 等关键状态变更点直接调用 {@link #write(String, String)} 上报, 无需 import wkbase.
 *
 * <p>用法:
 * <pre>{@code
 * // wkbase 中, Application 启动时:
 * WKDiagWriter.install((tag, msg) -> DiagSink.write(tag, msg));
 *
 * // wkim 内任意位置:
 * WKDiagWriter.write("MEMBERSHIP", "action=apply size=23");
 * }</pre>
 */
public final class WKDiagWriter {

    public interface Writer {
        void write(@NonNull String tag, @Nullable String message);
    }

    private static volatile Writer IMPL;

    private WKDiagWriter() {
    }

    /** wkbase 在 Application 启动时调一次. 幂等. */
    public static void install(@Nullable Writer writer) {
        IMPL = writer;
    }

    /** wkim 内任意位置写日志. 未 install 时 no-op, 永不抛异常. */
    public static void write(@NonNull String tag, @Nullable String message) {
        Writer w = IMPL;
        if (w == null) return;
        try {
            w.write(tag, message);
        } catch (Throwable ignored) {
        }
    }
}
