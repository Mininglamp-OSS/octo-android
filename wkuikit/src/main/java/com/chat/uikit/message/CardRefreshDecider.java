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

/**
 * 交互卡(type=17)终态帧补偿的"是否继续补拉/何时注销"纯决策逻辑。
 *
 * <p>抽成无 Android / SDK 依赖的独立类，纯函数、便于 JVM 单测（补偿状态机是修 P1-2/P1-3 的
 * 核心，之前无测试覆盖）。真正的 map 状态与副作用（拉取 / 落库 / 刷新）仍在
 * {@link MsgModel#onCardRefreshResult}，这里只负责判定。
 *
 * <p><b>计数语义 = 连续无进展补拉次数</b>（不是总次数）：
 * <ul>
 *   <li>有进展 → 清零，继续补（长推理卡流式几十个 transient 帧不会被"总次数上限"饿死 → 修 P1-2）；</li>
 *   <li>无进展 → +1，连续 {@code maxUnproductive} 次即注销（纯展示卡永无 content_edit 会几次就退出、
 *       不再 fan-out；bot 崩在某帧也有界收敛 → 修 P1-3）。</li>
 * </ul>
 *
 * <p><b>进展判定用 content_edit 内容哈希</b>，不用 extra_version：多副本 HiLo 下 version 非单调，
 * 终态帧 version 可能反而更低，用它判会把终态帧误当"无进展"漏掉。
 */
public final class CardRefreshDecider {

    private CardRefreshDecider() {
    }

    public enum Decision {
        /** content_edit 内容相对上次有变化 → 落库刷新 + 无进展计数清零。 */
        PROGRESS_RESET,
        /** 拉回的是 transient 中间流式帧（内容没变但卡片仍在活跃流式）→ 重置计数、继续等终态。 */
        MIDSTREAM_RESET,
        /** 本次无进展，但还没到上限 → 无进展计数 +1，继续等下次触发。 */
        UNPRODUCTIVE_CONTINUE,
        /** 连续无进展已达上限 → 注销这张卡，停止补偿。 */
        UNPRODUCTIVE_DEREGISTER
    }

    /**
     * content_edit 存在且内容相对 {@code lastHash} 有变化 = 有进展。
     *
     * @param contentEdit 本次补拉回来的 content_edit（无则传 null）
     * @param lastHash    上次记录的 content_edit 哈希；null 表示这张卡此前从未拿到过 content_edit
     */
    public static boolean isProgress(String contentEdit, Integer lastHash) {
        if (contentEdit == null) return false;
        return lastHash == null || lastHash != contentEdit.hashCode();
    }

    /**
     * @param progressed        本次是否有进展（{@link #isProgress}）
     * @param midStream         本次拉回的是否为 transient 中间流式帧（仍在活跃流式，不该计入收敛）
     * @param priorUnproductive 本次之前的连续无进展次数
     * @param maxUnproductive   连续无进展上限（达到即注销）
     */
    public static Decision decide(boolean progressed, boolean midStream, int priorUnproductive, int maxUnproductive) {
        if (progressed) return Decision.PROGRESS_RESET;
        if (midStream) return Decision.MIDSTREAM_RESET;
        return (priorUnproductive + 1 >= maxUnproductive)
                ? Decision.UNPRODUCTIVE_DEREGISTER
                : Decision.UNPRODUCTIVE_CONTINUE;
    }
}
