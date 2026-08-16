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

import android.util.Log;

import com.chat.base.BuildConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 数据库操作专用单线程调度器。
 * 所有 WKIM SDK 的 DB 操作（save/update/delete/query）统一在此线程串行执行，
 * 避免多线程并发争抢 SQLCipher 连接池导致连接饥饿或 ANR。
 */
public final class WKDbScheduler {

    private static final String TAG = "WKDbScheduler";

    /**
     * SQLCipher 连接池关闭后再取连接抛出的固定文案。SQLCipher 抛的是裸
     * {@link IllegalStateException}，没有专门的异常类型，只能按文案识别。
     */
    private static final String POOL_CLOSED_MSG = "connection pool has been closed";

    private static final Scheduler INSTANCE = Schedulers.from(
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "wk-db-worker");
                t.setDaemon(true);
                return t;
            })
    );

    private WKDbScheduler() {
    }

    /**
     * 提交一个数据库任务，串行执行。
     *
     * <p>登出 / token 失效（401）会关掉数据库，但此前已排队或延迟提交的任务仍会跑到，
     * 拿已关闭的连接池就抛 {@link IllegalStateException}。RxJava 的 ScheduledDirectTask
     * 会把它交给 {@code RxJavaPlugins.onError}，默认实现直接调用当前线程的
     * UncaughtExceptionHandler —— 进程当场崩在 wk-db-worker 上。
     *
     * <p>库都关了这些任务本来就没有意义，丢弃即可。只丢这一种，其余异常原样抛出，
     * 不掩盖真实问题。
     */
    public static Disposable submit(Runnable task) {
        return INSTANCE.scheduleDirect(guard(task));
    }

    /**
     * 延迟提交，语义同 {@link #submit(Runnable)}。
     */
    public static Disposable submit(Runnable task, long delay, TimeUnit unit) {
        return INSTANCE.scheduleDirect(guard(task), delay, unit);
    }

    private static Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (IllegalStateException e) {
                if (!isPoolClosed(e)) throw e;
                if (BuildConfig.DEBUG) Log.w(TAG, "db closed, drop pending task", e);
            }
        };
    }

    /**
     * @deprecated 直接拿 Scheduler 会绕过 {@link #submit(Runnable)} 的关库保护，
     * 新代码一律用 {@link #submit(Runnable)}。
     */
    @Deprecated
    public static Scheduler get() {
        return INSTANCE;
    }

    private static boolean isPoolClosed(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(POOL_CLOSED_MSG)) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
