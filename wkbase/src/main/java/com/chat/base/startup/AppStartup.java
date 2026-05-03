package com.chat.base.startup;

import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.NonNull;

import com.chat.base.utils.AppExecutors;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * App 启动 Initializer 分阶段调度器（YUJ-295 / P-04）。
 *
 * <h2>三阶段</h2>
 * <ul>
 *   <li><b>Phase-A（同步必须，&lt;20ms）</b>：在 {@code Application.onCreate} /
 *       {@code TSApplication.initAll()} 主线程内直接调用（语言、API、主题、
 *       WKBaseApplication 基础单例）。此工具类 <b>不</b> 提供 Phase-A API——
 *       本来就是直写代码，不需要调度。</li>
 *   <li><b>Phase-B（异步，首屏不依赖）</b>：通过 {@link #postPhaseB} 交给
 *       {@link AppExecutors#io()} 立即执行；Bugly、RLottie、push token 握手、
 *       WKIM 初始化 + 监听都归此阶段。</li>
 *   <li><b>Phase-C（idle 后）</b>：通过 {@link #postPhaseC} 挂到主线程
 *       {@link MessageQueue.IdleHandler}，首帧渲染完进入 idle 时再投递到
 *       {@link AppExecutors#io()}；如果在 {@link #PHASE_C_FALLBACK_MS} 内主线程
 *       一直没 idle，则强制投递兜底，避免任务无限期饿死。</li>
 * </ul>
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li>每个阶段任务都包在 {@link Trace} 区段中，方便 {@code systrace} /
 *       perfetto 里定位出处：<code>app-startup:&lt;phase&gt;:&lt;label&gt;</code>。</li>
 *   <li>任务抛出的异常不会让启动链崩溃——捕获并打印到 {@link Log#e}，按「启动路径
 *       任务不应互相影响」对齐旧 {@code new Thread().start()} fire-and-forget 语义。</li>
 *   <li>所有线程走 {@link AppExecutors}，符合 YUJ-288（P-11）禁用裸
 *       {@code new Thread} 的约束；{@code scripts/check-no-new-thread.sh} 本身不会命中本文件。</li>
 *   <li>不依赖 {@code androidx.startup} 的 {@code InitializationProvider}——那套是
 *       在 {@code Application.onCreate} 之前同步跑的，与「推迟到首帧后」完全相反；
 *       本类只处理「主进程 Application 内的分阶段调度」。</li>
 * </ul>
 */
public final class AppStartup {

    private static final String TAG = "AppStartup";

    /** Phase-C 兜底超时：idle 迟迟不来则强制执行。 */
    private static final long PHASE_C_FALLBACK_MS = 5_000L;

    /** Phase-C 任务全部投递完毕后的标记；供测试或诊断使用。 */
    private static final AtomicBoolean IDLE_HANDLER_INSTALLED = new AtomicBoolean(false);

    private AppStartup() {
    }

    /**
     * 立即把 {@code task} 投递到 {@link AppExecutors#io()}（Phase-B）。
     * 调用线程通常为 {@code Application.onCreate} 所在的主线程，但不强制。
     *
     * @param label 诊断标签，会写入 systrace 区段与日志
     * @param task  后台任务
     */
    public static void postPhaseB(@NonNull String label, @NonNull Runnable task) {
        AppExecutors.io().execute(() -> runWithTrace("B", label, task));
    }

    /**
     * 把 {@code task} 推迟到首帧后（主线程 idle）再投递到 {@link AppExecutors#io()}。
     * 如 {@link #PHASE_C_FALLBACK_MS} 内主线程未 idle，则强制投递兜底，
     * 保证任务最终一定会被执行。
     *
     * <p>可在任意线程调用；底层会把 IdleHandler 注册到主线程 Looper。
     *
     * @param label 诊断标签，会写入 systrace 区段与日志
     * @param task  idle 后要在 IO 线程执行的任务
     */
    public static void postPhaseC(@NonNull String label, @NonNull Runnable task) {
        final Runnable once = new OneShot(label, task);

        // 主线程 idle 触发：首帧渲染完、消息队列清空的第一个空档。
        Looper main = Looper.getMainLooper();
        Runnable installIdle = () -> {
            final MessageQueue queue = Looper.myQueue();
            queue.addIdleHandler(() -> {
                AppExecutors.io().execute(() -> runWithTrace("C-idle", label, once));
                return false; // one-shot
            });
        };
        if (Looper.myLooper() == main) {
            installIdle.run();
        } else {
            new android.os.Handler(main).post(installIdle);
        }

        // 兜底：PHASE_C_FALLBACK_MS 之后不管 idle 与否都投递。
        AppExecutors.postDelayed(
                () -> AppExecutors.io().execute(() -> runWithTrace("C-fallback", label, once)),
                PHASE_C_FALLBACK_MS);

        IDLE_HANDLER_INSTALLED.compareAndSet(false, true);
    }

    /**
     * 测试/诊断用：Phase-C 是否已经注册过 IdleHandler。
     */
    public static boolean isIdleHandlerInstalled() {
        return IDLE_HANDLER_INSTALLED.get();
    }

    private static void runWithTrace(String phase, String label, Runnable task) {
        final String section = "app-startup:" + phase + ":" + label;
        final long t0 = SystemClock.elapsedRealtime();
        try {
            Trace.beginSection(section);
            task.run();
        } catch (Throwable t) {
            // fire-and-forget 语义对齐旧 `new Thread().start()`：单个任务失败不影响其他阶段。
            Log.e(TAG, section + " failed", t);
        } finally {
            Trace.endSection();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, section + " done in " + (SystemClock.elapsedRealtime() - t0) + "ms");
            }
        }
    }

    /**
     * 保证同一个任务只会真正跑一次——idle 和 fallback 谁先触发，另一个就会被吞掉。
     */
    private static final class OneShot implements Runnable {
        private final String label;
        private final Runnable delegate;
        private final AtomicBoolean fired = new AtomicBoolean(false);

        OneShot(String label, Runnable delegate) {
            this.label = label;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            if (fired.compareAndSet(false, true)) {
                delegate.run();
            } else if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "phase-C already fired, skipping duplicate for label=" + label);
            }
        }
    }
}
