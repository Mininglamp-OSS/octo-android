# YUJ-283 P-04 · App Startup Initializer 分阶段化

| 项 | 值 |
|---|---|
| 父审计 | [YUJ-283 android-performance-audit](./yuj-283-android-performance-audit.md) |
| Sprint | 2 |
| 工期 | 1d |
| 回归风险 | 低（仅调度时机变化，语义不变 + emoji hot path 加 DCL 守卫） |
| 基线 | develop `950a77d9` |

## 1. 背景

YUJ-283 审计 P-04 指出：`Application.onCreate` 里三个模块（`WKBase` / `WKUIKit` / `WKPush`）各自 `AppExecutors.io().execute(...)` 一个大 blob，把 Bugly、EmojiManager、RLottie、WKIM.init、sensitiveWords 解析、push token 握手一锅端地扔进后台，没有阶段划分，也没有「首屏依赖 vs 首屏不依赖」的取舍：

- 冷启 CPU 竞争明显：三个大任务在 `app-io-N` 池里串行跑，idle 之后才真正归零。
- EmojiManager.init() 只在表情面板和文本渲染里用到，完全没必要在启动链里预热。
- sensitiveWords / ProhibitWord 的同步要等服务端响应，放在启动链占用不必要的 CPU 时段。

## 2. 方案：AppStartup 三阶段

新增 `wkbase/src/main/java/com/chat/base/startup/AppStartup.java`：

| Phase | 调度方式 | 定位 | 典型任务 |
|---|---|---|---|
| **A** | 同步直写 | `Application.onCreate` 主线程 | WKMultiLanguage / WKApi / WKBase 基础单例 / density / registerComponentCallbacks |
| **B** | `postPhaseB(label, r)` → `AppExecutors.io().execute(...)` | 立即异步，首屏不阻塞但可能依赖 | Bugly、RLottie、**本地 sensitiveWords 解析 → WKIM.init → initIMListener**（三步在同一 Runnable 内串行）、push token |
| **C** | `postPhaseC(label, r)` → 主线程 `IdleHandler` + 5s fallback → `AppExecutors.io()` | 首帧之后再跑 | **EmojiManager.init**、sensitiveWords **网络同步**、ProhibitWordModel.sync、deleteFlameMsg |

### 2.1 设计要点

- **systrace 可观测**：每个任务包在 `Trace.beginSection("app-startup:<phase>:<label>")` 里，perfetto 里一眼能看到 `app-startup:B:wkim` / `app-startup:C-idle:emoji` 的执行时间轴。
- **fire-and-forget 语义**：单个任务抛异常不会拖垮其他阶段；对齐旧 `new Thread().start()` 的失败边界。
- **Phase-C fallback**：5s 内主线程如果一直没 idle（极端情况首屏持续 busy），fallback 会强制投递，避免任务无限期饿死。
- **一致性去重**：`OneShot` 保证 idle 和 fallback 谁先触发，另一个就吞掉，任务绝不会跑两次。
- **不是 androidx.startup.Initializer**：后者是 `Application.onCreate` 之前 ContentProvider 同步跑，本类是「onCreate 之内的分阶段调度」，职责正交。`SplitInitializer` 那一套（同步 Activity Embedding 规则注册）继续保留，没有冲突。

### 2.2 改动清单

| 文件 | 修改 | 说明 |
|---|---|---|
| `wkbase/src/main/java/com/chat/base/startup/AppStartup.java` | **+新增** | 三阶段调度器 |
| `wkbase/src/main/java/com/chat/base/emoji/EmojiManager.java` | +volatile initialized + synchronized init() + ensureInitialized() + hot path guard | 让 init 可以延迟到 Phase-C 而不破坏文本渲染的 `getPattern()` 热路径 |
| `wkbase/src/main/java/com/chat/base/WKBaseApplication.java` | 拆 1 blob → Phase-B × 2（bugly、rlottie）+ Phase-C × 1（emoji） | 冷启 CPU 竞争减轻 |
| `wkuikit/src/main/java/com/chat/uikit/WKUIKitApplication.java` | 拆 1 blob → Phase-B（**parseLocalSensitiveWords → wkim.init → initIMListener**，串行）+ Phase-C（sensitiveWords 网络同步 / prohibit / deleteFlameMsg） | 首屏依赖拆出来优先跑，本地敏感词缓存在 listener 注册前就绪，远端同步推迟到 idle |
| `wkpush/src/main/java/com/chat/push/WKPushApplication.java` | `AppExecutors.io().execute` → `AppStartup.postPhaseB("push-token", …)` | 统一入口 |

