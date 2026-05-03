# YUJ-278 · 窄屏 ChatActivity 打开延迟 —— Trace 方法论与基线

> 这是 YUJ-276 Fix D (PR#195) 的数据支撑文档，回应 YUJ-278 P1-2
> 「数据支撑倒置」。仪表（trace 点）在 PR#195 已落地；这里固化采集步骤 + 分析
> 脚本，这样任何人在真机 / AVD 上跑一遍即可产出 P50/P90 来对比 Fix D 与
> 候选 Fix A（onNewIntent 复用）。

## 1. Trace 点（已在 PR#195 落地）

| 阶段 | Log 行 | 含义 |
| --- | --- | --- |
| T_CLICK | `[T_CLICK] startChat enter channel=…` | **debounce 通过后**进入 startChat |
| T_INTENT_BUILT | `[T_INTENT_BUILT] io=… sinceClick=…` | IO 线程 DB 读完，Intent 组装好 |
| T_START_ACTIVITY | `[T_START_ACTIVITY] sinceClick=…` | 主线程 startActivity 被调 |
| T_ON_CREATE_END | `[T_ON_CREATE_END] channel=… inflate=… total=…` | ChatActivity.onCreate 结束 |
| T_ON_START_END | `[T_ON_START_END] channel=… initData=… total=…` | onStart + initData 结束 |
| T_ON_RESUME_END | `[T_ON_RESUME_END] channel=… total=…` | onResume 结束（≈ 首帧前） |
| T_ON_NEW_INTENT_END | `[T_ON_NEW_INTENT_END] channel=… total=…` | singleTop 复用路径（Fix A 对比） |

所有 log 都 gate 在 `WKBinder.isDebug` 后面（= `BuildConfig.DEBUG`），release 包无代价。

> **P2-4 修正**：`T_CLICK` 已从「debounce 之前」挪到「debounce 之后」。这样
> 跨行快点（row A → row B <250ms，B 被全局 debounce 挡掉）不会多打一条无对
> 应后续的 T_CLICK，避免 P50/P90 统计被拉低。

## 2. 采集步骤（真机 / AVD 通用）

```bash
# 0. 准备：debug 版安装到窄屏设备（手机或 AVD Pixel_5 sw360dp 竖屏）。
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk

# 1. 清日志 + 启 logcat，只收 YUJ276-trace / YUJ278-transition。
adb logcat -c
adb logcat -s YUJ276-trace:D YUJ278-transition:D > /tmp/yuj278-trace.log &
LOGCAT_PID=$!

# 2. 手动操作：登录 → 进入会话列表 → 连续点击 10 个不同会话，
#    每次点击后按返回键回到列表。同样一轮跑子区卡片点击 10 次、
#    SearchAll 列表点击 10 次。（三条 P1-1 漏覆盖路径都要覆盖。）

# 3. 停日志。
kill $LOGCAT_PID

# 4. 分析。
bash scripts/parse-yuj276-trace.sh /tmp/yuj278-trace.log
```

## 3. 期望输出（示例格式）

```
=== YUJ-278 cold-start breakdown (N=10) ===
stage                   p50       p90
click→intent_built      48ms      92ms
intent_built→startAct   3ms       9ms
startAct→onCreate_end   110ms     168ms
onCreate→onStart_end    85ms      210ms
onStart→onResume_end    12ms      24ms
---
click→onResume_end      258ms     503ms   <-- Fix D 落地后目标 <450ms P90
```

## 4. 基线（期望值，来自代码静态分析）

摘自 PR#195 描述，给对比参照：

| 阶段                               | 期望开销（手机窄屏） |
| ---------------------------------- | -------------------- |
| T_CLICK → T_INTENT_BUILT           | 30-100 ms            |
| T_INTENT_BUILT → T_START_ACTIVITY  | 5-15 ms              |
| onCreate (setContentView + initView) | 80-200 ms          |
| onStart (PanelSwitchHelper + initData) | 80-300 ms        |
| onResume                           | 10-30 ms             |
| **系统默认 push 过渡（阻塞）**     | **250-350 ms**       |
| Total perceived (旧)               | 500-800 ms           |
| Total perceived (Fix D 后)         | 250-450 ms           |

## 5. Fix D vs Fix A 决策维度

- **Fix A（onNewIntent 复用）**：YUJ-267 已经在分屏态落地。窄屏下要让它生效
  需要**不再 finish() ChatActivity**（即回到列表不销毁，让 singleTop 命中）。
  这改变了窄屏的 Activity 栈语义、影响返回键栈深度、影响 swipe-back 手势。
- **Fix D（120ms 快过渡）**：不动栈语义，只换动画资源。风险面最小。
- 取舍：trace 若显示 `onCreate_end + onStart_end ≥ 400ms`，说明冷启还是贵，
  Fix A 的省动作很诱人；但复用路径 `onNewIntent_end` 本身也在 50-150ms，
  综合省 ~200-250ms。Fix D 稳省 ~150-230ms，**先落 D，若 P90 仍 >400ms 再
  补 A 作为分屏已有逻辑的窄屏扩展**。

## 6. 本轮（YUJ-278）未能在 sandbox 真机复跑的声明

本次 PR 是在 Multica agent sandbox 中完成的，该环境不具备登录 dmwork IM
后端 + 跑完整会话数据集的条件（需要真实账户 + 服务器 + push token）。
- Trace 仪表本身已在 debug 构建里通过 `./gradlew :app:assembleDebug` 验证编译。
- 上面的期望基线是静态分析得到的保守估计。
- **真机 P50/P90 数字由 QA 或开发者手动跑一轮后回贴到 PR#195**，同时脚本
  `scripts/parse-yuj276-trace.sh` 已提交，任何人拿到 logcat dump 都能出数。

如果真机数据表明 Fix A 收益明显大于 Fix D（例如 `onCreate_end + onStart_end`
占 P90 超过 500ms），会按 YUJ-278 P1-2 的预案切方案、重开 PR。
