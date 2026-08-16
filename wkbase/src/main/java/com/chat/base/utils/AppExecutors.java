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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  P-11 · 全仓统一 Executor 入口。
 * <p>
 * 取代散落在各处的 {@code new Thread(...).start()}。好处：
 * <ul>
 *   <li>线程有名字（app-io-N / app-bg-N / app-db），profiling / ANR 栈里可识别；</li>
 *   <li>线程优先级统一降到 {@link Thread#NORM_PRIORITY}-1，避免与主线程抢 CPU；</li>
 *   <li>无 unbounded 爆发：所有任务走有界池，避免短时 spike 造成 schedule 抖动。</li>
 * </ul>
 * <p>
 * <b>语义：</b>
 * <ul>
 *   <li>{@link #io()} —— I/O 密集（磁盘、网络、SharedPreferences、Bitmap decode、
 *   Glide 预热等）。阻塞型工作用这个池。</li>
 *   <li>{@link #background()} —— CPU 密集（JSON parse、图形运算、差分等）。
 *   池大小 = CPU 核心数，避免过度并发造成 context switch。</li>
 *   <li>{@link #db()} —— 单线程顺序化 DB 任务（非 Rx 场景；Rx 场景请继续走
 *   {@link WKDbScheduler#submit(Runnable)}）。</li>
 *   <li>{@link #mainThread(Runnable)} / {@link #postDelayed(Runnable, long)} ——
 *   投递到主线程 Looper；等价于 {@code AndroidUtilities.runOnUIThread}，但不依赖它。</li>
 * </ul>
 * <p>
 * <b>禁止再直接 new Thread()。</b> 如需特殊场景（如 {@code new Thread(r, name)} 作为
 * Executor 的 ThreadFactory，或 Crash 路径上的 Looper 线程），请把调用限定到
 * {@code scripts/check-no-new-thread.sh} 的白名单。
 */
public final class AppExecutors {

    private static final int CPU_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors());

    /** I/O 密集：允许多并发，池规模 = 2 × CPU（至少 4）。 */
    private static final ExecutorService IO = Executors.newFixedThreadPool(
            Math.max(4, CPU_COUNT * 2),
            namedFactory("app-io", Thread.NORM_PRIORITY - 1));

    /** CPU 密集：池规模 = CPU。 */
    private static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(
            CPU_COUNT,
            namedFactory("app-bg", Thread.NORM_PRIORITY));

    /** 单线程顺序化 DB 任务（非 Rx 场景）。 */
    private static final ExecutorService DB = Executors.newSingleThreadExecutor(
            namedFactory("app-db", Thread.NORM_PRIORITY));

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    /** I/O 密集任务池。 */
    public static ExecutorService io() {
        return IO;
    }

    /** CPU 密集任务池。 */
    public static ExecutorService background() {
        return BACKGROUND;
    }

    /** 单线程 DB 任务池（非 Rx）。 */
    public static ExecutorService db() {
        return DB;
    }

    /** 投递到主线程 Looper。 */
    public static void mainThread(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    /** 主线程延迟投递。 */
    public static void postDelayed(@NonNull Runnable r, long delayMillis) {
        MAIN.postDelayed(r, delayMillis);
    }

    private static ThreadFactory namedFactory(String prefix, int priority) {
        final AtomicInteger idx = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + idx.incrementAndGet());
            t.setDaemon(true);
            try {
                t.setPriority(priority);
            } catch (Throwable ignored) {
            }
            return t;
        };
    }
}
