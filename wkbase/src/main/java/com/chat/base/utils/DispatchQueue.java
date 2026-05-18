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
import android.os.Message;
import android.os.SystemClock;

import java.util.concurrent.CountDownLatch;

class DispatchQueue extends Thread {

    private volatile Handler handler;
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private long lastTaskTime;

    public DispatchQueue(String threadName) {
        this(threadName, true);
    }

    public DispatchQueue(String threadName, boolean start) {
        setName(threadName);
        if (start) {
            start();
        }
    }

    private void awaitReady() {
        try {
            readyLatch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void sendMessage(Message msg, int delay) {
        awaitReady();
        if (handler == null) return;
        if (delay <= 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delay);
        }
    }

    public void cancelRunnable(Runnable runnable) {
        awaitReady();
        if (handler != null) handler.removeCallbacks(runnable);
    }

    public void cancelRunnables(Runnable[] runnables) {
        awaitReady();
        if (handler == null) return;
        for (Runnable r : runnables) {
            handler.removeCallbacks(r);
        }
    }

    public boolean postRunnable(Runnable runnable) {
        lastTaskTime = SystemClock.elapsedRealtime();
        return postRunnable(runnable, 0);
    }

    public boolean postRunnable(Runnable runnable, long delay) {
        awaitReady();
        if (handler == null) return false;
        if (delay <= 0) {
            return handler.post(runnable);
        } else {
            return handler.postDelayed(runnable, delay);
        }
    }

    public void cleanupQueue() {
        awaitReady();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    public void handleMessage(Message inputMessage) {
    }

    public long getLastTaskTime() {
        return lastTaskTime;
    }

    public void recycle() {
        awaitReady();
        if (handler != null) handler.getLooper().quit();
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                DispatchQueue.this.handleMessage(msg);
            }
        };
        readyLatch.countDown();
        Looper.loop();
    }
}
