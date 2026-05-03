# YUJ-298 · 窄屏 ChatActivity 复用（Fix A 补课）—— 方案与 Trace 基线

> 回应 Yu 反馈（2026-05-03 11:34Z）「点击群或者子区进入的还是不够丝滑，点回退也
> 比较慢，也有延迟」。Sprint 0/1 把 Activity 过渡从 250-350ms 压到 120ms（PR#195,
> YUJ-276, Fix D），但窄屏每次点击仍在 recreate ChatActivity，XML inflate +
> PanelSwitchHelper.Builder + DB read 叠 200-400ms 是剩下的大头。本任务补上 Fix A：
> 让窄屏点击也走 onNewIntent 热路径。

## 1. 根因（和 YUJ-276 / YUJ-267 的关系）

| 场景 | 当前行为 | 根因 |
| --- | --- | --- |
| 分屏态切不同群 | onNewIntent 热路径（~80-150ms） | YUJ-267 Fix B + Activity Embedding 副栏常驻 |
| 分屏态返回列表 | 副栏常驻，back 由 Embedding 处理 | Embedding 内建 |
| **窄屏点不同群** | **onCreate 冷启动（~250-400ms）** | **每次 back → finish，ChatActivity 被销毁** |
| **窄屏返回列表** | **finish() + 默认过渡** | **150ms postDelayed + recreate 上一帧 + 默认 Activity transition** |

YUJ-276 的 Fix D（NarrowTransition 120ms slide）只是**把过渡动画本身**压短，并没
有避免 `DataBindingUtil.setContentView(act_chat_layout)` 的 XML 膨胀、没有避免
`PanelSwitchHelper.Builder(this).build()` 的面板体系首建、也没有避免 DB 首轮
fetch —— 这些单次加起来往往在 150-250ms 主线程时间上，用户仍然感觉「卡」。

## 2. 方案（Fix A · 三个动作）

### 2.1 路由统一 —— `ChatReuseNavigator.launchChat`
所有「点击会话 / 子区卡片 / 搜索结果 → 打开 ChatActivity」的 startActivity 都
经过 `com.chat.uikit.chat.ChatReuseNavigator.launchChat`。命中窄屏时 Intent 被
合并 `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`：

- 任务栈里已有 ChatActivity → AMS 先 reorder 到栈顶，再按 singleTop 派发
  `onNewIntent`（YUJ-267 里的 detach→persist→setIntent→initParam→
  resetPerChannelState→attach→initData 流程接管）。**感知延迟 ~50-100ms**。
- 任务栈里没有 ChatActivity → 正常新建，`onCreate` 里的 `NarrowTransition.
  applyFastOpen` 仍生效，与 PR#195 一致。**不回退**。

覆盖的 4 条入口（对齐 YUJ-278 P1-1 子区路径清单）：
1. `WKIMUtils.startChat` — 会话列表点击。
2. `WKThreadCreatedProvider` — 子区卡片点击（3 个 startActivity 分支）。
3. `CreateThreadActivity` — 创建子区后跳转。
4. `SearchAllActivity` — 搜索结果点击。

### 2.2 返回软化 —— `ChatReuseNavigator.goBackToList`
窄屏下 `ChatActivity.setBackListener` 不直接 `finish()`，而是调用
`ChatReuseNavigator.goBackToList(this)`。该方法：

1. 检查 `NarrowTransition.isNarrow(activity)`（手机类 sw<600dp + 未在 Embedding
   副栏）；非窄屏直接返回 `false`，调用方走原 `finish()` 路径（不改变分屏 / 折叠
   行为）。
2. 启动 `TabActivity` with `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`
   —— 把 `TabActivity` 重新带到任务栈顶，`ChatActivity` 被压到 `onStop`，**实例保活**。
3. 调 `NarrowTransition.applyFastClose` 保留 120ms 对称动画（pre-34 生效，
   API 34+ 已在 `applyFastOpen` 里预注册过 CLOSE override，此处 no-op）。

下一次 `launchChat` 命中 REORDER_TO_FRONT，直达 `onNewIntent`。

**去掉 150ms postDelayed**：原代码为等面板动画收起，但 `chatPanelManager.isCanBack`
已在调用点完成面板 collapse；真正的 150ms 只是压到用户感知延迟上。窄屏复用路径
直接立即执行，fallback（非窄屏）保留原 150ms 兼容分屏面板体系。

### 2.3 同频道短路 —— `onNewIntent` early-return
窄屏复用后常见新场景：用户 back 回列表 → 再次点击**同一会话**。此时
`oldChannelId == newChannelId`，走完整 detach/reset 会导致 `chatAdapter.setList([])`
闪烁 + 重读 DB。短路掉：只 `setIntent(intent)` 保留新 extras（tipsOrderSeq 等），
画面保持不动，感知延迟 <30ms。

