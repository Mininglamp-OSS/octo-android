package com.xinbida.wukongim.message.timer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerManager {
    private static volatile TimerManager instance;
    private final Handler handler;
    private final Map<String, Runnable> taskMap;
    private volatile ExecutorService executorService;  // 改为 volatile
    private final Object executorLock = new Object();  // 添加锁对象

    private TimerManager() {
        handler = new Handler(Looper.getMainLooper());
        taskMap = new ConcurrentHashMap<>();
        initExecutorService();
    }
    public static TimerManager getInstance() {
        if (instance == null) {
            synchronized (TimerManager.class) {
                if (instance == null) {
                    instance = new TimerManager();
                }
            }
        }
        return instance;
    }
    // 初始化线程池
    private void initExecutorService() {
        synchronized (executorLock) {
            if (executorService == null || executorService.isShutdown()) {
                executorService = new ThreadPoolExecutor(
                        3,                      // 核心线程数
                        5,                      // 最大线程数
                        60L,                    // 空闲线程存活时间
                        TimeUnit.SECONDS,       // 时间单位
                        new LinkedBlockingQueue<>(),  // 任务队列
                        new ThreadFactory() {
                            private final AtomicInteger count = new AtomicInteger(1);
                            @Override
                            public Thread newThread(Runnable r) {
                                Thread thread = new Thread(r);
                                thread.setName("TimerTask-" + count.getAndIncrement());
                                return thread;
                            }
                        },
                        new RejectedExecutionHandler() {
                            @Override
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                // 如果线程池已关闭，尝试重新初始化
                                if (executor.isShutdown()) {
                                    initExecutorService();
                                    // 重新提交任务
                                    try {
                                        executorService.execute(r);
                                    } catch (Exception e) {
                                        Log.e("TimerManager", "Task execution failed after retry", e);
                                    }
                                } else {
                                    // 如果是其他原因导致的拒绝，记录日志
                                    Log.e("TimerManager", "Task rejected: " + r.toString());
                                }
                            }
                        }
                );
            }
        }
    }

    /**
     * 添加定时任务
     */
    public void addTask(String taskId, final Runnable task, long delayMillis, final long periodMillis) {
        removeTask(taskId);

        Runnable wrappedTask = new Runnable() {
            @Override
            public void run() {
                // 检查线程池状态
                synchronized (executorLock) {
                    if (executorService == null || executorService.isShutdown()) {
                        initExecutorService();
                    }

                    try {
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    task.run();
                                } catch (Exception e) {
                                    Log.e("TimerManager", "Task execution failed", e);
                                }
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        Log.e("TimerManager", "Task rejected", e);
                        // 如果任务被拒绝，可以选择重试或其他处理方式
                        initExecutorService();
                        try {
                            executorService.execute(task);
                        } catch (Exception ex) {
                            Log.e("TimerManager", "Task retry failed", ex);
                        }
                    }
                }

                // 继续安排下一次执行
                if (!Thread.currentThread().isInterrupted()) {
                    handler.postDelayed(this, periodMillis);
                }
            }
        };

        taskMap.put(taskId, wrappedTask);
        handler.postDelayed(wrappedTask, delayMillis);
    }

    /**
     * 移除定时任务
     */
    public void removeTask(String taskId) {
        Runnable task = taskMap.remove(taskId);
        if (task != null) {
            handler.removeCallbacks(task);
        }
    }

    /**
     * 优雅关闭
     */
    public void shutdown() {
        // 先移除所有定时任务
        for (Runnable task : taskMap.values()) {
            handler.removeCallbacks(task);
        }
        taskMap.clear();

        // 关闭线程池
        synchronized (executorLock) {
            if (executorService != null && !executorService.isShutdown()) {
                try {
                    // 尝试优雅关闭
                    executorService.shutdown();
                    // 等待任务完成
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        // 如果等待超时，强制关闭
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    // 如果等待被中断，强制关闭
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            executorService = null;
        }
    }

    /**
     * 重启定时器管理器
     */
    public void restart() {
        shutdown();
        initExecutorService();
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isTaskRunning(String taskId) {
        return taskMap.containsKey(taskId);
    }
}