### 2.3 EmojiManager 双保险

原审计建议「EmojiManager 首次访问之前懒加载」。真实调用点扫描发现：

- `WKTextProvider.kt:1561`、`MoonUtil.java:83/133/215`、`SelectTextHelper.kt:149/361`、`WKUIChatMsgItemEntity.java:389` 都在**消息渲染**路径里读 `getPattern()`。
- 把 init 推到「打开表情面板」会让消息里的 emoji 表情在首次渲染时丢格式。

因此方案改为：`EmojiManager.init()` 本身做 DCL 幂等，所有 hot accessor 调用 `ensureInitialized()`。Phase-C 正常情况下在首帧前就跑完；极端情况（首帧之前就渲染了含 emoji 的消息），hot path 会同步触发 init，语义完全等价旧启动链，只是把 init 的「默认时机」挪到了 idle。

## 3. 预估收益

引用 YUJ-283 审计 §4.1：
- **Application.onCreate 主线程：-30-50ms**（Bugly/RLottie/WKIM/push 并行化 + Phase-C 去除启动链 CPU 占用）
- **idle 后首屏交互更顺**：EmojiManager.xml 解析 + Pattern 编译从启动链移到 idle 执行
- **profileability**：systrace 里 `app-startup:*` 区段直接映射到改动点，后续回归排查成本显著降低

> ⚠️ 真机 before/after trace 数据将在后续 QA 采集；Sprint 1 已约定「先打基础，数据后补」。

## 4. 验证

- `bash scripts/check-no-new-thread.sh` ✓
- `./gradlew :app:assembleDevDebug` ✓（本地 im-prod-server，38s）
- 回归保护：
  - WKIM.init + initIMListener 仍然在 Phase-B 立即执行，与旧 blob 的 wkim 块时序完全一致（上游 `TSApplication.initAll()` 里 `WKUIKitApplication.init` 之前已经 WKBase + Login + Scan 同步完成，不依赖 Bugly/RLottie）。
  - sensitiveWords 相关：唯一消费者是 `WKIMUtils.java:247`（消息接收时过滤）。**YUJ-304 修正**：本地 SP → SensitiveWords 反序列化（纯内存，毫秒级）从 Phase-C 前移到 Phase-B，并在同一 Runnable 内排在 `initIMListener` 之前，**保证 WKIM 入站监听注册的那一刻 `WKUIKitApplication.sensitiveWords` 已经可用**，彻底消除「冷启窗口内（最坏 5s fallback）敏感词过滤失效」的短窗口。网络同步（`MsgModel.syncSensitiveWords` + `ProhibitWordModel.sync`）仍留在 Phase-C，纯异步追赶。
  - EmojiManager：所有 `getPattern/getEmojiWithType/getDrawable/…` 入口都加 `ensureInitialized()` 守卫，双保险。

## 5. 白名单（本次不动）

| 文件 | 原因 |
|---|---|
| `AliveJobService.java:56` 的 `WKIMUtils.getInstance().initIMListener()` | Service 被 JobScheduler 唤醒路径，与 Application 启动链无关，不走 AppStartup。 |
| `TSApplication.kt:144` 前台回调里的 `WKIMUtils.getInstance().initIMListener()` | 前后台切换路径，不是启动链，保留现状。 |
| `SplitInitializer.kt` | `androidx.startup` Activity Embedding 规则注册，属于 Phase-A 之前的同步路径，不进 AppStartup。 |
| `WKSharedPreferencesUtil.prewarm` | YUJ-284/P-01 + YUJ-294 hotfix 已经完成优化；不重复处理。 |

—— Titan, 2026-05-03
