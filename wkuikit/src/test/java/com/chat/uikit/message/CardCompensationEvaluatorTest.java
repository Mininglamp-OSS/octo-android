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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.chat.uikit.message.CardCompensationEvaluator.Outcome;
import com.chat.uikit.message.CardCompensationEvaluator.ThrottleAction;
import com.xinbida.wukongim.entity.WKSyncChannelMsg;

import org.junit.Test;

/**
 * 交互卡终态帧补偿"调用方"接缝的 JVM 单测。
 *
 * <p><b>刻意用真 fastjson 反序列化的 {@link WKSyncChannelMsg}（而非手 set boolean）</b>——前两轮 review
 * 的两个 P1 都出在"从反序列化 DTO 派生信号"这一步，纯决策测试没能守住：
 * <ul>
 *   <li>{@link #transientFrameDeserialisedByFastjsonYieldsMidstreamReset()} 直接复现并锁死 P1-1：
 *       content_edit 反序列化成 HashMap，旧代码 toString()+parseObject 必抛、transient 永远 false。
 *       只要 evaluate 的 transient 派生退回旧写法，这条即红。</li>
 *   <li>{@link #nullResultIsNonSignal()} / {@link #emptyResultIsNonSignal()} /
 *       {@link #targetSeqAbsentIsNonSignal()} / {@link #messagePresentButNoContentEditIsNonSignal()} /
 *       {@link #fiveUnrelatedProbesOnNoFrameCardNeverConverge()} 锁死 P1-3：非信号不扣预算。</li>
 * </ul>
 */
public class CardCompensationEvaluatorTest {

    private static final int MAX = 5;
    private static final long TARGET_SEQ = 1008002L;

    /** 造一条真 JSON 再交给 fastjson 反序列化，得到与线上一致的运行时类型（content_edit=HashMap）。 */
    private static WKSyncChannelMsg parse(String messagesJson) {
        return JSON.parseObject("{\"messages\":" + messagesJson + "}", WKSyncChannelMsg.class);
    }

    private static String cardMsg(long seq, String contentEditJson) {
        String extra = contentEditJson == null ? "" : ",\"message_extra\":{\"content_edit\":" + contentEditJson + "}";
        return "{\"message_id\":\"m" + seq + "\",\"message_seq\":" + seq + extra + "}";
    }

    // ─────────────────────── P1-1：transient 派生（真 fastjson DTO） ───────────────────────