## 3. 可回归的内存 / 生命周期取舍

| 项 | 现状 | 本方案 |
| --- | --- | --- |
| ChatActivity 实例驻留 | back 即 finish（0 MB） | 驻留 1 个实例（~15-30MB） |
| Channel-scoped listeners | onDestroy 清 | `onPause`/`onStop` 不清，onNewIntent 路径 detach/attach |
| 真正退出路径 | finish() | 业务 `finish()`（退群 / 账号切换 / logout）不受影响 |
| 系统回收 | — | 进程压力下 AMS 回收 → 下一次命中冷路径（= 今天行为，无退化） |

## 4. Trace 采集（扩展 YUJ-278 方法论）

扩展 PR#195 落地的 trace 点：

| 阶段 | Log 行 | 含义 |
| --- | --- | --- |
| T_CLICK | `[T_CLICK] startChat enter channel=…` | startChat 入口 |
| T_INTENT_BUILT | `[T_INTENT_BUILT] io=… sinceClick=…` | IO 组装完成 |
| T_START_ACTIVITY_VIA_NAVIGATOR | `[T_START_ACTIVITY_VIA_NAVIGATOR] narrow=…` | YUJ-298 · 经 Navigator 分流的最后点 |
| T_ON_CREATE_END | `[T_ON_CREATE_END] channel=… total=…` | 冷路径（无复用实例时） |
| T_ON_NEW_INTENT_END | `[T_ON_NEW_INTENT_END] channel=… sameChannel=… total=…` | **Fix A 热路径**：此值应成为窄屏主路径 |
| T_REUSE_BACK | `[T_REUSE_BACK] kept=ChatActivity` | YUJ-298 · 软返回命中 |

### 采集步骤

```bash
# 1. 装 debug 包到真机（窄屏手机，比如 Pixel 7 竖屏 sw411dp）。
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk

# 2. 清日志 + 启 logcat。
adb logcat -c
adb logcat -s YUJ276-trace:D YUJ278-transition:D YUJ298-reuse:D > /tmp/yuj298-trace.log &
LOGCAT_PID=$!

# 3. 连续操作 10 次：
#    (a) 点会话 A 进入；
#    (b) back 返回；
#    (c) 点会话 B 进入（期望走 T_ON_NEW_INTENT_END 热路径）；
#    (d) back；
#    (e) 再点会话 A（期望走 sameChannel=true 短路分支）。
#    同样流程用子区卡片 / SearchAll 搜索结果各跑 10 次。

# 4. 停日志，分析。
kill $LOGCAT_PID
bash scripts/parse-yuj276-trace.sh /tmp/yuj298-trace.log
```

### 期望结果

| 指标 | 当前（Fix D 120ms） | 目标（Fix A 复用） |
| --- | --- | --- |
| 窄屏点击 P50（T_CLICK → T_ON_RESUME_END） | ~450ms | **< 200ms** |
| 窄屏点击 P90 | ~700ms | < 350ms |
| 窄屏返回 P50（back → 列表 onResume） | ~300ms | **< 150ms** |
| 窄屏点同一会话（sameChannel 短路） | — | < 80ms |
| 分屏态无回归 | ~80-150ms | ~80-150ms（与现状一致） |

## 5. 回归清单

- [ ] PR#191 分屏 singleTop + 选中态：不回归（本方案窄屏才加 flag，分屏不改）。
- [ ] PR#193 折叠屏 phone→unfold：不回归（`NarrowTransition.isNarrow` 在 unfold
      过程会变成 false，下一次 launch 自动不加 flag，Activity Embedding 接管）。
- [ ] PR#195 窄屏 120ms 快过渡：保留 —— `NarrowTransition.applyFastOpen/Close`
      未改动，冷启动和软返回都复用它。
- [ ] YUJ-267 Fix B onNewIntent 字段生命周期（persist-before-reset）：未动。
- [ ] 草稿 / 已读 / 未读 持久化：未动（走的还是 `persistOldChannelEditState`）。
- [ ] 退群 / logout / SystemBotsFallback 流程：未动（仍调 finish() → onDestroy 原路径）。

## 6. 分阶段交付（对齐 issue 要求）

> 本文档内含 Fix A + 返回优化 + trace 方法论，实现落在一个 feature branch 的 3 个
> commit 上，便于 reviewer 按节拍 rebase / revert：
>
> 1. `docs(perf): YUJ-298 narrow chat reuse — analysis & trace methodology`
> 2. `perf(android): YUJ-298 narrow ChatActivity reuse via REORDER_TO_FRONT (Fix A)`
> 3. `perf(android): YUJ-298 soft back + same-channel short-circuit`
>
> 如 ReviewBot / Yu 要求拆 3 个 PR，直接从 commit 拆分即可，无交叉依赖。
