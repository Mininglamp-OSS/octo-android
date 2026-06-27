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

import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xinbida.wukongim.sync.SyncGate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  · Space 切换 / Conversation resync 的去重协调器。
 *
 * <p>对照 iOS {@code WKSyncService._isSyncing}（全局 boolean 守卫）+ 补齐 Android 侧
 * 「每条 sync 路径独立 debounce 500ms」，解决  H2「5 条 sync 触发路径无去重」。
 *
 * <p>Android 侧 {@code performSpaceSwitch} / 注册成功补偿 / 实时消息跨 Space 触发的
 * resync / 启动时 Space 切换首帧 等 5 条路径，在 2 秒内可能连续 fire。本类提供两层
 * 防护：
 * <ol>
 *   <li><b>全局重入守卫</b>：{@link AtomicBoolean}，任何路径拿到 permit 即进入「syncing
 *       中」状态，其他路径直接 drop。必须在 sync 真正完成回调里调 {@link #complete()}
 *       释放。</li>
 *   <li><b>每路径 debounce 500ms</b>：防止同一路径（比如 {@code performSpaceSwitch} 连点）
 *       2 秒内重复入列。基于 {@link SystemClock#elapsedRealtime()} 的时间戳，天然抗时区
 *       / NTP 回跳。</li>
 * </ol>
 *
 * <p><b>线程安全</b>：{@link AtomicBoolean} + {@link ConcurrentHashMap}，所有公开方法可
 * 在任意线程调用（主线程 UI 事件 / IO 线程 Schedulers.io / SDK 回调线程）。
 *
 * <p><b>非目标</b>：不改 {@code performSpaceSwitch} 的 clear+resync 主架构（需与 iOS
 * 对齐，独立 Sprint），只做入口去重。
 */
public final class SpaceSyncCoordinator {

    /** 默认 per-path debounce 窗口（毫秒）。iOS 侧 {@code _isSyncing} 无 debounce，这里
     * 作为 Android 侧额外保险：同路径 2s 内重复点击也只触发一次。 */
    public static final long DEFAULT_DEBOUNCE_MS = 500L;

    /** 兜底释放：如果业务侧忘记调 {@link #complete()}，10s 后自动放行下一次 sync。 */
    private static final long STUCK_RESET_MS = 10_000L;

    private static final SpaceSyncCoordinator INSTANCE = new SpaceSyncCoordinator();

    public static SpaceSyncCoordinator getInstance() {
        return INSTANCE;
    }

    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private volatile long syncStartedAt = 0L;
    private final ConcurrentHashMap<String, Long> lastTriggerAt = new ConcurrentHashMap<>();

    private SpaceSyncCoordinator() {
    }

    /**
     *  (fixing  ReviewBot P1-#3) · 显式绑定本协调器到 wkim 层 SyncGate，
     * 让 {@code WKConnection} 第 5 条 sync 路径也走统一 debounce / 重入守卫。
     *
     * <p>必须由上层在 Application 初始化时调一次（幂等）。不放在 static 块里是因为
     * 触发时机要可控——必须在 {@code WKIM.init()} 之后，SDK 首次 sync 回调之前。
     */
    public static void installSyncGate() {
        SyncGate.install(new SyncGate.Guard() {
            @Override
            public boolean allow(String tag) {
                return getInstance().tryBegin(tag == null ? "wkConnectionSync" : tag);
            }

            @Override
            public void done() {
                getInstance().complete();
            }
        });
    }

    /**
     * 尝试拿到一次 sync 的执行 permit。
     *
     * @param tag 路径标签（稳定字符串，如 {@code "spaceSwitch"}、{@code "connectSuccessCompensate"}）。
     * @return true 表示抢到 permit，调用方可以启动 sync；false 表示被 debounce 或有进行中
     *         的 sync，调用方应直接 return。
     */
    @AnyThread
    public boolean tryBegin(@NonNull String tag) {
        return tryBegin(tag, DEFAULT_DEBOUNCE_MS);
    }

    /**
     * 同 {@link #tryBegin(String)}，但允许自定义 debounce 窗口。
     */
    @AnyThread
    public boolean tryBegin(@NonNull String tag, long debounceMs) {
        final long now = SystemClock.elapsedRealtime();

        // 1) per-path debounce
        Long last = lastTriggerAt.get(tag);
        if (last != null && (now - last) < debounceMs) {
            diagLog(tag, "debounced", now - last);
            return false;
        }

        // 2) 兜底：如果上一次 sync 在进行中超过 STUCK_RESET_MS 仍未 complete，强制放行，
        //    避免 callback 丢失导致永远堵住。
        if (syncing.get()) {
            long started = syncStartedAt;
            if (started != 0L && (now - started) > STUCK_RESET_MS) {
                syncing.compareAndSet(true, false);
                diagLog(tag, "stuck-reset", now - started);
            } else {
                diagLog(tag, "rejected-syncing", now - started);
                return false;
            }
        }

        // 3) 抢 permit
        if (!syncing.compareAndSet(false, true)) {
            diagLog(tag, "lost-race", 0L);
            return false;
        }
        syncStartedAt = now;
        lastTriggerAt.put(tag, now);
        diagLog(tag, "begin", 0L);
        return true;
    }

    /**
     * sync 完成（或失败 / 超时）时调用，释放 permit 供后续路径进入。必须被调用，否则
     * 靠 STUCK_RESET_MS 兜底。
     */
    @AnyThread
    public void complete() {
        long started = syncStartedAt;
        long elapsed = started > 0 ? SystemClock.elapsedRealtime() - started : -1L;
        syncing.compareAndSet(true, false);
        syncStartedAt = 0L;
        diagLog("*", "complete", elapsed);
    }

    /**
     * Space 串消息排查埋点 — 把每条 sync 路径的状态转换记到 DiagSink. 业务零侵入,
     * 仅在 DiagSink 启用时落盘. tag="performSpaceSwitch" 对应用户主动切换 Space,
     * 是排查"切 Space 后还在显示旧 Space 消息"的关键时间点.
     */
    private static void diagLog(@NonNull String tag, @NonNull String event, long elapsedMs) {
        try {
            String currentSpaceId = com.chat.base.space.SpaceFilter.getCurrentSpaceId();
            String currentSpaceName = com.chat.base.space.SpaceNameLookup.nameOf(currentSpaceId);
            com.chat.base.utils.DiagSink.write(
                    "SPACE-SYNC",
                    "tag=" + tag + " event=" + event
                            + " elapsedMs=" + elapsedMs
                            + " currentSpaceId=" + currentSpaceId
                            + " currentSpaceName='" + (currentSpaceName == null ? "" : currentSpaceName) + "'"
            );
        } catch (Throwable ignored) {
        }
    }

    /** 当前是否有 sync 正在进行。用于 UX overlay 判断是否显示 spinner。 */
    @AnyThread
    public boolean isSyncing() {
        return syncing.get();
    }

    /** 测试 / 登出时清状态。生产代码不要在 sync 中途调用。 */
    @AnyThread
    public void reset() {
        syncing.set(false);
        syncStartedAt = 0L;
        lastTriggerAt.clear();
    }

    /**
     * 返回 tag 上次触发距今多少毫秒；未曾触发返回 null。用于日志 / 诊断。
     */
    @AnyThread
    @Nullable
    public Long msSinceLastTrigger(@NonNull String tag) {
        Long t = lastTriggerAt.get(tag);
        if (t == null) return null;
        return SystemClock.elapsedRealtime() - t;
    }
}
