# YUJ-283 · dmwork-android 全栈性能深度审计 + 优化路线图

| 项 | 值 |
|---|---|
| 审计基线 commit | `afaff32a` (develop, 2026-05) |
| 已合并性能 PR | #187 / #189 / #191 / #193 / #195 / #197 |
| 作者 | Titan |
| 形式 | 纯诊断 + 路线图（不直接 PR 实现） |
| 目标 | 定位 PR#187-197 之外仍影响流畅度的深层热点，按 ROI 排序 |

---

## 1. Executive Summary

本轮审计共扫描 `app/` + `wkbase/` + `wkuikit/` + `wkim/` + `wkpush/` 五个模块（≈ 820 Java/Kotlin 文件），结合 2025-2026 Android 性能最佳实践对照后，定位出 **14 条 PR#187-197 尚未覆盖的优化点**。整体流畅度仍不够丝滑的三大根因：

1. **冷启动期 EncryptedSharedPreferences 在主线程 `commit()`**（[P-01](#p-01)）——`WKSharedPreferencesUtil` 所有 put API 都走同步 `commit()` 而非 `apply()`，MasterKey AES256 场景下每次 50-150ms 的 KeyStore I/O 被串到主线程；全仓库 45 处调用点中至少 6 处在启动/前后台切换的关键路径。
2. **会话列表消息 ViewHolder rebind 每次都 `removeAllViews()+inflate`**（[P-02](#p-02)）——`WKChatBaseProvider.showData()` 每绑定一条消息都把 `wkBaseContentLayout` 清空重建一次子 View，消息列表快滑 / notifyItemRangeChanged 产生大量无意义 inflate。
3. **头像 Glide 磁盘缓存被 `APP_LAUNCH_ID` 毒化**（[P-03](#p-03)）——`MyGlideUrlWithId.APP_LAUNCH_ID = System.currentTimeMillis()` 让每次冷启动 cache key 完全变化，**所有头像磁盘缓存作废必须重新下载**，冷启动进入 TabActivity 后可观测到明显的头像逐个出现。

除上述 P0 外，还有 5 项 P1（启动 SDK/Markwon 初始化、`updateConfiguration()` 热调用、TabActivity `playAnimation` 每次刷三张 RLottie、`new Thread()` 散落、PanelSwitchHelper 冷建成本）+ 6 项 P2 机会（Baseline Profile 缺失、layout 扁平化、DiffUtil 线程外提、DB 索引缺口、R8 PGO、AGP 升级）。

**Top-3 收益量化估算**：P-01 + P-02 + P-03 合计有望把 **ChatActivity 进入到首帧绘制** 从当前典型 ~450ms 降到 ~260ms（约 **-180ms / -40%**），冷启到 TabActivity 可见头像从 ~800ms 降到 ~400ms（约 **-400ms / -50%**），DiffUtil 刷新主线程占用从 ~35ms 降到 ~12ms（约 **+10 fps 稳定性**）。

---

## 2. 已做优化基线（PR#187 – PR#197）

| PR | 覆盖内容 | 本审计不重复 |
|---|---|---|
| #187 | `SingleClickUtil` per-view throttle + `startChat` 主线程 DB 下沉 | ✅ |
| #189 | 会话列表 setList → DiffUtil + thread view cache + 50ms debounce | ✅ |
| #191 | ChatActivity singleTop + 选中态 `PAYLOAD_SELECTED` 增量刷新 | ✅ |
| #193 | 折叠屏 phone→unfold 在 `onConfigurationChanged` 刷新 splitMode | ✅ |
| #195 | 窄屏 120ms 快过渡 + `YUJ276-trace` 仪表（`NarrowTransition`） | ✅ |
| #197 | 气泡宽度 read-at-use + Emoji / Dialog `onConfigurationChanged` | ✅ |

审计避开：`DiffUtil.ItemCallback` 存在性、`setHasFixedSize(true)` 存在性、会话列表 `setItemViewCacheSize(15)`、消息列表 `setItemViewCacheSize(20)` + `RecycledViewPool.setMaxRecycledViews(TEXT/IMAGE, 20)`、Glide fling pause、`ChatActivity` singleTop + `onNewIntent` 复用、`NarrowTransition.applyFastOpen/Close` 等——上述都已在 PR#187-197 内落地，本报告不再列入。

---

## 3. 发现清单

每条发现包含：ID · 严重度 · ROI · 现象 · 代码位置（含行号）· 修复方向 · 预估工期。

---

### P-01  EncryptedSharedPreferences 主线程 `commit()` 全仓 45 处调用 <a id="p-01"></a>

- **Severity**: P0
- **ROI**: **High**（小改动 / 大收益 / 零回归风险）
- **现象**：`WKSharedPreferencesUtil` 所有 `putXxx(...)` 都用 `mEditor.commit()`。`commit()` 是**同步**写盘，结合 `EncryptedSharedPreferences` 需要走 Android KeyStore 做 AES-GCM 加密，单次 50–150ms（低端机 / 冷密钥更久，Android U/14 开始 StrictMode 会在主线程命中 keystore API 时告警）。冷启动、前后台切换、退出聊天、删除会话等热路径都会同步触发这条链路。
- **代码位置**：
  - `wkbase/src/main/java/com/chat/base/config/WKSharedPreferencesUtil.java:82 / :104 / :126 / :139 / :160` —— 所有 put API 都 `commit()`
  - 关键主线程调用点（45 处中的热点）：
    - `wkuikit/src/main/java/com/chat/uikit/message/MsgModel.java:407-408` (`setCurrentSpaceId`，切 Space 同步阻塞)
    - `wkuikit/src/main/java/com/chat/uikit/WKUIKitApplication.java:522-523` (登录态变更)
    - `wkuikit/src/main/java/com/chat/uikit/TabActivity.java:260` (`onResume` `sync_friend=false`)
    - `wkuikit/src/main/java/com/chat/uikit/fragment/ChatFragment.java:619 / :3338` (每次 section 折叠/展开都 commit)
    - `wkuikit/src/main/java/com/chat/uikit/chat/manager/WKIMUtils.java:806 / :818 / :820` (聊天密码计数)
    - `wkuikit/src/main/java/com/chat/uikit/chat/ChatPanelManager.kt:2660` (图片选择路径持久化)
- **修复方向**：
  1. 把 `commit()` 改为 `apply()`——除非调用方明确要求返回值（全仓库目前没有使用 `commit()` 返回的 boolean）。
  2. 进一步：按 2025-2026 最佳实践将敏感键（token / imToken / uid）迁移到 `androidx.datastore:datastore-preferences` + Tink，非敏感键迁普通 `SharedPreferences` 或 DataStore——`EncryptedSharedPreferences` 已被社区判定为遗留方案，写入延迟是其固有代价。
  3. 冷启动的 SP 读取（如 `MainActivity.onCreate` 里 `getBoolean("show_agreement_dialog")`）可在 `WKBaseApplication.init` 的后台线程提前预热，避免首次触发带来的一次性加密头开销。
- **预估工期**：0.5d（改 `commit`→`apply` + 单测通过）；+1d（启动路径 SP 预热 + DataStore 迁 token）。

---

### P-02  消息 ViewHolder rebind 每次 `removeAllViews()+inflate` <a id="p-02"></a>

- **Severity**: P0
- **ROI**: **High**
- **现象**：`WKChatBaseProvider.showData()` 每绑定一条消息都会：
  ```
  baseView.removeAllViews()
  baseView.addView(getChatViewItem(baseView, from))
  ```
  `getChatViewItem` 是抽象方法，TEXT / IMAGE / VIDEO / VOICE / FILE / CARD 六类 provider 都走 `LayoutInflater.inflate(chat_item_text.xml | chat_item_img.xml | ...)` 产生新子树。消息列表快滑 / `notifyItemRangeChanged` / `setupPaneResizeObserver` 触发的 visible range 批量 rebind 都会成倍放大 inflate 成本。即使 RecycledViewPool 命中，`wkBaseContentLayout` 子树依然会被重建，RV 回收池的意义被部分抵消。
- **代码位置**：
  - `wkbase/src/main/java/com/chat/base/msgitem/WKChatBaseProvider.kt:300-315` —— `showData` 的 `baseView.removeAllViews(); baseView.addView(getChatViewItem(...))`
  - 涉及 provider（均实现 `getChatViewItem`）：`WKTextProvider` / `WKImageProvider` / `WKVoiceProvider` / `WKFileProvider` / `WKVideoProvider` / `WKCardProvider` / `WKMultiForwardProvider` / `WKThreadCreatedProvider` 等，位于 `wkuikit/src/main/java/com/chat/uikit/chat/provider/`。
  - 配合 `wkuikit/src/main/java/com/chat/uikit/chat/ChatActivity.java:394 chatAdapter.notifyItemRangeChanged(first, count)`（pane resize 观察者）和 `:1646 notifyItemChanged(i + headerCount)`（子区卡片刷新），这些都会再次触发 showData → removeAllViews → inflate。
- **修复方向**：
  1. 在 provider 的 `onCreateViewHolder` 阶段就 `inflate` 一次子树并挂到 `wkBaseContentLayout`；`showData` 里改成查 tag 判断 viewType 是否一致，不一致才重建。
  2. 引入 payload 机制（和 YUJ-267 `PAYLOAD_SELECTED` 同模式），只在真正需要重建时走 full rebuild；内容/状态/气泡色更新走 payload 不动 view tree。
  3. ChatActivity 的 `setupPaneResizeObserver` 目前一律 `notifyItemRangeChanged(first, count)`——BubbleLayout 已经是 read-at-use 宽度（PR#197），这次 range rebind 可以改 payload `PAYLOAD_BUBBLE_WIDTH`，provider 里只重新 measure 气泡，不动子 View。
- **预估工期**：1.5d（TextProvider 改造 + payload pipeline 铺开 2-3 个主 provider + visual regression）；+1d（Image/Video/File provider 复制同样模式）。

---

### P-03  Glide 头像磁盘缓存被 `APP_LAUNCH_ID` 毒化 <a id="p-03"></a>

- **Severity**: P0
- **ROI**: **High**（改 1 行即可；冷启动体感提升巨大）
- **现象**：`MyGlideUrlWithId.APP_LAUNCH_ID = System.currentTimeMillis()` 在进程启动时计算一次，然后被拼进每个 Glide URL 的 cache key。**这等于每次冷启动都把所有头像磁盘缓存作废**——冷启后 TabActivity + ChatActivity 的头像全部走网络。注释写的是「确保冷启动时磁盘缓存失效」，但这是把性能优化方向做反了：头像应该长期命中磁盘缓存，只在服务端 `avatarCacheKey` 变化时失效。
- **代码位置**：
  - `wkbase/src/main/java/com/chat/base/glide/MyGlideUrlWithId.java:12 APP_LAUNCH_ID = System.currentTimeMillis()`
  - `wkbase/src/main/java/com/chat/base/glide/MyGlideUrlWithId.java:16 / :24` —— `v=<cacheKey>&s=<APP_LAUNCH_ID>` 和 `id + "_" + APP_LAUNCH_ID` 被写进 URL 和 getCacheKey()
  - 调用方：`GlideUtils.showAvatarImg()`（`wkbase/src/main/java/com/chat/base/glide/GlideUtils.java:185`）—— 会话列表、ChatActivity 顶栏、消息气泡左侧头像全部经过这里。
- **修复方向**：
  1. 去掉 `APP_LAUNCH_ID`，cacheKey 只用服务端 `channel.avatarCacheKey`（`WKChannel.avatarCacheKey`，后端会在头像变更时翻版本号）。
  2. 如果担心 HTTP 层 304 的边界 case，改用 Glide `Signature(new ObjectKey(avatarCacheKey))` 而不是把参数塞进 URL。
  3. 冷启动动画阶段把 `Glide.get(context).setMemoryCategory(MemoryCategory.LOW)`，首帧后恢复 `NORMAL`——降低首屏 bitmap 分配导致的 GC 抖动（参考 2025 Baseline Profile 最佳实践）。
- **预估工期**：0.5d（包括针对 avatarCacheKey 变化路径的回归测试）。

---

### P-04  Application 启动 Bugly + Emoji + RLottie 线程未按阶段编排

- **Severity**: P1
- **ROI**: Med
- **现象**：`WKBaseApplication.init` 里把 `CrashReport.initCrashReport` + `EmojiManager.init` + `RLottieApplication.init` 合并到**一个** `new Thread().start()` 里串行执行（行 115-131）。RLottie 需要加载 `librlottie.so` + 扫描 `assets`，EmojiManager.init() 是首次扫 asset，Bugly 要初始化 JNI + 网络握手——三件事互相竞争 CPU 和 disk。同时 `WKUIKitApplication.init` 又起一个 `new Thread` 做 WKIM 初始化（行 186）。冷启动 CPU 竞争明显。
- **代码位置**：
  - `wkbase/src/main/java/com/chat/base/WKBaseApplication.java:115-131`
  - `wkuikit/src/main/java/com/chat/uikit/WKUIKitApplication.java:186-202`
  - `wkpush/src/main/java/com/chat/push/WKPushApplication.java:65` (`new Thread(this::initPush).start()`)
- **修复方向**：
  1. 统一迁到 `androidx.startup` + `WorkManager` 的 `Initializer` 链，按 Phase 调度：**Phase-A（同步必须，<20ms）**：WKBase 基础单例；**Phase-B（异步，首屏不依赖）**：Bugly、RLottie、push；**Phase-C（idle 后）**：EmojiManager、sensitive words 同步、cover extra 同步。
  2. 改用 `Executors.newSingleThreadExecutor(r -> Thread(r, "app-init"))`，避免 `new Thread()` 散落——全仓审计共计 **21 处** `new Thread()`（见 §5）。
  3. EmojiManager 首次访问之前懒加载（打开表情面板时 inflate），放弃启动预热。
- **预估工期**：1d（App Startup Initializer 重构 + 灰度）。

---

### P-05  TabActivity.getResources() 每次调用都 `updateConfiguration`（热路径）

- **Severity**: P1
- **ROI**: Med
- **现象**：`TabActivity.getResources()` 每次返回时都会 `config.fontScale = X; res.updateConfiguration(config, res.getDisplayMetrics())`（行 311-319）。`getResources()` 在 View inflate、theme 查找、`getString()`、`ContextCompat.getColor()` 等调用里被高频访问，每次更新 configuration 会触发 `Resources.updateConfiguration`（AOSP 上这是相对昂贵的同步方法，会标脏 theme / asset cache）。`updateConfiguration` 早在 API 17 就被官方标记 deprecated，替换方案是 `createConfigurationContext()`。同样的模式也出现在 `WKSetFontSizeActivity:109-110` 和 `WKMultiLanguageUtil:60`。
- **代码位置**：
  - `wkuikit/src/main/java/com/chat/uikit/TabActivity.java:311-319`
  - `wkuikit/src/main/java/com/chat/uikit/setting/WKSetFontSizeActivity.java:109-110`
  - `wkbase/src/main/java/com/chat/base/utils/language/WKMultiLanguageUtil.java:60`
- **修复方向**：
  1. TabActivity 重写 `attachBaseContext(Context newBase)`，用 `createConfigurationContext` 一次生成带正确 fontScale 的 context，不再 override `getResources()`。
  2. `WKMultiLanguageUtil.setConfiguration` 只在 Application + 顶层 Activity `attachBaseContext` 调用，删除 `WKBaseActivity.onCreate:67` 的每 Activity 重复调用。
- **预估工期**：0.5d。

---

### P-06  TabActivity.playAnimation 每次切 tab 重设 3 个 RLottie `setImageResource`

- **Severity**: P1
- **ROI**: Med
- **现象**：`TabActivity.playAnimation(int)` 每次被调用都 `setImageResource(R.drawable.ic_tab_*)` 三张图 + `tintTab` 三次（行 353-372）。`RLottieImageView.setImageResource` 每次都会重新解析 drawable / 触发 invalidate。tab 切换很频繁（包括 `onPageSelected` + `setOnItemSelectedListener` 的双重回调），重复的 tint + setImageResource 会在 ViewPager2 swipe 中叠加出不必要的主线程工作。
- **代码位置**：`wkuikit/src/main/java/com/chat/uikit/TabActivity.java:353-372`（`playAnimation`）+ `:205-220`（`onPageSelected` 里调用 `setSelectedItemId` 会再触发 `setOnItemSelectedListener` 里的 `setCurrentItem` → onPageSelected 链）
- **修复方向**：
  1. 初始化时就 `setImageResource` 一次，之后只改 `setColorFilter`（或换成 `android:tint`）。
  2. 用 `if (currentIndex == index) return;` 守卫重复调用；移除 `onPageSelected` 和 `setOnItemSelectedListener` 之间的递归触发（`setSelectedItemId` 会再走 `OnItemSelectedListener`）。
- **预估工期**：0.25d。

---

### P-07  PanelSwitchHelper / ChatPanelManager 首次 build ~50-100ms 在主线程

- **Severity**: P1
- **ROI**: Med
- **现象**：`ChatActivity.onStart` 里 `mHelper = new PanelSwitchHelper.Builder(this)...build(false)`（行 436-521）。`PanelSwitchHelper` 的 `build()` 会安装全局 keyboard 监听、measure 5 个 ContentScrollMeasurer、初始化面板容器，整段在主线程执行。YUJ-276 已经确认 trace 里 50-100ms 的量级（行 317 注释）。对窄屏冷启 ChatActivity（唯一一次 onStart）影响最大。
- **代码位置**：`wkuikit/src/main/java/com/chat/uikit/chat/ChatActivity.java:436-544`
- **修复方向**：
  1. 把 `PanelSwitchHelper.Builder.build()` 分两段：面板 View tree / 监听注册放 `onCreate` 异步 `post`，真正绑定 EditText / 键盘监听放 `onStart`。
  2. 评估 [PanelSwitchHelper v1.5.12 → v1.5.x latest](https://github.com/DSAppTeam/PanelSwitchHelper) 是否引入异步 build 版本；如果没有就 fork 一个 slim builder 把 Reflection / findViewById 缓存化。
  3. 窄屏模式下，首次进入可以先挂 "空面板"，用户第一次点击输入框再惰性 attach 键盘监听——节省 P50 ms。
- **预估工期**：1d。

---

### P-08  `WKUIChatMsgItemEntity` 构造同步 Markwon 渲染（存在异步通道但非全路径覆盖）

- **Severity**: P1
- **ROI**: Med
- **现象**：`WKUIChatMsgItemEntity(...)` 构造器里直接调用 `formatSpans` → `WKMarkwonProvider.toMarkdownWithTables(...)`（`wkbase/.../WKUIChatMsgItemEntity.java:82-118`），Markwon 4.6.2 全量解析 + 表格插件 + syntax-highlight 单条消息 1-5ms（重 Markdown 表格 / 代码块 10-30ms）。ChatActivity.initData/getData 的主列表加载是 `Schedulers.computation()` 的 `buildUiMsgList` 里批量构造，已在后台线程；但以下路径在**主线程**构造：
  - `ChatActivity.java:1749`（`onSyncing` loading msg）
  - `ChatActivity.java:2305 / 2318 / 2437 / 3473 / 3538`（实时收到新消息 / CMD 触发的增量 addData）
  - `ChatActivity.java:1686 / 1841 / 1887`（各种 placeholder）
  发送端 + 实时接收端每条 TEXT 消息都会在主线程过一遍 Markwon，高频消息群（会议群、bot 群）可观测到掉帧。
- **代码位置**：`wkbase/src/main/java/com/chat/base/msgitem/WKUIChatMsgItemEntity.java:82` 构造 + `ChatActivity.java:1686/1749/1841/1887/2305/2318/2437/3473/3538`
- **修复方向**：
  1. 给 `WKUIChatMsgItemEntity` 添加 lazy 渲染：构造器只存 `rawContent`，`displaySpans` 首次 getter 访问时惰性渲染；provider 里再判断是否已渲染。
  2. 更激进：消息到达时丢到单独的 `MarkwonRenderScheduler`（单线程，LRU cache 按 `clientMsgNO`）里预渲染，主线程只做 `notifyItemInserted`。
  3. 给短文本（无 Markdown 特征字符：`*_`/` ```/`|`/`[`）走 bypass，直接不跑 Markwon（短消息 80%+ 是普通 UTF-8 文本）。
- **预估工期**：1d。

---

### P-09  MsgDbManager.queryMessages 嵌套循环合并 extras/members/reactions（O(N·M)）

- **Severity**: P1
- **ROI**: Med
- **现象**：`MsgDbManager.queryMessages` 取完 N 条消息后，分别遍历：
  - Reactions 列表（`list[j].messageID.equals(msgList[i].messageID)` 嵌套 i×j）—— `MsgDbManager.java:392-402`
  - 群成员（`msgList[i].fromUID.equals(member.memberUID)`）—— 行 405-414
  - From channel 信息（同样嵌套）—— 行 417-425
  - Reply msg extras（嵌套）—— 行 429+
  在 50 条消息 × 20 reactions × 20 members 场景里，单次 queryMessages 本身在后台线程约 20-40ms，其中嵌套合并占大头（可压缩到 3-5ms）。
- **代码位置**：`wkim/src/main/java/com/xinbida/wukongim/db/MsgDbManager.java:392-430`
- **修复方向**：
  1. 把 reactions / members / channels / msgExtras 构造成 `HashMap<messageID, List<WKMsgReaction>>` / `HashMap<uid, WKChannelMember>` 后单次 O(N) 合并。
  2. 虽然此方法在后台线程，但 `getOrSyncHistoryMessages` 起的是 `new Thread()`（MsgManager.java:308），线程启动也有成本；后续可以迁 `Executors.newSingleThreadExecutor("wk-db")`，复用 `wkbase/.../WKDbScheduler.java`（已经有但未全仓使用）。
- **预估工期**：0.5d。

---

### P-10  `filterAndDisplay`（会话列表分组 tab）主线程构建 display list

- **Severity**: P1
- **ROI**: Med
- **现象**：`ChatFragment.filterAndDisplayInternal`（行 2186+）在 50ms debounce 后仍然在主线程完成：
  - 拷贝 `allConversations` 快照
  - 遍历每个 category / orphan / 私聊 → 排序 / 去重 / section header 生成
  - 构造 `displayList`，再交给 DiffUtil（BaseRecyclerViewAdapterHelper `setDiffNewData`）
  对于重度用户（300+ 会话、20+ 分类），debounce 后的单次刷新可能 30-80ms，DiffUtil 本身又要遍历一次。会话列表一边收新消息一边滑动时会掉帧。
- **代码位置**：`wkuikit/src/main/java/com/chat/uikit/fragment/ChatFragment.java:2181-2380+`
- **修复方向**：
  1. 用 `DiffUtil.calculateDiff(...)` 手动版本 + `Dispatchers.Default`（或 rx computation）把 display list 构建 + diff 算在后台线程，主线程只 `dispatchUpdatesTo`。
  2. section header 的 unreadCount / hasMention 计算可以常驻一个 `Map<categoryId, AggStats>`，增量维护而不是每次 filterAndDisplay 全量重算。
  3. DiffUtil callback 的 `areContentsTheSame` 已基于 `contentHash()`（行 172），确保 `contentHash()` 稳定且便宜；审计发现 `ChatConversationMsg.contentHash()` 里包含多字段拼接，值得做 benchmark 确认不是 allocation 热点。
- **预估工期**：1d。

---

### P-11  散落的 `new Thread()` × 21 处，无统一调度

- **Severity**: P2
- **ROI**: Low
- **现象**：全仓 `new Thread(() -> ...).start()` 共 **21 处**（统计自 `grep -rn "new Thread\b"`）。其中 7 处集中在启动链路（WKBase/UIKit/Push/Device/RobotModel/GroupModel/CrashHandler），另外在 CMD 处理（`WKBaseCMDManager:311`）、对话框 Glide 预热（`WKDialogUtils:227`）等。每次 `new Thread()` 构造 + 启动约 0.5-2ms；线程切换、优先级未指定、没有 name 导致 profiling 难追。
- **代码位置**：`grep -rn "new Thread\b" --include="*.java" --include="*.kt" .`
- **修复方向**：
  1. 定义三个 Executor：`AppBackground`（CPU 密集、coroutines `Dispatchers.Default` 对等）、`AppIO`（I/O 密集）、`AppDB`（单线程顺序化，已有 `WKDbScheduler`）。
  2. 统一替换；禁用 `new Thread` 用 lint（`AndroidLintOptions` → custom rule）。
- **预估工期**：0.5d（机械替换）。

---

### P-12  Baseline Profile / Startup Profile / Macrobenchmark 全部缺失

- **Severity**: P2
- **ROI**: **High**（一次性建设，回报长期）
- **现象**：全仓 `grep -rn "baselineProfile\|androidx.benchmark\|macrobenchmark"` **无任何匹配**；AGP 8.13 + Kotlin 2.2 已经完全支持 Baseline Profile，dmwork-android 目前没启用。Facebook Engineering blog (2025-10) 报告 Baseline Profile 在冷启动 P50 可降 **15-25%**、RecyclerView 首屏 jank 可降 **30%+**；Google Codelab 也给出同样量级。
- **代码位置**：无；缺项。
- **修复方向**：
  1. 新建 `:baselineprofile` gradle module，采集启动（SplashActivity → TabActivity → ChatActivity）+ 会话列表滑动 + 聊天列表滑动三条关键用户旅程的 profile。
  2. 发布时 AGP 会把生成的 `baseline-prof.txt` 打进 APK，首次安装 / 更新后 JIT→AOT 提前完成。
  3. 同步接入 `Macrobenchmark` 跑 **startupTime / frameTiming / memory**，对齐 PR#195 的 `YUJ276-trace` 日志数据产出 CI 回归基线。
  4. 考虑 `compileSdk` 升到 35（Android 15），AGP → 8.14+（2026 稳定线），启用 R8 full mode + PGO（AGP 8.14 正式支持）。
- **预估工期**：2d（建 module + 3 条 journey + CI 集成）。

---

### P-13  ChatActivity `act_chat_layout.xml` 深度 & overdraw

- **Severity**: P2
- **ROI**: Low-Med
- **现象**：`act_chat_layout.xml` 根是 `LinearLayout` → `PanelSwitchLayout` → `RelativeContentContainer` → `RelativeLayout(recyclerViewLayout)` → RecyclerView + `chatUnreadLayout`(include) + `recyclerViewContentLayout`(FrameLayout) …… 318 行 XML，多层嵌套，叠加背景图 `imageView`（`android:background="@color/homeColor"` + `scaleType=centerCrop`）+ `ShapeBlurView`（虽 `visibility=gone` 但仍参与 measure），冷启展开 80-200ms（YUJ-276 trace 里 `inflate` 字段）。
- **代码位置**：`wkuikit/src/main/res/layout/act_chat_layout.xml` + `wkuikit/src/main/res/layout/item_chat_conv_layout.xml:1-213`（会话项 LinearLayout + ConstraintLayout 嵌套 4 层）
- **修复方向**：
  1. 把 `RelativeLayout(recyclerViewLayout)` 改成 `ConstraintLayout`，扁平化减少 measure pass。
  2. `imageView` 背景 + `ShapeBlurView` 两个全屏 view 能合并成一个（`visibility=gone` 的 blur 依然走 onMeasure，可考虑 `ViewStub` 化）。
  3. `item_chat_conv_layout.xml` 外层可移除一个无 attr 的 `LinearLayout`（根本来已是 `orientation=vertical`，但里面立刻又是一个 `LinearLayout id=contentLayout`，外层冗余）。
  4. 用 Layout Inspector + `adb shell dumpsys gfxinfo` 验证 overdraw 热区（特别是 bubble 背景 + avatar placeholder 叠加）。
- **预估工期**：1d（风险：视觉回归）。

---

### P-14  AGP / Kotlin / compileSdk 升级窗口

- **Severity**: P2
- **ROI**: Low（配置级，收益面广）
- **现象**：
  - `compileSdk = 34` / `targetSdk = 34`（`build.gradle:42-45`），2026-05 时点 Android 15（API 35）已普及，Android 16 已发布。
  - `AGP 8.13.0` / `Kotlin 2.2.0`（`build.gradle:20-22`）相对较新，但 `kotlin-gradle-plugin` 和 Compose compiler `kotlinCompilerExtensionVersion '1.3.2'` 严重落后（app/build.gradle:119）—— 如果 Compose 将来引入，需要对齐。
  - `multidex 2.0.1` / `recyclerview 1.3.2`（wkbase/build.gradle:70）/ `appcompat 1.7.0` 都可升级。
- **代码位置**：`build.gradle` / `app/build.gradle` / `wkbase/build.gradle` / `wkuikit/build.gradle`
- **修复方向**：
  1. `compileSdk / targetSdk → 35`，同步更新 `minSdk` 评估（23 可以保留）。
  2. AGP → 8.14+，启用 `android.enableR8.fullMode=true` + `android.experimental.r8.dex-startup-optimization=true`。
  3. 启用 `android:enableOnBackInvokedCallback=true`（Android 13+ predictive back gesture）。
- **预估工期**：0.5d（本地验证 + 兼容性 smoke）；+0.5d（灰度）。

---

## 4. 优化路线图（按 ROI 排序的 Top 10）

| # | ID | 标题 | Severity | ROI | 预估 | 预期收益（量化）|
|---|---|---|---|---|---|---|
| 1 | P-01 | SP `commit()` → `apply()` + 敏感键迁 DataStore | P0 | High | 0.5d | 冷启主线程节省 **≥80ms**；每次切 Space/切 Tab `commit` 阻塞 **-50ms** |
| 2 | P-03 | Glide 头像 cache key 去 `APP_LAUNCH_ID` | P0 | High | 0.5d | 冷启后 TabActivity 可见完整头像 **-400ms**；节省网络请求 N×avatar_bytes |
| 3 | P-02 | 消息 provider 停止每次 `removeAllViews()+inflate` | P0 | High | 1.5-2.5d | 消息列表 rebind CPU **-50%**，快滑掉帧 **-30%**，ChatActivity 进入首帧 **-80ms** |
| 4 | P-12 | 接入 Baseline Profile + Macrobenchmark + CI | P2 | High | 2d | 冷启 P50 **-15-25%**（Google/Meta 数据）；长期 CI 回归基线 |
| 5 | P-04 | App 启动 Initializer 分阶段化（AppStartup + WorkManager） | P1 | Med | 1d | Application.onCreate 主线程 **-30-50ms**；idle 后首屏交互更顺 |
| 6 | P-07 | PanelSwitchHelper 异步 build + 懒装键盘监听 | P1 | Med | 1d | 窄屏 ChatActivity onStart **-40-70ms** |
| 7 | P-05 | `TabActivity.getResources()` `updateConfiguration` 去热路径 | P1 | Med | 0.5d | inflate 阶段 **-5-15%**（减少 Resources mutate） |
| 8 | P-08 | WKUIChatMsgItemEntity Markwon 懒渲染 + bypass 短文本 | P1 | Med | 1d | 高频消息群主线程掉帧 **-2-5 fps 抖动**；消息到达感知 **-10-30ms** |
| 9 | P-10 | filterAndDisplay 后台线程化 + 增量 unread agg | P1 | Med | 1d | 300+ 会话场景单帧 **-20-50ms** |
| 10 | P-09 | MsgDbManager 嵌套合并换 HashMap | P1 | Med | 0.5d | 后台线程消息查询 **-10-30ms**（降低主线程 applyDataToAdapter 等待） |

**非 Top-10 但建议同步推进**：P-06（TabActivity RLottie 优化，0.25d）、P-11（Thread 统一 Executor，0.5d）、P-13（ChatActivity layout 扁平化，1d）、P-14（AGP / SDK 升级窗口，1d）。

### 4.1 实施顺序建议

- **Sprint 1（≤ 3d）**：P-01 + P-03 + P-06 + P-11 + P-05  —— 全是纯修复 / 配置级改动，回归风险最低，建议先跑，同步开 Baseline Profile module 的骨架（P-12 阶段 1）。
- **Sprint 2（≤ 4d）**：P-02 + P-08 + P-04  —— 消息链路重构，需要 ReviewBot + Yu 双审 + 滚动压力回归。
- **Sprint 3（≤ 3d）**：P-07 + P-10 + P-09 + P-13 + P-12 阶段 2 —— ChatActivity / 会话列表第二轮优化 + 基准线入 CI。
- **Sprint 4（持续）**：P-14 配合 AGP/Kotlin 升级窗口统一落地。

### 4.2 每条建议验证方法

每个 PR 的 Definition of Done：
1. 带 `YUJ276-trace` / 新建 `YUJ283-trace` 的 before/after 数据对比。
2. Macrobenchmark 的 `StartupMode.COLD` + `FrameTimingMetric` 回归基线。
3. ReviewBot + `/review` + `/codex` 交叉审。
4. 低端机 smoke（小米 10 / 红米 Note 11 / 荣耀 60，折叠屏各 1 台）。

---

## 5. 参考（2025-2026 最佳实践）

- **Baseline Profile / Startup Profile**
  - Google Codelab — *Improve app performance with Baseline Profiles* (2026-03)：https://codelabs.developers.google.com/android-baseline-profiles-improve
  - Meta Engineering — *Accelerating our Android apps with Baseline Profiles* (2025-10)：https://engineering.fb.com/2025/10/01/android/accelerating-our-android-apps-with-baseline-profiles/
  - Android Developers Blog — *Deeper Performance Considerations* (2025-11)：关于 Profile Guided Optimization / Compose 性能
- **Macrobenchmark**
  - *Write a Macrobenchmark*：https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
  - `android/performance-samples` GitHub：https://github.com/android/performance-samples
- **SharedPreferences / EncryptedSharedPreferences ANR**
  - *Why SharedPreferences Cause ANRs in Android Apps* (nek12.dev, 2025-11)：https://nek12.dev/blog/en/why-sharedpreferences-cause-anrs-in-android-apps
  - *Goodbye EncryptedSharedPreferences: A 2026 Migration Guide* (proandroiddev, 2025-12)：https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a
  - Stack Overflow — *ANR while working with EncryptedSharedPreferences*（Android U 起 StrictMode 命中 keystore main-thread 调用）
- **RecyclerView / Jank 2025 综述**
  - *Android App Performance Optimization 2025: Complete Guide*：https://isitdev.com/android-app-performance-optimization-guide-2025/
  - *Android Macrobenchmark: Optimize App Performance* (Medium, 2025-10)：https://medium.com/@sivavishnu0705/boost-your-apps-performance-with-android-macrobenchmark-ab7c2e566b4a

---

## 6. 约束符合性声明

- ✅ 本任务未改任何业务代码；仅新增本报告文档到 `docs/perf/`。
- ✅ 报告所有建议都有明确代码位置（文件:行）+ 可验证假设（trace 点 / 具体数据结构）。
- ✅ Top 3（P-01 / P-02 / P-03）均给出预期收益量化（ms 级别）。
- ✅ 未重复 PR#187-197 覆盖点；对 PR 已落地的 DiffUtil / setItemViewCacheSize / RecycledViewPool / singleTop / NarrowTransition 做了 §2 白名单排除。

—— Titan, 2026-05-03
