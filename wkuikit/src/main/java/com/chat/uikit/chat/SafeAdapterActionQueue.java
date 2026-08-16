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

import java.util.ArrayDeque;

/**
 * 消息列表适配器刷新动作的串行队列。
 *
 * <p>RecyclerView 正在 layout / scroll 时调 {@code notifyItem*} 会抛
 * IllegalStateException，只能延后。延后就必须保证顺序：同一条消息的两次刷新若被颠倒，
 * 旧状态会覆盖新状态；删除若跑到插入前面，索引就对不上了。
 *
 * <p>因此排队规则是「一旦有动作在队列里，后续动作一律入队」，且**队头被 layout 挡住时
 * 重排的是同一个队头**，绝不让后来的动作先跑（早先的实现是把队头重新 post 到队尾，
 * 后到的动作因此会插队）。
 *
 * <p>若某次 layout 抛异常被上层吞掉，RecyclerView 的内部计数器会永久停在 computing 态，
 * 这时重试多少次都没用 —— 重试到上限就把整个队列丢掉，宁可少刷几次也不崩。
 *
 * <p><b>非线程安全，只能在主线程使用。</b>所有状态（队列、重试计数、标志位）都是裸字段，
 * 依赖「IM / 网络回调都已 marshal 到主线程，{@link Host#postDrain} 投递的 runnable 也在
 * 主线程执行」这一前提。
 */
final class SafeAdapterActionQueue {

    /** 与 RecyclerView 的耦合点，抽出来是为了能脱离 Android 单测。 */
    interface Host {
        /** RecyclerView 是否正在 layout / scroll。 */
        boolean isBusy();

        /** 把排空动作投递到下一帧（实现为 {@code recyclerView.post}），必须保证 FIFO。 */
        void postDrain(Runnable drain);

        /** 重试耗尽、丢弃 {@code count} 个动作时回调，用于打日志。 */
        void onDropped(int count);
    }

    private final Host host;
    private final int maxRetry;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();

    private boolean drainScheduled;
    private boolean draining;
    private int retryCount;

    SafeAdapterActionQueue(Host host, int maxRetry) {
        this.host = host;
        this.maxRetry = maxRetry;
    }

    /**
     * 提交一个动作。队列为空且 RecyclerView 空闲时同步执行（与未引入队列前的行为一致），
     * 否则入队等待排空。
     */
    void submit(Runnable action) {
        if (action == null) return;
        // draining 期间即使队列瞬时为空也必须入队：此刻是在队头的 run() 里被递归调用，
        // 直接执行就跑到了同批次后续动作的前面。
        if (!draining && queue.isEmpty() && !host.isBusy()) {
            action.run();
            return;
        }
        queue.addLast(action);
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (drainScheduled) return;
        drainScheduled = true;
        host.postDrain(this::drain);
    }

    /** 排空队列。由 {@link Host#postDrain} 投递的 runnable 调用。 */
    private void drain() {
        drainScheduled = false;
        if (queue.isEmpty()) {
            retryCount = 0;
            return;
        }
        if (host.isBusy()) {
            if (++retryCount >= maxRetry) {
                int dropped = queue.size();
                queue.clear();
                retryCount = 0;
                host.onDropped(dropped);
                return;
            }
            scheduleDrain();
            return;
        }
        retryCount = 0;
        draining = true;
        try {
            // 每执行一个动作前重新判一次 busy：动作本身可能触发 layout。
            while (!queue.isEmpty() && !host.isBusy()) {
                queue.pollFirst().run();
            }
        } finally {
            draining = false;
            // 覆盖两种情况：被 busy 中断，以及某个动作抛异常提前退出 —— 剩下的不能烂在队列里。
            if (!queue.isEmpty()) scheduleDrain();
        }
    }

    /** 仅供测试断言用。 */
    int pendingCount() {
        return queue.size();
    }
}
