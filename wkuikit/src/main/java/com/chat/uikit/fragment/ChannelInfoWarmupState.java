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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * channelInfo 后台预热链的状态机（纯 Java，无 Android 依赖，可 JVM 单测）。
 *
 * <p>背景：会话行的置顶(top)、免打扰(mute)、名称、头像只存在于 channel 表，
 * conversation/sync 不下发。新设备 / 清数据后登录时 channel 表是空的，原本唯一的
 * 补齐时机是 adapter bind 里的 fetchChannelInfo —— 用户不滑到那一行就永远补不上。
 * 预热链负责在列表装配完成后把缺失的 channel 分批拉回来。
 *
 * <p>本类只管「该不该发、发哪些、这一代还算不算数」，不碰线程、网络和 DB ——
 * 那些留在 {@code ChatFragment}。这样把最容易写错的代际/取消语义单独隔离出来，
 * 可以用单测锁死，而不是靠注释维持。
 *
 * <p><b>线程模型</b>：所有方法只在主线程调用。IO 线程只做「本地有没有这个 channel」
 * 的扫描，扫完把结果通过 {@link #onScanResult} 交回主线程。
 *
 * <p><b>代际（generation）语义</b>：Space 切换 / 销毁时 {@link #reset} 自增代际，
 * 让在途的链自然失效。<b>关键不变式：陈旧代际的回调绝不能清掉新代际的运行标志</b> ——
 * {@link #onScanResult}、{@link #abort}、{@link #finish} 都以 {@link #isStale} 开头，
 * 陈旧代际直接返回、不改任何状态。
 */
final class ChannelInfoWarmupState {

    /** 预热目标：只带 channelID + channelType，不持有会话 / 频道对象，避免跨线程读写。 */
    static final class Target {
        final String channelID;
        final byte channelType;

        Target(String channelID, byte channelType) {
            this.channelID = channelID;
            this.channelType = channelType;
        }

        @NonNull
        String key() {
            return channelID + "_" + channelType;
        }
    }

    private int generation = 0;
    private boolean running = false;
    /**
     * 链运行期间被吞掉的 kickoff。收链时据此重起一轮，否则那段时间内新进列表的
     * 会话（实时消息新建、增量合并）永远等不到预热 —— 默认 tab 上尤其严重，
     * 因为 topChanged 分支走的是 filterAndDisplay，根本不会再调 kickoff。
     */
    private boolean dirty = false;
    @Nullable
    private String spaceId = null;
    @Nullable
    private List<Target> pending = null;
    private int offset = 0;
    /**
     * 本 Space 内已发过请求的 channel key。kickoff 触发频率很高（预热拉回来的每个
     * channel 都会触发 topChanged → sortMsg → kickoff），没有这个集合的话，永远
     * 拉不到的 channel（已解散 / 无权限）会被反复请求。
     */
    private final Set<String> attempted = new HashSet<>();

    int generation() {
        return generation;
    }

    boolean isRunning() {
        return running;
    }

    boolean isDirty() {
        return dirty;
    }

    boolean isAttempted(String channelID, byte channelType) {
        return attempted.contains(channelID + "_" + channelType);
    }

    int attemptedCount() {
        return attempted.size();
    }

    /**
     * 尝试开链。
     *
     * @return {@code true} 表示本次调用取得了链的所有权，调用方应当去做扫描；
     *         {@code false} 表示已有链在跑，本次被记为 {@link #dirty}，
     *         收链时会自动重起一轮。
     */
    boolean tryBegin(@Nullable String currentSpaceId) {
        if (running) {
            dirty = true;
            return false;
        }
        running = true;
        dirty = false;
        spaceId = currentSpaceId;
        pending = null;
        offset = 0;
        return true;
    }

    /** 代际是否已被 {@link #reset} 推进 —— 是则说明新一代已接管，本代应当噤声。 */
    boolean isStale(int gen) {
        return gen != generation;
    }

    /**
     * IO 线程扫描结果回到主线程。
     *
     * @return {@code true} 表示可以开始发批次；{@code false} 表示本代已作废
     *         （不改任何状态）或没有待办（已收链）。
     */
    boolean onScanResult(int gen, @Nullable List<Target> scanned) {
        // 陈旧代际：running 归新一代所有，这里绝不能碰。
        if (isStale(gen)) return false;
        if (scanned == null || scanned.isEmpty()) {
            running = false;
            pending = null;
            offset = 0;
            return false;
        }
        pending = new ArrayList<>(scanned);
        offset = 0;
        return true;
    }

    /** 当前 Space 是否已不是开链时的那个。 */
    boolean spaceChanged(@Nullable String currentSpaceId) {
        // 注意：空 spaceId 是合法状态（无 Space 模式），不能当成「没有 Space 就不预热」，
        // 只比较「是否还是开链时的那个 Space」。
        return !Objects.equals(spaceId, currentSpaceId);
    }

    /** 中止本代（Fragment 不可用 / Space 变了）。陈旧代际调用是 no-op。 */
    void abort(int gen) {
        if (isStale(gen)) return;
        running = false;
        pending = null;
        offset = 0;
    }

    /** 取下一批目标并推进游标；返回空列表表示已取完。 */
    @NonNull
    List<Target> nextBatch(int batchSize) {
        if (pending == null || batchSize <= 0 || offset >= pending.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + batchSize, pending.size());
        List<Target> batch = new ArrayList<>(pending.subList(offset, end));
        offset = end;
        return batch;
    }

    /** 是否还有未发出的目标。 */
    boolean hasMore() {
        return pending != null && offset < pending.size();
    }

    void markAttempted(@NonNull Target target) {
        attempted.add(target.key());
    }

    /**
     * 收链。
     *
     * @return {@code true} 表示期间有被吞掉的 kickoff，调用方应当立即重起一轮。
     *         因为 {@link #attempted} 会把已请求过的过滤掉，重起必然收敛而不会打转。
     */
    boolean finish(int gen) {
        if (isStale(gen)) return false;
        running = false;
        pending = null;
        offset = 0;
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    /**
     * 让在跑的链失效。
     *
     * @param clearAttempted Space 切换 / 重新联网传 {@code true} —— 允许对同一个
     *                       channel 重新尝试；单纯销毁传 {@code false}。
     */
    void reset(boolean clearAttempted) {
        generation++;
        running = false;
        dirty = false;
        spaceId = null;
        pending = null;
        offset = 0;
        if (clearAttempted) attempted.clear();
    }

    /**
     * 只清「已请求过」记录，不打断在跑的链。
     *
     * <p>用于网络恢复：拉取失败没有任何回调信号（provider 对非子区频道传的是 null
     * 回调，失败被静默吞掉），所以无法区分「永久失败」和「网络抖动」。连接恢复时清空，
     * 让下一轮 kickoff 重新尝试；重试量受实际仍缺失的数量约束，不会放大。
     */
    void clearAttempted() {
        attempted.clear();
    }
}
