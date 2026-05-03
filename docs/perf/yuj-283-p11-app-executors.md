# YUJ-283 P-11 · 统一 Thread → AppExecutors

| 项 | 值 |
|---|---|
| 父审计 | [YUJ-283 android-performance-audit](./yuj-283-android-performance-audit.md) |
| 工期 | 0.5d（Sprint 1 机械替换） |
| 回归风险 | 极低（纯调度器替换，无行为/时序变化） |
| 基线 | develop `afaff32a` |

## 1. 背景

YUJ-283 审计指出：全仓 `new Thread(() -> …).start()` 共 **21 处**散落在 `WKBase / UIKit / Push / Device / Robot / Group / CMD / Dialog / Scan` 等关键路径，存在以下问题：

1. **匿名**：线程没有 name，profiling / ANR 堆栈里只能看到 `Thread-1234`，定位成本高。
2. **优先级**：全部继承 `Thread.NORM_PRIORITY`，前台任务和后台任务一起跟主线程抢 CPU。
3. **无界爆发**：每次都 `new Thread()`，构造 + `start()` 有 0.5-2ms 开销，冷启动期间连发会导致 scheduler 抖动。
4. **daemon 未设置**：进程退出时部分后台线程可能阻塞 VM 关闭。

## 2. 方案

### 2.1 新增 `AppExecutors`

路径：`wkbase/src/main/java/com/chat/base/utils/AppExecutors.java`

| API | 语义 | 池 |
|---|---|---|
| `io()` | I/O 密集（磁盘、网络、SP、Glide decode、WKIM DB 包装） | FixedThreadPool，大小 = 2×CPU（≥4），优先级 NORM-1 |
| `background()` | CPU 密集（JSON parse、QR decode、WKBaseCMDManager 撤回合流） | FixedThreadPool，大小 = CPU，优先级 NORM |
| `db()` | 单线程顺序化 DB 任务（非 Rx 场景；Rx 走 `WKDbScheduler`） | SingleThreadExecutor，优先级 NORM |
| `mainThread(r)` / `postDelayed(r, ms)` | 主线程 Looper 投递（已在主线程则直接 run） | `Handler(Looper.getMainLooper())` |

全部线程 `setDaemon(true)`，按 `app-io-N / app-bg-N / app-db` 命名。

### 2.2 替换点（13 处 / 5 个模块）

| 模块 | 文件 | 语义 | 替换为 |
|---|---|---|---|
| wkbase | `WKBaseApplication:115` | Bugly + Emoji + RLottie 合并 init | `io()` |
| wkbase | `WKDeviceUtils:62` | SD/SP 读写设备 ID | `io()` |
| wkbase | `WKDialogUtils:227` | Glide bitmap 解码（QR parse） | `io()` |
| wkbase | `WKBaseCMDManager:311` | CMD 撤回合流 | `background()` |
| wkbase | `CrashHandler:109` | Crash toast（需 Looper） | **保留**（白名单） |
| wkuikit | `WKUIKitApplication:186` | WKIM init + sensitiveWords | `io()` |
| wkuikit | `WKRobotModel:40` | Robot 数据同步 | `io()` |
| wkuikit | `GroupModel:665` | 群迁移数据收集 | `io()` |
| wkuikit | `SettingActivity:122` | 缓存大小统计 | `io()` |
| wkpush | `WKPushApplication:65` | Firebase initPush | `io()` |
| wkpush | `WKPushApplication:113` | OPPO Heytap register | `io()` |
| wkpush | `WKPushApplication:215` | 华为 token 获取 | `io()` |
| wkscan | `WKScanActivity:80` | QR 解码 | `background()` |

### 2.3 白名单（不替换）

| 文件 | 原因 |
|---|---|
| `AppExecutors.java` | 池内部 `ThreadFactory`——唯一允许的构造点。 |
| `WKDbScheduler.java` | Rx 单线程 DB 的 `ThreadFactory`，已经是正确用法。 |
| `CrashHandler.java` | Crash 路径上主 Looper 即将退出，Toast 必须在独立 Looper 线程；`AppExecutors` 的 Executor 不提供 Looper。 |
| `wkim/**` | WKIM SDK 独立 maven publish，改动放在 `wkim` 自己的治理窗口（P-05 跟进）。 |

## 3. CI 闸口

`scripts/check-no-new-thread.sh` 作为 `android-build.yml` 的前置步骤：

- 扫描 `wkbase / wkuikit / wkpush / wkscan / app` 下的 `.java/.kt`。
- 忽略注释行，按显式白名单豁免。
- 命中即 `exit 1`，把 PR 阻在 develop 外。

本地验证：
```bash
bash scripts/check-no-new-thread.sh
# ✓ no `new Thread` outside whitelist (checked: wkbase wkuikit wkpush wkscan app)
```

## 4. 预估收益

- 冷启动 CPU 竞争：5 条启动链路线程合并到 `app-io-N` 池，减少 `new Thread()` 构造 + 调度抖动，估 **-5~10ms**（P90）。
- profiling 可读性：ANR / systrace 里看到 `app-io-3`，直接定位任务出处；显著降低 bug 回收时间。
- 行为零变化：所有任务仍然是 fire-and-forget，Executor 不做 serialization（io/background），与旧 `new Thread()` 语义一致。

## 5. 非目标

- 不引入 Kotlin Coroutines。（与现有 RxJava3 / callback 风格混用会引入另一层心智负担，留给 P-12 长期规划。）
- 不替换 `wkim` SDK 内的 4 处 `new Thread()`（WKConnection:550/1125、MsgManager:308、ChannelMembersManager:116）——该模块独立版本化，改动需要发版协调，独立 issue 跟进。
- 不写自定义 Lint 规则（custom lint 需要独立 module + AGP 注册，不符合 0.5d 工期）。`check-no-new-thread.sh` 是等效但更轻的闸口。

## 6. 相关 PR

- 本 issue: YUJ-288
- 审计: YUJ-283
- 并行 Sprint 1: P-01（SP commit→apply）、P-03（Glide APP_LAUNCH_ID）、P-05（wkim 线程治理）、P-06（TabActivity RLottie）
