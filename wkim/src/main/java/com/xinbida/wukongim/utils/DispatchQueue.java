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

package com.xinbida.wukongim.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;

/**
 * Clean-room implementation of a serial background task queue.
 * Built on Android's {@link HandlerThread} API — no code was copied from
 * any GPL or proprietary source. This replaces a previous GPL-licensed file.
 * Provides a simple API to post, delay, and cancel runnables on a dedicated thread.
 */
class DispatchQueue {

    private final HandlerThread thread;
    private final Handler handler;
    private volatile long lastTaskTime;

    public DispatchQueue(String threadName) {
        thread = new HandlerThread(threadName);
        thread.start();
        handler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                DispatchQueue.this.handleMessage(msg);
            }
        };
    }

    public DispatchQueue(String threadName, boolean start) {
        thread = new HandlerThread(threadName);
        if (start) {
            thread.start();
        }
        handler = start ? new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                DispatchQueue.this.handleMessage(msg);
            }
        } : null;
    }

    public void sendMessage(Message msg, int delay) {
        if (handler == null) return;
        if (delay <= 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delay);
        }
    }

    public void cancelRunnable(Runnable runnable) {
        if (handler != null) handler.removeCallbacks(runnable);
    }

    public void cancelRunnables(Runnable[] runnables) {
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
        if (handler == null) return false;
        return delay <= 0 ? handler.post(runnable) : handler.postDelayed(runnable, delay);
    }

    public void cleanupQueue() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    public void handleMessage(Message inputMessage) {
    }

    public long getLastTaskTime() {
        return lastTaskTime;
    }

    public void recycle() {
        thread.quitSafely();
    }

    public void setPriority(int priority) {
        thread.setPriority(priority);
    }
}