    @Test
    public void transientFrameDeserialisedByFastjsonYieldsMidstreamReset() {
        // content_edit.transient=true 的中间流式帧：即便内容与上次相同（无进展）也必须判 MIDSTREAM_RESET、
        // 重置计数继续等终态——绝不能因 transient 读不出而落入 UNPRODUCTIVE 把仍在流式的卡饿死注销。
        String ce = "{\"transient\":true,\"plain\":\"thinking\",\"type\":17}";
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ, ce) + "]");

        // priorHash 命中同一内容 → progressed=false，逼迫走 transient 分支。
        String contentEditStr = CardCompensationEvaluator.extractContentEditMap(result, TARGET_SEQ).toString();
        Outcome outcome = CardCompensationEvaluator.evaluate(
                result, TARGET_SEQ, contentEditStr.hashCode(), 4, MAX, false);

        assertFalse(outcome.nonSignal);
        assertEquals("transient 中间帧无进展应 MIDSTREAM_RESET，而非 UNPRODUCTIVE（守住 P1-1）",
                CardRefreshDecider.Decision.MIDSTREAM_RESET, outcome.decision);
    }

    @Test
    public void transientFlagReadDirectlyOffDeserialisedMap() {
        // 单独锁死派生：真 fastjson DTO 的 content_edit 是 HashMap，isTransient 必须读得出 true。
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":true}") + "]");
        assertTrue(CardCompensationEvaluator.isTransient(
                CardCompensationEvaluator.extractContentEditMap(result, TARGET_SEQ)));

        WKSyncChannelMsg terminal = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":false,\"type\":17}") + "]");
        assertFalse(CardCompensationEvaluator.isTransient(
                CardCompensationEvaluator.extractContentEditMap(terminal, TARGET_SEQ)));
    }

    // ─────────────────────── P1-3：非信号不扣预算 ───────────────────────

    @Test
    public void nullResultIsNonSignal() {
        // (b) 请求失败 → result 空：非信号，不动预算。
        Outcome outcome = CardCompensationEvaluator.evaluate(null, TARGET_SEQ, null, 3, MAX, false);
        assertTrue(outcome.nonSignal);
        assertNull(outcome.decision);
    }

    @Test
    public void emptyResultIsNonSignal() {
        // (b) 返回 messages 空数组：非信号。
        Outcome outcome = CardCompensationEvaluator.evaluate(parse("[]"), TARGET_SEQ, null, 3, MAX, false);
        assertTrue(outcome.nonSignal);
    }

    @Test
    public void targetSeqAbsentIsNonSignal() {
        // 返回非空但不含目标 seq（[seq,seq+1] 窗口捞回邻居消息）：非信号。
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ + 1, "{\"type\":17}") + "]");
        assertTrue(CardCompensationEvaluator.evaluate(result, TARGET_SEQ, null, 3, MAX, false).nonSignal);
    }

    @Test
    public void messagePresentButNoContentEditIsNonSignal() {
        // 命中目标消息但还没 content_edit（bot 首帧未到）：非信号——绝不能当"无进展"扣预算。
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ, null) + "]");
        assertTrue(CardCompensationEvaluator.evaluate(result, TARGET_SEQ, null, 3, MAX, false).nonSignal);
    }

    @Test
    public void fiveUnrelatedProbesOnNoFrameCardNeverConverge() {
        // (c) 无帧卡被同频道 5 次无关 CMD 补拉：每次都非信号（decision=null），调用方据此直接 return、
        // 不动预算，所以 5 次后也不会走到 UNPRODUCTIVE_DEREGISTER。真正的断言是每次都 nonSignal。
        WKSyncChannelMsg noFrame = parse("[" + cardMsg(TARGET_SEQ, null) + "]");
        for (int i = 0; i < 5; i++) {
            // priorUnproductive 传 i 模拟"即便预算被别处推高，无帧仍不产出收敛决策"。
            Outcome outcome = CardCompensationEvaluator.evaluate(noFrame, TARGET_SEQ, null, i, MAX, false);
            assertTrue("第 " + i + " 次无帧补拉必须是非信号", outcome.nonSignal);
            assertNull("非信号不产出任何收敛决策", outcome.decision);
        }
    }

    // ─────────────────────── P1-2：终态后陈 transient 帧不得覆盖 ───────────────────────

    @Test
    public void staleTransientAfterTerminalAppliedIsDropped() {
        // 已锁存终态帧后，乱序滞后的 transient 帧（即便内容"有进展"）必须当非信号丢弃，
        // 否则 CONFLICT_REPLACE 会拿它覆盖已落库的终态帧（P1-2）。
        WKSyncChannelMsg transientFrame = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":true,\"plain\":\"late\"}") + "]");
        Outcome outcome = CardCompensationEvaluator.evaluate(
                transientFrame, TARGET_SEQ, "whatever".hashCode(), 0, MAX, /* terminalApplied= */ true);
        assertTrue("终态已应用后的 transient 帧应被丢弃", outcome.nonSignal);
    }

    @Test
    public void terminalFrameSetsAppliedTerminalFlag() {
        // 非 transient 且有进展的帧 = 终态帧：evaluate 应标记 appliedTerminal，供调用方锁存。
        WKSyncChannelMsg terminal = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":false,\"plain\":\"done\"}") + "]");
        Outcome outcome = CardCompensationEvaluator.evaluate(terminal, TARGET_SEQ, "old".hashCode(), 0, MAX, false);
        assertEquals(CardRefreshDecider.Decision.PROGRESS_RESET, outcome.decision);
        assertTrue("非 transient 进展帧应标记为终态", outcome.appliedTerminal);
    }

    @Test
    public void terminalAppliedStillAcceptsNewerNonTransientFrame() {
        // 终态已应用后，又来一个**非** transient 的新帧（如再次编辑）应放行，不被 P1-2 守卫误杀。
        WKSyncChannelMsg newer = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":false,\"plain\":\"edited-again\"}") + "]");
        Outcome outcome = CardCompensationEvaluator.evaluate(newer, TARGET_SEQ, "old".hashCode(), 0, MAX, true);
        assertFalse(outcome.nonSignal);
        assertEquals(CardRefreshDecider.Decision.PROGRESS_RESET, outcome.decision);
    }

    // ─────────────────────── 正常进展 / 收敛路径（回归） ───────────────────────

    @Test
    public void changedContentEditProgressesAndCarriesNewHash() {
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":false,\"plain\":\"final\"}") + "]");
        Outcome outcome = CardCompensationEvaluator.evaluate(result, TARGET_SEQ, "old".hashCode(), 2, MAX, false);
        assertFalse(outcome.nonSignal);
        assertEquals(CardRefreshDecider.Decision.PROGRESS_RESET, outcome.decision);
        assertNotNull("有进展需带回新哈希供调用方记录", outcome.newContentHash);
    }

    @Test
    public void staleNonTransientFrameConvergesAtLimit() {
        // 非 transient 且无进展（bot 崩在同一非流式帧）：达上限时注销，收敛有界。
        WKSyncChannelMsg result = parse("[" + cardMsg(TARGET_SEQ, "{\"transient\":false,\"plain\":\"stuck\"}") + "]");
        String hash = CardCompensationEvaluator.extractContentEditMap(result, TARGET_SEQ).toString();
        Outcome atLimit = CardCompensationEvaluator.evaluate(result, TARGET_SEQ, hash.hashCode(), MAX - 1, MAX, false);
        assertEquals(CardRefreshDecider.Decision.UNPRODUCTIVE_DEREGISTER, atLimit.decision);
        Outcome below = CardCompensationEvaluator.evaluate(result, TARGET_SEQ, hash.hashCode(), 0, MAX, false);
        assertEquals(CardRefreshDecider.Decision.UNPRODUCTIVE_CONTINUE, below.decision);
    }

    // ─────────────────────── (a) 节流：窗口内不立即补、只挂 trailing ───────────────────────

    @Test
    public void throttleWithinWindowDefersInsteadOfPulling() {
        long min = 2000L, maxWindow = 300_000L, now = 100_000L;
        // 距上次补拉不足 min → THROTTLED（调用方按 seq 只挂一个 trailing，合并窗口内多次 CMD 为一次后补）。
        assertEquals(ThrottleAction.THROTTLED,
                CardCompensationEvaluator.throttle(now, now - 500, maxWindow, now - 500, min));
        // 距上次补拉已过 min → 立即补。
        assertEquals(ThrottleAction.PULL_NOW,
                CardCompensationEvaluator.throttle(now, now - 5000, maxWindow, now - 3000, min));
        // 从未补拉过 → 立即补。
        assertEquals(ThrottleAction.PULL_NOW,
                CardCompensationEvaluator.throttle(now, now - 5000, maxWindow, null, min));
    }

    @Test
    public void throttleDeregistersAfterWallClockWindow() {
        long min = 2000L, maxWindow = 300_000L, now = 1_000_000L;
        assertEquals(ThrottleAction.DEREGISTER_STALE,
                CardCompensationEvaluator.throttle(now, now - maxWindow - 1, maxWindow, now - 100, min));
    }
}
