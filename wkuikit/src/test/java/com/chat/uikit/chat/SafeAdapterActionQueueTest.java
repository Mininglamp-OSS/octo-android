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

package com.chat.uikit.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SafeAdapterActionQueue} 的顺序 / 重试语义测试。
 *
 * <p>FakeHost 用一个 FIFO 队列模拟 {@code recyclerView.post} 的投递顺序，测试自己控制
 * busy 状态与「跑下一帧」的时机，从而能确定性地复现真机上极难命中的时序。
 */
public class SafeAdapterActionQueueTest {

    private static final int MAX_RETRY = 3;

    private FakeHost host;
    private SafeAdapterActionQueue queue;
    private List<String> executed;

    private static final class FakeHost implements SafeAdapterActionQueue.Host {
        boolean busy;
        int droppedTotal;
        int dropCallCount;
        final ArrayDeque<Runnable> posted = new ArrayDeque<>();

        @Override
        public boolean isBusy() {
            return busy;
        }

        @Override
        public void postDrain(Runnable drain) {
            posted.addLast(drain);
        }

        @Override
        public void onDropped(int count) {
            droppedTotal += count;
            dropCallCount++;
        }

        /** 跑完当前已投递的一轮，模拟一帧过去。 */
        boolean runOneFrame() {
            if (posted.isEmpty()) return false;
            posted.pollFirst().run();
            return true;
        }

        void runUntilIdle() {
            int guard = 0;
            while (runOneFrame()) {
                if (++guard > 100) throw new IllegalStateException("drain 没有收敛，疑似死循环");
            }
        }
    }

    @Before
    public void setUp() {
        host = new FakeHost();
        executed = new ArrayList<>();
        queue = new SafeAdapterActionQueue(host, MAX_RETRY);
    }

    private Runnable action(String name) {
        return () -> executed.add(name);
    }

    @Test
    public void 空闲且无排队时同步执行() {
        queue.submit(action("A"));

        assertEquals(List.of("A"), executed);
        assertTrue("不该投递 drain", host.posted.isEmpty());
    }

    @Test
    public void 忙时入队_排空后按提交顺序执行() {
        host.busy = true;
        queue.submit(action("A"));
        queue.submit(action("B"));
        queue.submit(action("C"));

        assertEquals("忙时不该执行任何动作", List.of(), executed);
        assertEquals(3, queue.pendingCount());

        host.busy = false;
        host.runUntilIdle();

        assertEquals(List.of("A", "B", "C"), executed);
        assertEquals(0, queue.pendingCount());
    }

    /**
     * 回归测试：PR #129 review 指出的缺陷。
     *
     * <p>旧实现在队头被 layout 挡住时把队头重新 post 到**队尾**，导致后入队的 B 抢在 A 前面
     * 执行。这里断言队头始终是队头。
     */
    @Test
    public void 队头被挡住时重排的是同一个队头_后到的动作不会插队() {
        host.busy = true;
        queue.submit(action("A"));   // 先入队
        queue.submit(action("B"));   // 后入队

        // 第一帧：仍然 busy，队头 A 排不出去 → 重排，B 不能因此抢先
        host.runOneFrame();
        assertEquals("仍在 busy，不该执行任何动作", List.of(), executed);
        assertEquals(2, queue.pendingCount());

        // 第二帧：layout 结束
        host.busy = false;
        host.runUntilIdle();

        assertEquals("A 必须先于 B", List.of("A", "B"), executed);
    }

    @Test
    public void 重试期间新提交的动作排在原有动作之后() {
        host.busy = true;
        queue.submit(action("A"));

        host.runOneFrame();          // 一次失败的重试
        queue.submit(action("B"));   // 重试窗口内到达

        host.busy = false;
        host.runUntilIdle();

        assertEquals(List.of("A", "B"), executed);
    }

    @Test
    public void 重试耗尽_丢弃整个队列且不抛异常() {
        host.busy = true;
        queue.submit(action("A"));
        queue.submit(action("B"));

        for (int i = 0; i < MAX_RETRY; i++) {
            host.runOneFrame();
        }

        assertEquals("重试耗尽后应丢弃而不是执行", List.of(), executed);
        assertEquals(0, queue.pendingCount());
        assertEquals(2, host.droppedTotal);
        assertEquals(1, host.dropCallCount);
        assertTrue("丢弃后不该再排下一轮", host.posted.isEmpty());
    }

    @Test
    public void 丢弃之后队列仍然可用() {
        host.busy = true;
        queue.submit(action("A"));
        for (int i = 0; i < MAX_RETRY; i++) {
            host.runOneFrame();
        }
        assertEquals(0, queue.pendingCount());

        host.busy = false;
        queue.submit(action("B"));

        assertEquals("重试计数应已复位，新动作照常同步执行", List.of("B"), executed);
    }

    @Test
    public void 排空过程中动作内部再提交_追加到队尾而不是立刻执行() {
        host.busy = true;
        queue.submit(() -> {
            executed.add("A");
            queue.submit(action("A-nested"));   // 在 A 的 run() 里递归提交
        });
        queue.submit(action("B"));

        host.busy = false;
        host.runUntilIdle();

        assertEquals(List.of("A", "B", "A-nested"), executed);
    }

    @Test
    public void 排空途中重新变忙_剩余动作保持顺序留到下一帧() {
        host.busy = true;
        queue.submit(() -> {
            executed.add("A");
            host.busy = true;          // A 执行时又触发了一次 layout
        });
        queue.submit(action("B"));
        queue.submit(action("C"));

        host.busy = false;
        host.runOneFrame();
        assertEquals("A 之后应停下", List.of("A"), executed);
        assertEquals(2, queue.pendingCount());

        host.busy = false;
        host.runUntilIdle();
        assertEquals(List.of("A", "B", "C"), executed);
    }

    @Test
    public void 动作抛异常_剩余队列不会烂在里面() {
        host.busy = true;
        queue.submit(() -> {
            executed.add("A");
            throw new RuntimeException("boom");
        });
        queue.submit(action("B"));

        host.busy = false;
        try {
            host.runOneFrame();
        } catch (RuntimeException expected) {
            // 刻意不吞：异常照常穿出去，但队列必须已经安排好后续排空
        }

        assertEquals(List.of("A"), executed);
        assertEquals(1, queue.pendingCount());

        host.runUntilIdle();
        assertEquals(List.of("A", "B"), executed);
    }
}
