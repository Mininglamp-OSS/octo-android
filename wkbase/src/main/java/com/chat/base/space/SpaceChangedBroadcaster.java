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

package com.chat.base.space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *  · 当前 Space 切换广播（process-scope）。
 *
 * <p>配合 {@link SpaceFilter#getCurrentSpaceId()} 使用：当 {@code current_space_id}
 * 从一个非空值切到另一个非空值、或由空切到非空（反之亦然）时，由
 * {@code MsgModel#setCurrentSpaceId} 在 SP 持久化之后主动广播。
 *
 * <p><b>为什么需要这个广播：</b>（PR#205）把窄屏 ChatActivity 改成
 * {@code FLAG_ACTIVITY_REORDER_TO_FRONT} 保活 + {@code onNewIntent} 热复用，
 * 切换频道只做 per-channel detach / persist / reset / attach / initData，
 * <b>不刷新 Space 上下文</b>。用户「Space A 进群 X → 返回 → 切 Space → 进群 Y」
 * 时，保活的 ChatActivity 在跨 Space 场景被错误复用，可能把 Space A 的旧
 * channel 残留（SDK 内 channel session / WKChannel.remoteExtraMap / 渲染缓存）
 * 串到 Space B，导致「Y 页面显示 X 内容」的数据隔离破坏（P0）。
 *
 * <p>广播点统一在 {@code MsgModel#setCurrentSpaceId} —— 全端唯一的 Space 切换
 * 落地路径（{@code performSpaceSwitch} / App 启动恢复 / 加群自动切换 / 邀请落地页
 * 都会走到这里）。监听方（{@code ChatActivity}）在 {@code onCreate} 注册、
 * {@code onDestroy} 反注册，收到广播且自身记录的 Space 与新 Space 不一致时
 * 直接 {@code finish()}，下次从 {@link com.chat.uikit.chat.ChatReuseNavigator}
 * 进来会走正常 {@code onCreate} 冷启路径，Space 上下文自然对齐。
 *
 * <p><b>为什么不用 EndpointManager：</b>{@code EndpointManager} 是单方法注册
 * （同 sid 只保留一个 handler），无法支持多个 ChatActivity 实例同时监听。
 * 这里使用 {@link CopyOnWriteArrayList} 实现真正的多订阅者语义，零拷贝迭代，
 * 订阅 / 取消订阅成本 O(n)。
 *
 * <p><b>无泄漏保证：</b>生命周期对齐 Activity 的 onCreate/onDestroy；
 * 订阅者自己负责取消订阅。列表本身是 process-scope 静态字段，保留的是
 * Listener 引用，由订阅方保证不捕获不需要长驻的外部引用。
 *
 * <p><b>去重策略：</b>{@link #notifyChanged(String, String)} 会过滤
 * {@code TextUtils.equals(old, new)} 的无意义广播（setCurrentSpaceId 设置
 * 相同值时），避免下游做冗余 finish。
 */
public final class SpaceChangedBroadcaster {

    /** 当前 Space 变化事件监听。 */
    public interface Listener {
        /**
         * @param oldSpaceId 切换前的 Space ID（可能为空串表示「非 Space 模式」）
         * @param newSpaceId 切换后的 Space ID（可能为空串）
         */
        void onSpaceChanged(@NonNull String oldSpaceId, @NonNull String newSpaceId);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private SpaceChangedBroadcaster() {
    }

    /**
     * 注册监听。重复注册同一 listener 只会生效一次（对齐
     * {@link CopyOnWriteArrayList#addIfAbsent(Object)} 语义）。
     */
    public static void addListener(@Nullable Listener listener) {
        if (listener == null) return;
        LISTENERS.addIfAbsent(listener);
    }

    /**
     * 反注册监听。未注册过时 no-op。
     */
    public static void removeListener(@Nullable Listener listener) {
        if (listener == null) return;
        LISTENERS.remove(listener);
    }

    /**
     * 广播 Space 变化事件。推荐调用点：
     * {@code MsgModel.setCurrentSpaceId(String, String)} 在 SP 持久化之后。
     *
     * <p>null 统一归一化为空串（对齐 {@link SpaceFilter#getCurrentSpaceId()} 的
     * 空串语义）；old == new 时静默吞掉。
     */
    public static void notifyChanged(@Nullable String oldSpaceId, @Nullable String newSpaceId) {
        String oldId = oldSpaceId == null ? "" : oldSpaceId;
        String newId = newSpaceId == null ? "" : newSpaceId;
        // 用 Objects.equals（纯 JVM）而不是 android.text.TextUtils.equals —— 保持
        // 该类可在 host-side 单元测试中直接运行，且避免 Android 桩在 JVM 下抛
        // "Stub!"。两者行为在非 null 路径上语义等价。
        if (Objects.equals(oldId, newId)) return;
        for (Listener l : LISTENERS) {
            try {
                l.onSpaceChanged(oldId, newId);
            } catch (Throwable ignored) {
                // 单个 listener 抛异常不能影响其它 listener
            }
        }
    }

    /** 单元测试辅助：清空所有 listener（仅测试可见）。 */
    @VisibleForTesting
    public static void clearListenersForTest() {
        LISTENERS.clear();
    }

    /** 单元测试辅助：当前 listener 数量。 */
    @VisibleForTesting
    public static int listenerCountForTest() {
        return LISTENERS.size();
    }
}
