package com.xinbida.wukongim.db;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * YUJ-326 · 内存门闸：backfill 是否已完成，用于 UI 侧 {@code SpaceCacheFlag.isEnabled()}
 * 的最后一层 gate。
 *
 * <p>放在 SDK (wkim) 层而非 UI (wkuikit) 层的原因：
 * <ul>
 *   <li>{@link WKDBSpaceIdBackfill#runIfNeeded} 必须能主动 push 状态给 flag 使用方，
 *       而 wkim 不能反向依赖 wkuikit。</li>
 *   <li>SDK 内部 API（e.g. clearForSpace / per-Space sync 将来给其它接入方用）在
 *       backfill 未完成前也需要能感知状态。</li>
 * </ul>
 *
 * <p>状态源语义：
 * <ul>
 *   <li>{@code true} = backfill 成功完成（或之前版本已完成），per-Space 路径可安全使用。</li>
 *   <li>{@code false} = 未完成 / 磁盘不足推迟 / 硬失败，上层必须回退 clearAll 老路径。</li>
 * </ul>
 *
 * <p>初值：{@code false}（保守）。每次应用进程启动 → {@link WKDBSpaceIdBackfill#runIfNeeded}
 * 读 SP 判定后 push 真值。AtomicBoolean 保证跨线程立即可见。
 */
public final class SpaceCacheBackfillGate {

    private static final AtomicBoolean done = new AtomicBoolean(false);

    private SpaceCacheBackfillGate() {}

    /** backfill 完成状态查询。上层 SpaceCacheFlag.isEnabled 读此值做最终 gate。 */
    public static boolean isBackfillDone() {
        return done.get();
    }

    /** backfill 路径（或单测 / debug panel）push 状态。 */
    public static void setBackfillDone(boolean v) {
        done.set(v);
    }
}
