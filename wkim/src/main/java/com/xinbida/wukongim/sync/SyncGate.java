package com.xinbida.wukongim.sync;

/**
 *  (fixing  ReviewBot P1-#3) · SDK 侧 sync 触发点的轻量守卫 hook。
 *
 * <p>上层模块（wkuikit {@code SpaceSyncCoordinator}）无法被 wkim 直接依赖——wkim 是
 * SDK 底层。为了让 {@code WKConnection} 里的 sync 触发点也能走统一的 Space 级 debounce /
 * 重入守卫，这里提供一个 <b>install-once</b> 的静态 hook：上层在 Application 初始化
 * 时 {@link #install(Guard)} 一次；SDK 侧在 sync 入口前调 {@link #allow(String)}，在
 * sync 回调末尾调 {@link #done()}。
 *
 * <p><b>未安装 fail-open 语义</b>：上层未注册 guard 时 {@link #allow(String)} 恒返回
 * {@code true}、{@link #done()} 空操作——保持与安装前行为 100% 一致，SDK 独立用户
 * 不受影响。
 */
public final class SyncGate {

    /** 上层注入的守卫逻辑。实现必须线程安全。 */
    public interface Guard {
        /**
         * @param tag 稳定路径标签，例如 {@code "wkConnectionSync"}。
         * @return {@code true} 表示允许进入 sync；{@code false} 表示被 debounce / 已有
         *         sync 进行中，调用方应跳过 sync 触发（但仍可继续执行 sync 无关的副作用，
         *         比如连接状态更新）。
         */
        boolean allow(String tag);

        /** sync 结束（成功 / 失败 / 超时）时调用，释放 permit。 */
        void done();
    }

    private static volatile Guard guard;

    private SyncGate() {
    }

    /**
     * 安装 / 替换守卫。同一 process 建议只调用一次（上层 Application 初始化期）。
     * 传入 {@code null} 可以卸载守卫，恢复 fail-open 语义。
     */
    public static void install(Guard g) {
        guard = g;
    }

    /**
     * 询问守卫是否允许进入 sync。未安装 guard 时恒返回 {@code true}（fail-open）。
     */
    public static boolean allow(String tag) {
        Guard g = guard;
        if (g == null) return true;
        try {
            return g.allow(tag);
        } catch (Throwable t) {
            // 守卫异常不能影响 SDK 主流程——按允许处理
            return true;
        }
    }

    /**
     * 释放 permit。未安装 guard 时空操作。
     */
    public static void done() {
        Guard g = guard;
        if (g == null) return;
        try {
            g.done();
        } catch (Throwable ignored) {
        }
    }
}
