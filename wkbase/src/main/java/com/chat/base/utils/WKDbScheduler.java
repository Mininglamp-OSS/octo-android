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
