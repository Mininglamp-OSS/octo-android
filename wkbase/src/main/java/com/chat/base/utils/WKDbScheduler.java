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

import java.util.concurrent.Executors;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 数据库操作专用单线程调度器。
 * 所有 WKIM SDK 的 DB 操作（save/update/delete/query）统一在此线程串行执行，
 * 避免多线程并发争抢 SQLCipher 连接池导致连接饥饿或 ANR。
 */
public final class WKDbScheduler {

    private static final Scheduler INSTANCE = Schedulers.from(
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "wk-db-worker");
                t.setDaemon(true);
                return t;
            })
    );

    private WKDbScheduler() {
    }

    public static Scheduler get() {
        return INSTANCE;
    }
}
