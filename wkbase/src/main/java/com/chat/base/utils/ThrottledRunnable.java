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

import android.os.SystemClock;

/**
 * Fires {@code target} at most once per {@code intervalMs}. Extra invocations inside the
 * window are dropped (not queued). Use for feedback that should not spam — e.g. length-limit
 * toasts triggered on every keystroke.
 */
public final class ThrottledRunnable implements Runnable {
    private final long intervalMs;
    private final Runnable target;
    private long lastFiredMs = 0L;

    public ThrottledRunnable(long intervalMs, Runnable target) {
        this.intervalMs = intervalMs;
        this.target = target;
    }

    @Override
    public void run() {
        long now = SystemClock.uptimeMillis();
        if (now - lastFiredMs < intervalMs) return;
        lastFiredMs = now;
        target.run();
    }
}
