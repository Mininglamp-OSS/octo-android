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

import com.alibaba.fastjson.JSONObject;
import com.xinbida.wukongim.entity.WKSyncChannelMsg;
import com.xinbida.wukongim.entity.WKSyncRecent;

import java.util.Map;

/**
 * 交互卡(type=17)终态帧补偿的**调用方**纯逻辑：把一次补拉响应 / 一次触发时机映射成"该做什么"。
 *
 * <p>抽成无 Android/SDK 依赖的独立类，是为了能在 JVM 上用**真 fastjson 反序列化的 DTO**（而非手 set
 * boolean）覆盖前两轮 review 漏过的两个缺陷——它们都出在"从反序列化 DTO 派生信号"这一步，而非
 * {@link CardRefreshDecider} 的纯决策，所以之前只测决策函数没能守住：
 * <ul>
 *   <li><b>P1-1</b>：{@code content_edit} 是裸 {@link Map}，fastjson 反序列化为 {@code HashMap}，其
 *       {@code toString()} 是 Java map 记法不是 JSON；旧代码 {@code toString()} 再 {@code parseObject}
 *       每次抛异常 → {@code transient} 永远读成 false、{@code MIDSTREAM_RESET} 成死代码。</li>
 *   <li><b>P1-3</b>：目标 seq 不在返回 / 在但还没 {@code content_edit} → 必须当"非信号"，否则同频道
 *       另一张流式卡的每个 CMD 都会 fan-out 补拉本卡、在首帧到达前榨干收敛预算。</li>
 * </ul>
 * 真正的 map 状态与副作用（拉取 / 落库 / 刷新 / 注销）仍在 {@link MsgModel}，这里只判定。
 */
public final class CardCompensationEvaluator {

    private CardCompensationEvaluator() {
    }

    /** 一次补拉响应的评估结果。 */
    public static final class Outcome {
        /** true = 空返回 / 目标 seq 不在返回 / 在但无 content_edit → 非信号，调用方直接返回、不动预算。 */
        public final boolean nonSignal;
        /** {@link #nonSignal}=false 时的决策；否则为 null。 */
        public final CardRefreshDecider.Decision decision;
        /** 有进展时的新内容哈希（调用方写入 lastCardContentHash）；否则 null。 */
        public final Integer newContentHash;

        private Outcome(boolean nonSignal, CardRefreshDecider.Decision decision, Integer newContentHash) {
            this.nonSignal = nonSignal;
            this.decision = decision;
            this.newContentHash = newContentHash;
        }

        static Outcome nonSignal() {
            return new Outcome(true, null, null);
        }

        static Outcome decided(CardRefreshDecider.Decision d, Integer newHash) {
            return new Outcome(false, d, newHash);
        }
    }

    /**
     * 评估一次补拉响应。
     *
     * @param result            补拉返回（可能 null）
     * @param targetSeq         目标卡片 seq
     * @param priorHash         上次记录的 content_edit 哈希（null=从未拿到）
     * @param priorUnproductive 本次之前的连续无进展次数
     * @param maxUnproductive   连续无进展上限
     */
    public static Outcome evaluate(WKSyncChannelMsg result, long targetSeq,
                                   Integer priorHash, int priorUnproductive, int maxUnproductive) {
        Map contentEditMap = extractContentEditMap(result, targetSeq);
        if (contentEditMap == null) return Outcome.nonSignal();
        // 内容哈希判进展：键集固定 → HashMap.toString() 稳定，String.hashCode 可靠。
        String contentEdit = contentEditMap.toString();
        boolean progressed = CardRefreshDecider.isProgress(contentEdit, priorHash);
        boolean midStream = isTransient(contentEditMap);
        CardRefreshDecider.Decision d =
                CardRefreshDecider.decide(progressed, midStream, priorUnproductive, maxUnproductive);
        return Outcome.decided(d, progressed ? contentEdit.hashCode() : null);
    }

    /** 取目标 seq 的 content_edit 原始 Map；目标不在返回 / 有消息但无 content_edit 均返回 null。 */
    public static Map extractContentEditMap(WKSyncChannelMsg result, long targetSeq) {
        if (result == null || result.messages == null || result.messages.isEmpty()) return null;
        for (WKSyncRecent msg : result.messages) {
            if (msg != null && msg.message_seq == targetSeq && msg.message_extra != null) {
                return msg.message_extra.content_edit;
            }
        }
        return null;
    }

    /**
     * content_edit 信封顶层 transient 标记（对齐服务端 cardmsg.Transient）。content_edit 是裸 {@link Map}，
     * 用 {@link JSONObject#JSONObject(Map)} 直接包装读；绝不能 {@code toString()} 再 {@code parseObject}——
     * {@code HashMap.toString()} 不是 JSON、必抛，会让 transient 永远读成 false（P1-1）。
     */
    @SuppressWarnings("unchecked")
    public static boolean isTransient(Map contentEdit) {
        if (contentEdit == null) return false;
        try {
            return new JSONObject(contentEdit).getBooleanValue("transient");
        } catch (Exception e) {
            return false;
        }
    }

    /** 补拉触发时机的节流判定。 */
    public enum ThrottleAction {
        /** 登记已超补偿墙钟窗口仍无终态 → 注销。 */
        DEREGISTER_STALE,
        /** 距上次补拉不足最小间隔 → 挂延迟补拉（调用方按 seq 只挂一个 trailing）。 */
        THROTTLED,
        /** 立即补拉。 */
        PULL_NOW
    }

    /**
     * 决定这次触发该立即补拉、挂延迟补拉、还是注销（墙钟兜底）。纯时间比较，便于 JVM 覆盖节流边界。
     *
     * @param now           当前时刻（elapsedRealtime）
     * @param registeredAt  首次登记时刻（null 视为未超窗口）
     * @param maxWindowMs   补偿墙钟窗口
     * @param lastRefreshAt 上次补拉时刻（null=从未补拉）
     * @param minIntervalMs 两次补拉最小间隔
     */
    public static ThrottleAction throttle(long now, Long registeredAt, long maxWindowMs,
                                          Long lastRefreshAt, long minIntervalMs) {
        if (registeredAt != null && now - registeredAt > maxWindowMs) return ThrottleAction.DEREGISTER_STALE;
        if (lastRefreshAt != null && now - lastRefreshAt < minIntervalMs) return ThrottleAction.THROTTLED;
        return ThrottleAction.PULL_NOW;
    }
}
