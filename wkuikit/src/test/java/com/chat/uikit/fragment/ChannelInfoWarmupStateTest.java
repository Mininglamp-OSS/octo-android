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

package com.chat.uikit.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link ChannelInfoWarmupState} 的单测。
 *
 * <p>重点锁死三件容易写错、且一旦写错就静默失效的事：
 * <ul>
 *   <li>陈旧代际的回调绝不能清掉新代际的运行标志（清掉 = 新链永远不会真正跑起来）</li>
 *   <li>链运行期间被吞掉的 kickoff 必须在收链时补回来（否则那段时间新进的会话永远不预热）</li>
 *   <li>重起必须收敛 —— attempted 过滤保证不会自己跟自己打转</li>
 * </ul>
 */
public class ChannelInfoWarmupStateTest {

    private static ChannelInfoWarmupState.Target target(String id) {
        return new ChannelInfoWarmupState.Target(id, (byte) 1);
    }

    private static List<ChannelInfoWarmupState.Target> targets(String... ids) {
        List<ChannelInfoWarmupState.Target> list = new ArrayList<>();
        for (String id : ids) list.add(target(id));
        return list;
    }

    // ── 开链 / 幂等 ────────────────────────────────────────────────────────

    @Test
    public void tryBegin_firstCallTakesOwnership() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        assertTrue(s.tryBegin("space-a"));
        assertTrue(s.isRunning());
        assertFalse(s.isDirty());
    }

    @Test
    public void tryBegin_whileRunning_isRejectedAndMarksDirty() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        assertTrue(s.tryBegin("space-a"));

        assertFalse(s.tryBegin("space-a"));
        assertTrue("被吞掉的 kickoff 必须记为 dirty", s.isDirty());
        assertTrue(s.isRunning());
    }

    // ── 代际语义（最关键的不变式）──────────────────────────────────────────

    @Test
    public void staleScanResult_doesNotClearNewGenerationRunningFlag() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        int staleGen = s.generation();

        // Space 切换：代际推进，旧链作废
        s.reset(true);
        assertFalse(s.isRunning());

        // 新一代开链
        assertTrue(s.tryBegin("space-b"));
        int freshGen = s.generation();
        assertNotEquals(staleGen, freshGen);
        assertTrue(s.isRunning());

        // 旧一代的 IO 扫描结果姗姗来迟 —— 必须完全无副作用
        assertFalse(s.onScanResult(staleGen, targets("a", "b")));
        assertTrue("陈旧代际不得清掉新代际的 running", s.isRunning());
    }

    @Test
    public void staleAbortAndFinish_areNoOps() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        int staleGen = s.generation();
        s.reset(false);
        s.tryBegin("space-b");

        s.abort(staleGen);
        assertTrue("陈旧代际 abort 不得影响新代际", s.isRunning());

        assertFalse(s.finish(staleGen));
        assertTrue("陈旧代际 finish 不得影响新代际", s.isRunning());
    }

    @Test
    public void isStale_tracksGeneration() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        int gen = s.generation();
        assertFalse(s.isStale(gen));
        s.reset(false);
        assertTrue(s.isStale(gen));
    }

    // ── 扫描结果 ──────────────────────────────────────────────────────────

    @Test
    public void onScanResult_empty_closesChain() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        assertFalse(s.onScanResult(s.generation(), new ArrayList<>()));
        assertFalse("空扫描后必须能收链，否则运行标志会永久卡住",
                s.finish(s.generation()));
        assertFalse(s.isRunning());
    }

    @Test
    public void onScanResult_empty_stillReportsSwallowedKickoff() {
        // 锁住调用点所依赖的契约：空扫描之后 finish() 仍然要能把被吞掉的 kickoff
        // 上报出来。
        //
        // 注意这个用例抓不住它对应的那个缺陷 —— 缺陷在调用点：空扫描路径上
        // 「onScanResult 返回 false 就直接 return」，压根没人调 finish()，dirty
        // 于是烂在里面，下一次 tryBegin 再把它重置掉。状态机本身一直是对的。
        // ChatFragment 是 Fragment，这条路径 JVM 单测覆盖不到，只能靠「收链的唯一
        // 出口是 finish()」这个约定 + 本用例守住契约的另一半。
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.tryBegin("space-a");   // 扫描期间新会话进来 → 被吞 → dirty

        assertFalse(s.onScanResult(s.generation(), new ArrayList<>()));
        assertTrue("空扫描后 finish 必须仍能上报被吞掉的 kickoff", s.finish(s.generation()));
        assertFalse(s.isRunning());
        assertFalse(s.isDirty());
    }

    @Test
    public void onScanResult_null_closesChain() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        assertFalse(s.onScanResult(s.generation(), null));
        assertFalse(s.finish(s.generation()));
        assertFalse(s.isRunning());
    }

    // ── 分批 ──────────────────────────────────────────────────────────────

    @Test
    public void nextBatch_walksThroughPendingThenReportsNoMore() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("a", "b", "c", "d", "e"));

        List<ChannelInfoWarmupState.Target> first = s.nextBatch(2);
        assertEquals(2, first.size());
        assertEquals("a", first.get(0).channelID);
        assertTrue(s.hasMore());

        assertEquals(2, s.nextBatch(2).size());
        assertTrue(s.hasMore());

        List<ChannelInfoWarmupState.Target> last = s.nextBatch(2);
        assertEquals("最后一批不足 batchSize 时只返回剩余的", 1, last.size());
        assertEquals("e", last.get(0).channelID);
        assertFalse(s.hasMore());
        assertTrue(s.nextBatch(2).isEmpty());
    }

    @Test
    public void nextBatch_withoutPending_isEmpty() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        assertTrue(s.nextBatch(4).isEmpty());
        assertFalse(s.hasMore());
    }

    // ── 收链 / 重起 ───────────────────────────────────────────────────────

    @Test
    public void finish_withoutDirty_doesNotRequestRestart() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("a"));
        s.nextBatch(4);

        assertFalse(s.finish(s.generation()));
        assertFalse(s.isRunning());
    }

    @Test
    public void finish_withDirty_requestsRestartExactlyOnce() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("a"));
        s.tryBegin("space-a");   // 被吞 → dirty
        s.nextBatch(4);

        assertTrue("链运行期间被吞掉的 kickoff 必须在收链时补回来", s.finish(s.generation()));
        assertFalse(s.isRunning());
        assertFalse("dirty 只应消费一次", s.isDirty());

        // 重起后若无新候选，不应再次要求重起 —— 保证收敛
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("b"));
        s.nextBatch(4);
        assertFalse(s.finish(s.generation()));
    }

    // ── attempted 过滤 ────────────────────────────────────────────────────

    @Test
    public void markAttempted_isKeyedByIdAndType() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.markAttempted(new ChannelInfoWarmupState.Target("c1", (byte) 1));

        assertTrue(s.isAttempted("c1", (byte) 1));
        assertFalse("同 id 不同 type 是不同频道", s.isAttempted("c1", (byte) 2));
        assertFalse(s.isAttempted("c2", (byte) 1));
    }

    @Test
    public void reset_clearsAttemptedOnlyWhenAsked() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.markAttempted(target("c1"));

        s.reset(false);
        assertTrue("单纯销毁不应清掉已尝试记录", s.isAttempted("c1", (byte) 1));

        s.reset(true);
        assertFalse("Space 切换应允许重新尝试", s.isAttempted("c1", (byte) 1));
        assertEquals(0, s.attemptedCount());
    }

    @Test
    public void clearAttempted_doesNotInterruptRunningChain() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("a", "b"));
        int gen = s.generation();
        s.markAttempted(target("a"));

        s.clearAttempted();

        assertFalse(s.isAttempted("a", (byte) 1));
        assertTrue("网络恢复清 attempted 不应打断在跑的链", s.isRunning());
        assertFalse("也不应推进代际", s.isStale(gen));
        assertTrue(s.hasMore());
    }

    // ── Space 语义 ────────────────────────────────────────────────────────

    @Test
    public void spaceChanged_comparesAgainstSpaceAtBegin() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");

        assertFalse(s.spaceChanged("space-a"));
        assertTrue(s.spaceChanged("space-b"));
        assertTrue(s.spaceChanged(null));
    }

    @Test
    public void spaceChanged_treatsNullSpaceAsLegitimate() {
        // 无 Space 模式：开链时就是 null，之后仍是 null 就不算变化。
        // 写成「空就 return」会让该模式永远不预热。
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin(null);

        assertFalse(s.spaceChanged(null));
        assertTrue(s.spaceChanged("space-a"));
    }

    @Test
    public void abort_stopsChainAndAllowsFreshBegin() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.onScanResult(s.generation(), targets("a", "b", "c"));

        s.abort(s.generation());

        assertFalse(s.isRunning());
        assertFalse(s.hasMore());
        assertTrue("中止后应能重新开链", s.tryBegin("space-a"));
    }

    @Test
    public void abort_discardsDirtyRatherThanLeavingItDangling() {
        // 「链已经不跑了、dirty 却还挂着」这种中间态一旦存在，下一次 tryBegin 会把
        // 它悄悄重置掉，等于又开一条能漏掉 kickoff 的缝。中止的语义就是这一轮不要了，
        // 所以要显式丢弃。不变式：dirty 只由 finish 消费，由 abort / reset 丢弃。
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        s.tryBegin("space-a");
        s.tryBegin("space-a");   // 被吞 → dirty
        assertTrue(s.isDirty());

        s.abort(s.generation());

        assertFalse(s.isRunning());
        assertFalse("中止应当丢弃 dirty，不留悬挂状态", s.isDirty());
    }

    // ── 端到端序列 ────────────────────────────────────────────────────────

    @Test
    public void fullLifecycle_convergesWithoutSpinning() {
        ChannelInfoWarmupState s = new ChannelInfoWarmupState();
        List<String> fetched = new ArrayList<>();

        assertTrue(s.tryBegin("space-a"));
        s.onScanResult(s.generation(), targets("a", "b", "c"));
        // 链跑到一半时来了新会话 → kickoff 被吞
        assertFalse(s.tryBegin("space-a"));

        while (s.hasMore()) {
            for (ChannelInfoWarmupState.Target t : s.nextBatch(2)) {
                s.markAttempted(t);
                fetched.add(t.channelID);
            }
        }
        assertEquals(Arrays.asList("a", "b", "c"), fetched);

        // 收链要求重起，第二轮把新会话补上
        assertTrue(s.finish(s.generation()));
        assertTrue(s.tryBegin("space-a"));
        s.onScanResult(s.generation(), targets("d"));
        for (ChannelInfoWarmupState.Target t : s.nextBatch(2)) {
            s.markAttempted(t);
            fetched.add(t.channelID);
        }
        assertFalse("第二轮没有再被吞的 kickoff，不应无限重起", s.finish(s.generation()));
        assertEquals(Arrays.asList("a", "b", "c", "d"), fetched);
        assertEquals(4, s.attemptedCount());
    }
}
