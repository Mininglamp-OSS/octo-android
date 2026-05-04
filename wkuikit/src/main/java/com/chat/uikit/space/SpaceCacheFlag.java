package com.chat.uikit.space;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.uikit.BuildConfig;

/**
 * YUJ-326 · Phase 3b 灰度 Feature Flag（三级）。
 *
 * <p>三级覆盖（优先级：SP override &gt; RemoteConfig bucket &gt; BuildConfig 默认值）：
 * <ol>
 *   <li><b>编译期</b> {@link BuildConfig#DEBUG} · dev/staging build 默认开启；release build
 *       默认关闭（走远程开关）。</li>
 *   <li><b>远程开关</b> SP key {@code remote_per_space_cache_bucket}（int 0-100）。写入由
 *       AppConfig / RemoteConfig 拉取回调触发（future hook，见
 *       {@link #setRemoteBucket(int)}）。命中判定 {@code hash(uid) % 100 &lt; bucket}。</li>
 *   <li><b>本地 QA override</b> SP key {@code debug_per_space_cache_override}：{@code 1}
 *       强开、{@code 0} 强关、{@code null} 走远程+编译规则。仅 debug 面板用。</li>
 * </ol>
 *
 * <p><b>硬约束</b>：此 flag 只影响 {@code performSpaceSwitch} 里 clearAll → clearForSpace
 * 的切换路径。flag off 时完全走 YUJ-312/316/318 老路径（全库 clearAll + 全量 resync），
 * 不触碰 SpaceFilter Round-3 五层防御的任何 Set/内存态（见 YUJ-325 §6 硬约束）。
 *
 * <p><b>回滚路径</b>：远程推 {@code remote_per_space_cache_bucket=0} 秒级把所有用户切回
 * {@code clearAll()}，已写入的 {@code space_id} 列保留无副作用（老 {@code clearAll()} 清
 * 整个 conversation 表，之前写入的 space_id 跟着一起被清）。
 */
public final class SpaceCacheFlag {

    private static final String SP_REMOTE_BUCKET = "remote_per_space_cache_bucket";
    private static final String SP_DEBUG_OVERRIDE = "debug_per_space_cache_override";

    /**
     * dev / staging build 默认打开以便内部验证；release 默认关闭走远程开关。
     * 与 YUJ-325 §6 灰度阶梯 Phase 3b.0 (dev) / 3b.1 (staging dogfood) 对齐。
     */
    private static final boolean DEFAULT_DEBUG_BUILD = true;
    private static final boolean DEFAULT_RELEASE_BUILD = false;

    /** @VisibleForTesting */
    @Nullable
    private static Boolean sTestOverride = null;

    private SpaceCacheFlag() {}

    /**
     * 本进程是否启用 per-Space cache 路径。<b>每次 performSpaceSwitch 入口调用一次，
     * 不缓存</b> —— 远程 bucket 改变或 QA 切换 override 可立刻生效，无需重启。
     *
     * <p>YUJ-326 Yu review 2026-05-04 04:41Z · 追加 backfill gate：无论 test / SP / remote
     * 如何打开，若 {@code WKDBSpaceIdBackfill} 尚未完成（磁盘不足推迟 / 重试中 / 硬失败），
     * 此方法必须返回 false，让上层回落 {@code clearAll()} 老路径。避免在"列已加但数据
     * 未回填"的 window 内被 {@code clearForSpace} 误清未识别行。测试 override 无视此
     * gate，方便单测不依赖真实 DB 状态。
     */
    @AnyThread
    public static boolean isEnabled() {
        if (sTestOverride != null) return sTestOverride;

        // Level 3: 本地 QA override（优先级最高，用于 debug 面板 / CI 验证）。
        int override = WKSharedPreferencesUtil.getInstance().getInt(SP_DEBUG_OVERRIDE, -1);
        boolean upstreamEnabled;
        if (override == 1) {
            upstreamEnabled = true;
        } else if (override == 0) {
            upstreamEnabled = false;
        } else {
            // Level 2: 远程 bucket。拿 uid 稳定 hash 做分桶，同一用户多次判定结果一致。
            int bucket = WKSharedPreferencesUtil.getInstance().getInt(SP_REMOTE_BUCKET, -1);
            if (bucket >= 0) {
                if (bucket >= 100) {
                    upstreamEnabled = true;
                } else if (bucket == 0) {
                    upstreamEnabled = false;
                } else {
                    upstreamEnabled = computeBucket(safeUid()) < bucket;
                }
            } else {
                // Level 1: 编译期默认值。
                upstreamEnabled = BuildConfig.DEBUG ? DEFAULT_DEBUG_BUILD : DEFAULT_RELEASE_BUILD;
            }
        }

        if (!upstreamEnabled) return false;

        // Final gate · backfill 必须已完成，否则回落老路径。
        return com.xinbida.wukongim.db.SpaceCacheBackfillGate.isBackfillDone();
    }

    /**
     * 远程 AppConfig / RemoteConfig 拉到值后写入 SP。未来接 {@code WKCommonModel.getAppConfig}
     * 回调时，取 {@code per_space_cache_bucket} 字段调此方法即可。当前手动 QA 下可 push 到
     * SP。clamp 到 [0,100]。
     */
    @AnyThread
    public static void setRemoteBucket(int bucket) {
        int clamped = Math.max(0, Math.min(100, bucket));
        WKSharedPreferencesUtil.getInstance().putInt(SP_REMOTE_BUCKET, clamped);
    }

    /** QA 面板本地覆盖：{@code null} 清除，{@code true/false} 强开/强关。 */
    @AnyThread
    public static void setDebugOverride(@Nullable Boolean value) {
        if (value == null) {
            WKSharedPreferencesUtil.getInstance().putInt(SP_DEBUG_OVERRIDE, -1);
        } else {
            WKSharedPreferencesUtil.getInstance().putInt(SP_DEBUG_OVERRIDE, value ? 1 : 0);
        }
    }

    /** @VisibleForTesting — 单测专用，跳过 SP/BuildConfig 链路。传 null 恢复生产行为。 */
    public static void setTestOverrideForUnitTest(@Nullable Boolean v) {
        sTestOverride = v;
    }

    @NonNull
    private static String safeUid() {
        try {
            String uid = WKConfig.getInstance().getUid();
            return uid == null ? "" : uid;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * @VisibleForTesting · 计算 uid 的稳定 bucket（范围 {@code [0, 100)}）。
     *
     * <p>Jerry review 2026-05-04 06:49Z · Critical #1：原实现 {@code Math.abs(uid.hashCode())}
     * 当 {@code hashCode() == Integer.MIN_VALUE} 时溢出回 {@code Integer.MIN_VALUE}（负数），
     * 再 {@code % 100} 得到负数 → 与 {@code bucket} 的比较恒成立（负数 &lt; 正数）。
     * 灰度 {@code bucket=0} 明明要"全关"，却对该 uid 变成"强开"，灰度逃逸。
     *
     * <p>修法：先与 {@code 0x7FFFFFFF} 做位与清零符号位（纯位运算不溢出），再取模 100。
     * 对所有 32-bit int 值域都正确，包括 {@code Integer.MIN_VALUE}。
     *
     * <p>副作用：提取成包外可见的静态方法，便于单测在不构造 mock 的情况下直接覆盖到
     * {@code Integer.MIN_VALUE} 分支。生产调用方（{@link #isEnabled}）语义不变。
     */
    static int computeBucket(@NonNull String uid) {
        return (uid.hashCode() & 0x7FFFFFFF) % 100;
    }
}
