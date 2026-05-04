package com.chat.uikit.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * YUJ-330 Jerry review 2026-05-04 06:49Z Warning #2 · 并发回归：
 * {@link MsgModel#updateLastSyncVersion} 在多线程交替写入高低版本时，内存 map
 * 必须保持 {@code max(...)} 语义，永远不会被较小 version 覆盖。
 *
 * <p>原实现 {@code get → compare → put} 是 check-then-put TOCTOU：两个 sync 回调
 * 并发读到同一个旧值，都进入 put 分支，最后写入的可能是较小的 version。修复后
 * 用 {@link java.util.concurrent.ConcurrentHashMap#compute} 在 bucket 锁内做 "取 max"
 * 并用 changed 标志位决定是否 persist，等价于单写者语义但不串行化读路径。
 *
 * <p>host-side 测试依赖 {@link MsgModel#resetLastSyncVersionForTest()} 跳过 SP 预加载
 * （真实 Context 不可用，否则 {@code WKSharedPreferencesUtil} 链路会 NPE）。persist
 * 分支本身 try/catch 吞了 NPE，不影响 map 语义验证。
 */
public class MsgModelUpdateLastSyncVersionConcurrencyTest {

    private static final String SPACE_A = "space_a_concurrency";
    private static final int ROUNDS = 200;

    @Before
    public void setUp() {
        MsgModel.getInstance().resetLastSyncVersionForTest();
    }

    @After
    public void tearDown() {
        MsgModel.getInstance().resetLastSyncVersionForTest();
    }

    @Test
    public void alternatingHighLowWritesKeepMaxInvariant() throws Exception {
        // Worker A 按升序写入 1..ROUNDS；Worker B 按降序写入 ROUNDS..1。
        // 不管调度如何交错，map[SPACE_A] 必须一直 ≥ 已被 observe 过的最大值。
        // 尾态断言 == ROUNDS（两边写入的最大值）。
        //
        // 注：使用 JDK 标准 ExecutorService（非 AppExecutors）承载 2 个 worker，
        // 遵守 YUJ-283 P-11（仓库禁止裸 `new Thread`）。单测不需要共享线程池，
        // Future.get(timeout) 取代 Thread.join，try/finally 保证线程池释放。
        CountDownLatch start = new CountDownLatch(1);
        final long[] maxObservedA = {0};
        final long[] maxObservedB = {0};

        Runnable up = () -> {
            try { start.await(); } catch (InterruptedException ignored) { return; }
            for (int i = 1; i <= ROUNDS; i++) {
                MsgModel.getInstance().updateLastSyncVersion(SPACE_A, i);
                long v = MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A);
                if (v < maxObservedA[0]) {
                    // 回退即失败（应永远单调非递减）
                    throw new AssertionError("monotonic violation up: prev="
                            + maxObservedA[0] + " now=" + v);
                }
                maxObservedA[0] = Math.max(maxObservedA[0], v);
            }
        };
        Runnable down = () -> {
            try { start.await(); } catch (InterruptedException ignored) { return; }
            for (int i = ROUNDS; i >= 1; i--) {
                MsgModel.getInstance().updateLastSyncVersion(SPACE_A, i);
                long v = MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A);
                if (v < maxObservedB[0]) {
                    throw new AssertionError("monotonic violation down: prev="
                            + maxObservedB[0] + " now=" + v);
                }
                maxObservedB[0] = Math.max(maxObservedB[0], v);
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> fA = pool.submit(up);
            Future<?> fB = pool.submit(down);
            start.countDown();
            // Future.get(timeout) 代替 Thread.join：超时即 fail，异常(含 AssertionError)透传
            fA.get(30, TimeUnit.SECONDS);
            fB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue("executor did not terminate",
                    pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(ROUNDS, MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A));
    }

    @Test
    public void staleUpdateDoesNotOverwriteNewer() {
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 100L);
        // 模拟慢回调回来，携带的 version 比当前小 —— 必须被丢弃
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 50L);
        assertEquals(100L, MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A));
    }

    @Test
    public void equalVersionIsNoOp() {
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 42L);
        // 等值写入不会降级也不会 bump
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 42L);
        assertEquals(42L, MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A));
    }

    @Test
    public void zeroOrNegativeVersionIgnored() {
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 77L);
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, 0L);
        MsgModel.getInstance().updateLastSyncVersion(SPACE_A, -5L);
        assertEquals(77L, MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A));
    }

    @Test
    public void nullSpaceIdIgnored() {
        MsgModel.getInstance().updateLastSyncVersion(null, 999L);
        // 不抛，且其它 space 不受污染
        assertEquals(0L, MsgModel.getInstance().getLastSyncVersionForSpace(SPACE_A));
    }
}
