/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.uikit.message.CardRefreshDecider.Decision;

import org.junit.Test;

/**
 * 交互卡终态帧补偿决策的纯逻辑单测。重点守住 review 指出的 P1-2 / P1-3：
 * 长推理卡流式不被"总次数上限"饿死；纯展示卡 / bot 卡死能有界收敛注销。
 */
public class CardRefreshDeciderTest {

    private static final int MAX = 5;

    // ───────────────────────── isProgress ─────────────────────────

    @Test
    public void nullContentEditIsNeverProgress() {
        assertFalse(CardRefreshDecider.isProgress(null, null));
        assertFalse(CardRefreshDecider.isProgress(null, 12345));
    }

    @Test
    public void firstContentEditIsProgress() {
        assertTrue(CardRefreshDecider.isProgress("frame-1", null));
    }

    @Test
    public void sameContentIsNotProgress() {
        String ce = "frame-同一帧";
        assertFalse(CardRefreshDecider.isProgress(ce, ce.hashCode()));
    }

    @Test
    public void changedContentIsProgress() {
        assertTrue(CardRefreshDecider.isProgress("frame-2", "frame-1".hashCode()));
    }

    // ───────────────────────── decide ─────────────────────────

    @Test
    public void progressAlwaysResets() {
        assertEquals(Decision.PROGRESS_RESET, CardRefreshDecider.decide(true, 0, MAX));
        assertEquals("有进展时哪怕之前无进展次数很高也应 reset 而非注销",
                Decision.PROGRESS_RESET, CardRefreshDecider.decide(true, MAX + 100, MAX));
    }

    @Test
    public void unproductiveBelowMaxContinues() {
        assertEquals(Decision.UNPRODUCTIVE_CONTINUE, CardRefreshDecider.decide(false, 0, MAX));
        assertEquals(Decision.UNPRODUCTIVE_CONTINUE, CardRefreshDecider.decide(false, MAX - 2, MAX));
    }

    @Test
    public void unproductiveReachingMaxDeregisters() {
        assertEquals(Decision.UNPRODUCTIVE_DEREGISTER, CardRefreshDecider.decide(false, MAX - 1, MAX));
        assertEquals(Decision.UNPRODUCTIVE_DEREGISTER, CardRefreshDecider.decide(false, MAX, MAX));
    }

    // ───────────────────────── 场景回放（守住 P1-2 / P1-3）─────────────────────────

    /** P1-2：长推理卡流式 30 个不同 transient 帧，每帧都有进展 → 永不注销，额度不会被烧光。 */
    @Test
    public void longStreamOfDistinctFramesNeverDeregisters() {
        Sim sim = new Sim(MAX);
        for (int i = 0; i < 30; i++) {
            Decision d = sim.pull("transient-frame-" + i);
            assertEquals("第 " + i + " 个不同帧应判有进展", Decision.PROGRESS_RESET, d);
            assertFalse("有进展不该注销", sim.deregistered);
        }
        // 终态帧（内容再次变化，哪怕它的 version 更低）→ 仍判进展、落库，不漏。
        assertEquals(Decision.PROGRESS_RESET, sim.pull("✅ 已完成"));
        assertFalse(sim.deregistered);
    }

    /** P1-3：纯展示卡永远无 content_edit → 连续无进展，MAX 次后注销、停止 fan-out。 */
    @Test
    public void displayOnlyCardDeregistersAfterMaxUnproductive() {
        Sim sim = new Sim(MAX);
        for (int i = 0; i < MAX - 1; i++) {
            assertEquals(Decision.UNPRODUCTIVE_CONTINUE, sim.pull(null));
            assertFalse(sim.deregistered);
        }
        assertEquals(Decision.UNPRODUCTIVE_DEREGISTER, sim.pull(null));
        assertTrue("连续 MAX 次无 content_edit 应注销", sim.deregistered);
    }

    /** bot 崩在某帧：先来一帧（进展），之后一直重复同一帧 → 连续无进展，最终注销。 */
    @Test
    public void botStuckOnSameFrameDeregisters() {
        Sim sim = new Sim(MAX);
        assertEquals(Decision.PROGRESS_RESET, sim.pull("frame-stuck"));   // 首帧有进展
        for (int i = 0; i < MAX - 1; i++) {
            assertEquals(Decision.UNPRODUCTIVE_CONTINUE, sim.pull("frame-stuck"));
        }
        assertEquals(Decision.UNPRODUCTIVE_DEREGISTER, sim.pull("frame-stuck"));
        assertTrue(sim.deregistered);
    }

    /**
     * 把 {@link MsgModel#onCardRefreshResult} 里的状态机（lastHash + 无进展计数 + 注销）
     * 抽出来复刻一份，用 {@link CardRefreshDecider} 驱动，验证端到端行为。
     */
    private static final class Sim {
        private final int max;
        private Integer lastHash = null;
        private int unproductive = 0;
        boolean deregistered = false;

        Sim(int max) {
            this.max = max;
        }

        Decision pull(String contentEdit) {
            if (deregistered) throw new IllegalStateException("注销后不应再补拉");
            boolean progressed = CardRefreshDecider.isProgress(contentEdit, lastHash);
            if (progressed) lastHash = contentEdit.hashCode();
            Decision d = CardRefreshDecider.decide(progressed, unproductive, max);
            switch (d) {
                case PROGRESS_RESET:
                    unproductive = 0;
                    break;
                case UNPRODUCTIVE_CONTINUE:
                    unproductive++;
                    break;
                case UNPRODUCTIVE_DEREGISTER:
                    deregistered = true;
                    break;
            }
            return d;
        }
    }
}
