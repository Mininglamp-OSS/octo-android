package com.xinbida.wukongim.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.UiThread;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;

public class DispatchQueuePool {

    private final LinkedList<DispatchQueue> queues = new LinkedList<>();
    private final HashMap<DispatchQueue, Integer> busyQueuesMap = new HashMap<>();
    private final LinkedList<DispatchQueue> busyQueues = new LinkedList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int maxCount;
    private int createdCount;
    private final int guid;
    private int totalTasksCount;
    private boolean cleanupScheduled;
    private final Object lock = new Object();

    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (!queues.isEmpty()) {
                    long currentTime = SystemClock.elapsedRealtime();
                    for (int a = 0, N = queues.size(); a < N; a++) {
                        DispatchQueue queue = queues.get(a);
                        if (queue != null && queue.getLastTaskTime() < currentTime - 30000) {
                            queue.recycle();
                            queues.remove(a);
                            createdCount--;
                            a--;
                            N--;
                        }
                    }
                }
                if (!queues.isEmpty() || !busyQueues.isEmpty()) {
                    mainHandler.postDelayed(this, 30000);
                    cleanupScheduled = true;
                } else {
                    cleanupScheduled = false;
                }
            }
        }
    };

    public DispatchQueuePool(int count) {
        maxCount = count;
        guid = new SecureRandom().nextInt();
    }

    @UiThread
    public void execute(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        DispatchQueue queue;
        synchronized (lock) {
            try {
                if (!busyQueues.isEmpty() && (totalTasksCount / 2 <= busyQueues.size() || queues.isEmpty() && createdCount >= maxCount)) {
                    queue = busyQueues.remove(0);
                } else if (queues.isEmpty()) {
                    queue = new DispatchQueue("DispatchQueuePool" + guid + "_" + new SecureRandom().nextInt());
                    queue.setPriority(Thread.MAX_PRIORITY);
                    createdCount++;
                } else {
                    queue = queues.remove(0);
                }

                if (!cleanupScheduled) {
                    mainHandler.postDelayed(cleanupRunnable, 30000);
                    cleanupScheduled = true;
                }

                totalTasksCount++;
                if (queue != null) {
                    busyQueues.add(queue);
                    Integer count = busyQueuesMap.get(queue);
                    if (count == null) {
                        count = 0;
                    }
                    busyQueuesMap.put(queue, count + 1);
                }
            } catch (Exception e) {
                queue = new DispatchQueue("DispatchQueuePool" + guid + "_" + new SecureRandom().nextInt());
                queue.setPriority(Thread.MAX_PRIORITY);
                createdCount++;
                busyQueues.add(queue);
                busyQueuesMap.put(queue, 1);
            }
        }

        if (queue != null) {
            final DispatchQueue finalQueue = queue;
            queue.postRunnable(() -> {
                try {
                    runnable.run();
                } finally {
                    mainHandler.post(() -> {
                        synchronized (lock) {
                            totalTasksCount--;
                            Integer i = busyQueuesMap.get(finalQueue);
                            if (i != null) {
                                int remainingTasksCount = i - 1;
                                if (remainingTasksCount == 0) {
                                    busyQueuesMap.remove(finalQueue);
                                    busyQueues.remove(finalQueue);
                                    queues.add(finalQueue);
                                } else {
                                    busyQueuesMap.put(finalQueue, remainingTasksCount);
                                }
                            }
                        }
                    });
                }
            });
        }
    }
}