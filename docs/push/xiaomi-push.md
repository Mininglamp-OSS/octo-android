# 小米推送（MiPush）接入流程与要点

> 调研任务：MOB-14（父任务 MOB-13 · Android 各厂商推送调研）
> 调研时间：2026-08-19
> 结论基线：OctoIM 现网代码（`wkpush` 模块 + octo-server `modules/webhook`）+ 小米澎湃 OS 开发者平台官方文档

---

## 0. TL;DR

OctoIM **已经完整接入小米推送**，客户端到服务端链路是通的，不需要从零接入。本文档的主要价值在第 6、7 节：**小米 2026 年消息分类新规已于 2026-08-01 正式生效**，现网实现有 4 个需要确认/补齐的点，其中 `channel_id` 未配置会直接导致下发失败（错误码 27001）或降级到"单设备单日仅 1 条"的默认通道。

---

## 1. MiPush 的技术定位

小米推送是 MIUI / HyperOS 的**系统级长连接通道**：长连接由系统 `com.xiaomi.xmsf`（推送服务框架）维护，而不是由 App 自己保活。因此：

- App 被杀、被冻结，仍然能收到通知栏消息 —— 这是厂商通道相对自建长连接的核心优势。
- 官方口径：设备联网情况下有效推送送达率 90%+。
- 代价是消息内容、频次、分类完全受小米的运营规则管控（见第 6 节）。

OctoIM 自己有 WuKongIM 长连接，厂商推送只在**App 离线/后台不可达**时由服务端兜底触发，两者是互补关系，不是替代关系。

---

## 2. 账号与密钥申请流程

1. 用小米开发者账号登录**小米澎湃 OS 开发者平台**（`dev.mi.com`）→ 管理中心 → PUSH 服务 → 应用列表。
2. 找到目标 App（按包名匹配，OctoIM 为 `com.mininglamp.octo`），点击"启用推送"。
3. 启用流程中会要求**选择并填写通知类目**，这一步最多可同时创建 8 个分类，审核周期 3–5 个工作日。
4. 启用后在"应用管理 → 应用信息"页拿到三个凭据：

| 凭据 | 用途 | OctoIM 中的位置 |
|---|---|---|
| `AppId` | 客户端 SDK 初始化 | `local.properties` → `XIAOMI_APP_ID` |
| `AppKey` | 客户端 SDK 初始化 | `local.properties` → `XIAOMI_APP_KEY` |
| `AppSecret` | **服务端**发消息鉴权 | octo-server `Push.MI.AppSecret`（不进客户端） |

> ⚠️ AppSecret 绝不能出现在 APK 里。OctoIM 的做法是正确的：客户端 `wkpush/build.gradle` 只注入 `XIAOMI_APP_ID` / `XIAOMI_APP_KEY` 两个 BuildConfig 字段，AppSecret 只存在于服务端配置。

5. 运营平台（发消息、看统计、管分类）是另一个域名：`admin.xmpush.xiaomi.com`。

---

## 3. 客户端接入（OctoIM 现状）

### 3.1 依赖方式

小米不提供 Maven 坐标，只能下载 AAR 本地引入。OctoIM 用独立 module 包住这个 AAR：

- `MyLibs/xiaomipush/MiPush_SDK_Client_7_9_2-C_3rd.aar`（当前版本 **7.9.2-C**，`-3rd` 表示第三方版即非 MIUI 内置版）
- `MyLibs/xiaomipush/build.gradle:1-2` —— 用 `artifacts.add("default", file(...))` 把 AAR 暴露成一个 project 依赖
- `wkpush/build.gradle:58` —— `implementation project(path: ':MyLibs:xiaomipush')`

这个包法比官方文档的 `flatDir` + `implementation(name:'...', ext:'aar')` 更干净，AAR 的 manifest 和 proguard 规则都能正常参与合并。

### 3.2 AndroidManifest

**关键事实：SDK 需要的组件和权限，AAR 自带 manifest 已经声明，不需要手写。** 实测解包 `MiPush_SDK_Client_7_9_2-C_3rd.aar` 的 `AndroidManifest.xml`，它自带：

- 权限：`INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` / `VIBRATE` / `POST_NOTIFICATIONS`
- 自定义权限：`${applicationId}.permission.MIPUSH_RECEIVE`（signature 级）及对应 `uses-permission`
- 组件：`XMPushService`（`:pushservice` 进程）、`XMJobService`（`:pushservice`）、`PushMessageHandler`、`MessageHandleService`、`UploadLogSDKService`、`PingReceiver`（`:pushservice`）、`LogUploadFileProvider`、`NotificationClickedActivity`

已在 `app/build/intermediates/merged_manifest/prodRelease/.../AndroidManifest.xml` 验证合并生效（第 109–112、857–916 行）。

**业务侧只需要手写自定义 Receiver**，见 `wkpush/src/main/AndroidManifest.xml:20-35`：

```xml
<receiver android:name=".push.XiaoMiMessageReceiver" android:exported="true">
    <intent-filter><action android:name="com.xiaomi.mipush.RECEIVE_MESSAGE" /></intent-filter>
    <intent-filter><action android:name="com.xiaomi.mipush.MESSAGE_ARRIVED" /></intent-filter>
    <intent-filter><action android:name="com.xiaomi.mipush.ERROR" /></intent-filter>
</receiver>
```

### 3.3 初始化时机与主进程约束

官方硬性要求：**必须只在主进程调用 `registerPush`**，且自定义 Receiver 所在进程要和调用 `registerPush` 的进程一致。因为 SDK 会拉起 `:pushservice` 进程，`Application.onCreate()` 会被执行两次。

OctoIM 满足这个约束，但不是靠官方 demo 的 `shouldInit()`，而是靠 Application 层统一的进程门禁：

- `app/src/main/java/com/octoim/app/TSApplication.kt:57-63` —— `processName == getAppPackageName()` 才走 `initAll()`
- `initAll()` 里才调 `WKPushApplication.getInstance().init(...)`（`TSApplication.kt:113`+，`initAll()` 内）

所以 `:pushservice` 进程起来时不会重复注册。**这一点在后续重构 Application 启动流程时必须保住**，否则会出现重复注册 / regId 抖动。

### 3.4 注册与通道选择逻辑

`wkpush/.../WKPushApplication.java` 的实际策略是 **FCM 优先，厂商通道兜底**：

- `getPushToken()` 先判断 Google Play Services 是否可用 + Firebase 是否初始化成功
- 可用 → 走 FCM，`device_type` 上报 `"FIREBASE"`
- 不可用 → 按 ROM 分发：`OsUtils.isEmui()` → 华为，`isMiui()` → `initXiaoMiPush()`，`isOppo()` → OPPO，`isVivo()` → vivo

`initXiaoMiPush()` 只有一行：

```java
MiPushClient.registerPush(context, BuildConfig.XIAOMI_APP_ID, BuildConfig.XIAOMI_APP_KEY);
```

两个前置条件（在 `getPushToken()` 中）：
1. 用户已登录（`WKConfig.getInstance().getUid()` 非空）—— 未登录不注册，避免拿不到归属用户的 regId
2. `BuildConfig.XIAOMI_APP_ID` 非空 —— 未配置密钥时静默跳过，不会崩

`registerPush` 是异步的，regId 通过 Receiver 回调拿到（`XiaoMiMessageReceiver.java:86` / `:124` 的 `onCommandResult` / `onReceiveRegisterResult`，判 `COMMAND_REGISTER` + `ErrorCode.SUCCESS`，取 `commandArguments.get(0)`），然后 `PushModel.registerDeviceToken(regId, bundleId, "")` 上报服务端。

`PushModel.registerDeviceToken` 在 `device_type` 传空时按 ROM 回填，MIUI 填 `"MI"`（`PushModel.java:55-69`，MIUI 分支在 `:63`），上报字段为 `{device_token, device_type, bundle_id}`。

### 3.5 混淆

`wkpush/consumer-rules.pro:17-19`：

```
-keep class com.xiaomi.** { *; }
-dontwarn com.xiaomi.push.**
```

覆盖了 SDK。注意官方还要求 keep 自定义 Receiver —— `XiaoMiMessageReceiver` 因为在 manifest 中声明，R8 默认会保留其类与无参构造，当前 `minifyEnabled false` 下也无风险；后续开启混淆时建议显式补一条 `-keep class com.chat.push.push.XiaoMiMessageReceiver { *; }`。

---

## 4. 服务端下发（OctoIM 现状）

代码在 octo-server `modules/webhook/push_mi.go`（只读参考仓库，本次未改动）。

- 注册：`modules/webhook/api.go:97-101`，`mi.PackageName` 非空时才把 `NewMIPush(mi.AppID, mi.AppSecret, mi.PackageName, mi.ChannelID)` 挂到 `DeviceTypeMI` 上 —— 即客户端上报的 `device_type="MI"` 决定路由。
- 下发接口：`POST https://api.xmpush.xiaomi.com/v4/message/regid`（form 表单），Header `Authorization: key=<AppSecret>`。
- 关键参数（`push_mi.go:66-82`）：

| 参数 | 现值 | 含义 |
|---|---|---|
| `registration_id` | 客户端上报的 regId | 单设备定向 |
| `pass_through` | `"0"` | **通知栏消息**（`1` 才是透传） |
| `notify_type` | `"-1"` | 响铃+震动+呼吸灯 |
| `notify_id` | `messageSeq` | 同 id 的通知会互相覆盖 |
| `payload` | URL-encode 后的内容 | 官方要求必须 urlencode |
| `extra.notify_effect` | `"1"` | 点击打开 Launcher Activity |
| `extra.sound_uri` | `android.resource://<pkg>/raw/newmsg` | 自定义提示音，`app/src/main/res/raw/newmsg.wav` 存在，路径有效 |
| `extra.badge` | badge 数 | 桌面角标 |
| `extra.channel_id` | `m.channelID`（**配置项**） | 消息分类，见第 6 节 |

- 返回校验：`checkMIPushResult`，`result != "ok"` 时取 `reason` / `description` 报错。

### 透传 vs 通知栏

| | 通知栏消息 `pass_through=0` | 透传消息 `pass_through=1` |
|---|---|---|
| 谁弹通知 | 系统弹，App 无需存活 | App 自己弹，需进程存活 |
| App 被杀 | 仍送达 | 基本收不到 |
| 回调 | `onNotificationMessageArrived` / `onNotificationMessageClicked` | `onReceivePassThroughMessage` |
| 受分类管控 | 是 | 是（同样计入配额） |

OctoIM 用 `pass_through=0` 是**正确选择** —— 厂商通道的意义就是 App 不在时也能到达，用透传等于放弃了这个优势。

---

## 5. 消息送达链路（端到端）

```
用户 A 发消息
  → WuKongIM 判定用户 B 离线
  → octo-server webhook 按 B 的 device_type 路由
  → device_type="MI" → MIPush.Push(regId, payload)
  → api.xmpush.xiaomi.com/v4/message/regid
  → 小米推送服务端 → MIUI/HyperOS 系统长连接（com.xiaomi.xmsf）
  → 系统直接弹通知栏（App 无需存活）
  → 用户点击 → notify_effect=1 → 拉起 Launcher Activity
```

**这里有一个产品层面的缺口**：`notify_effect=1`（NOTIFY_LAUNCHER_ACTIVITY）是小米的"预定义点击行为"，官方文档明确说明**预定义通知被点击时不会回调 `onNotificationMessageClicked`**。所以：

- 客户端 `XiaoMiMessageReceiver.onNotificationMessageClicked` 实际上**收不到**这类点击
- 该方法当前的实现体也只是把内容赋给几个成员变量，没有任何跳转逻辑（`XiaoMiMessageReceiver.java:62-70`）
- 结果：点小米通知只能进 App 首页，**无法直达对应会话**

要做会话直达，标准做法是服务端改 `extra.notify_effect=2`（NOTIFY_ACTIVITY）+ `extra.intent_uri` 指向承接 Activity，再在该 Activity 用 `Intent.getSerializableExtra(PushMessageHelper.KEY_MESSAGE)` 取 `MiPushMessage` 解析会话参数。这是**服务端 + 客户端联动改造**，不在本次调研的交付范围内，作为待办列出。

---

## 6. ⚠️ 2026 年消息分类新规（已生效，最高优先级）

**小米推送 2026 年消息分类新规已于 2026-08-01 正式运行**，这是本次调研最重要的发现。

### 6.1 三类通道及配额

| 通道 | 是否需申请 | 单日推送倍数 | 单设备单应用单日接收上限 |
|---|---|---|---|
| **默认**（未申请分类） | 否 | 1 倍 | **1 条** |
| **公信** | 需在运营平台申请 | 2 倍（持《互联网新闻信息服务许可证》为 3 倍） | 5–8 条 |
| **私信** | 需按新规申请 | 不限量 | **不限量** |

公信单日总量公式：`应用在 HyperOS/MIUI 上安装且通知开启数 × 倍数`，基数不足 10000 按 10000 计。限额以**送达量**核算。

### 6.2 OctoIM 属于私信

私信新规仅允许 **41 个消息类型**，分聊天消息 / 个人账户 / 个人资产 / 设备信息 / 订单及物流 / 工作信息等组。**"好友聊天"、"群组聊天"、"音视频通话" 明确在列** —— OctoIM 的 IM 消息推送天然属于私信，应当且只应当走私信通道。

官方合规示例（好友聊天）：标题 `[好友消息]小红`，描述 `吃了吗`。
明确违规：模糊消息、AI 发起的聊天、官方营销推送、转赞评互动。

### 6.3 私信的两个硬性要求

1. **必须携带 `channel_id`**：审核通过后平台自动生成，推送时不带 → 错误码 **27001 `invalid channel info!`**；带错/未审核同样 27001。
2. **必须携带模板 id**：2026-07-01 起新接入应用私信须用模板；**2026-12-31 前未完成私信模板接入将影响下发**。

> 现网 `push_mi.go` 只传了 `extra.channel_id`，**没有传模板 id**。这是需要在 2026-12-31 前闭环的合规项。

### 6.4 其他数值

- 单个应用最多申请 **30 个 channel**（启用流程页一次最多创建 8 个），命名建议中文 ≤20 字符，不得与应用名相同，审核 5 个工作日。
- 8 类可订阅公信（资讯订阅/直播预约/活动预约/赛事预约/作品预约/游戏内动态提醒/上线提醒/商品预约），完成订阅接入后管控标准同私信。客户端用 `MiPushClient.requestSubscribeChannel(...)`。OctoIM 用不上。
- **【个人订阅】通道于 2026-12-31 关停。**
- 违规惩罚：用私信通道发公信内容会被严格处罚；恢复需向 `mipush-permission@xiaomi.com` 提交盖章整改报告，自然年内叠加计次。

### 6.5 动态关闭率

- 阈值：月日均动态关闭率 **0.05%**（= 新增通知关闭数 ÷ 通知开启数，取当月每日均值）
- 超标 → 邮件提示 → 30 天整改期内仍超标 → 降额：普通应用降至 1.5 倍 / 单设备 3 条，持证应用降至 2.5 倍 / 单设备 5 条
- 次月 ≤0.05% 自动恢复；申诉需在收到邮件后 3 个工作日内提交，同一事件限 1 次
- 查询路径：运营平台 → 推送统计 → 用户数据 → 动态关闭率

---

## 7. 其他限制与配额

### 7.1 QPS（按通知开启数分级）

| 通知开启数 | QPS |
|---|---|
| ≥1000 万 | 3000 |
| ≥500 万且 <1000 万 | 2500 |
| ≥100 万且 <500 万 | 2000 |
| ≥10 万且 <100 万 | 1000 |
| <10 万 | 500 |

单请求最多携带 1000 个目标设备。超限返回 **200002**。

> OctoIM 目前是 `v4/message/regid` **单设备单请求**，在用户量上来后这是 QPS 瓶颈：1 QPS 只推 1 个设备，而 `v2/multi_messages/regids` 一次能带 1000 个。属于后续性能优化项，当前量级不紧迫。

### 7.2 配额可观测

- 响应体返回 `day_quota`（当日可下发总量）/ `day_acked`（当日已送达数）
- 也可 `GET https://api.xmpush.xiaomi.com/v1/trace/quota/get`（1 秒限 1 次，Header 同样是 `Authorization: key=<AppSecret>`）
- 超量：普通接口返回 **200001**；multi 接口不返回该码，改用 `channel_exceed_quota` 字段，如 `no_channel:2 a:3` 表示 2 条无 channel_id、3 条 channel_id=a 的消息被丢弃
- 回执 callback 需订阅 `callback.type=128`，extra 中含 `ack` 与 `quota`

### 7.3 SDK 侧上限

| 项 | 上限 |
|---|---|
| 单设备单 App 可订阅 topic | 30 个 |
| 单设备单 App 可设 alias | 15 个（超出覆盖最早的） |
| 一个 userAccount 对应设备 | 20 台（超出后最早注册设备失效） |
| 通知分类并存 | 10001 类 |
| 定时消息 | 未来 30 天内 |
| **regId 失效条件** | **设备超过 30 天未与小米 Push 服务器建立长连接** |

### 7.4 消息有效期与折叠

- 公信超 24 小时未点击消失，私信超 48 小时；有效期上限公信最长 1 天、私信最长 10 天
- MIUI 10+ 同 App 通知聚合为消息组，组内最多 10 条（超出删最旧）；折叠态默认展示 3 条，多余以 `+N` 标识；位于通知栏首位时展示 5 条

---

## 8. 常见坑与排查

### 8.1 收不到消息的排查顺序

1. **regId 是否上报成功** —— 看 `onReceiveRegisterResult` 是否 `ErrorCode.SUCCESS`，以及 `registerDeviceToken` 是否 200。注意 OctoIM 未登录不注册，未登录状态下永远拿不到 regId，这是预期行为。
2. **device_type 是否为 `MI`** —— 如果设备装了 GMS，`getPushToken()` 会走 FCM 分支，`device_type` 是 `FIREBASE`，服务端不会走小米通道。国行小米无 GMS，走 MiPush；国际版/刷了 GMS 的设备走 FCM。**排查时先确认走的是哪条通道，别在小米后台找一条根本没发过小米的消息。**
3. **服务端是否配了 `Push.MI.PackageName`** —— `api.go:97` 为空则整个 `DeviceTypeMI` 不注册，静默不推。
4. **`channel_id` 是否配置且审核通过** —— 空/错 → 27001；即使没报 27001，也可能被降级到默认通道（单设备单日 1 条），表现为"部分设备收不到"，极易误判为丢消息。
5. **配额是否触顶** —— 查响应的 `day_quota` / `day_acked`，或调 quota 接口。
6. **regId 是否已失效** —— 设备 30 天未连小米 Push 服务器则 regId 失效，需重新注册。长期不活跃用户的 token 要有清理机制。

### 8.2 具体坑

| 坑 | 说明 |
|---|---|
| **测试消息被拦截** | 消息内容含 "test" / "测试" 等字眼可能被小米判为非重要消息并拦截。联调时用真实业务文案。 |
| **`:pushservice` 进程重复初始化** | Application 会被实例化两次。OctoIM 靠 `TSApplication.kt:61-62` 的主进程判断规避，重构启动流程时不能破坏。 |
| **payload 未 urlencode** | 官方硬性要求。`push_mi.go:68` 已做 `url.QueryEscape`。 |
| **点击不跳会话** | `notify_effect=1` 是预定义行为，不回调 `onNotificationMessageClicked`，只能进首页。见第 5 节。 |
| **Android 13 通知权限** | `POST_NOTIFICATIONS` 已由 AAR 合并进 manifest，运行时申请在 `wkuikit/.../TabActivity.java:257`。用户拒绝后系统通道消息一样不展示，且会计入动态关闭率。 |
| **AppSecret 泄漏** | 只能在服务端。当前实现正确。 |
| **自启动/后台限制** | 通知栏消息由系统通道下发，**不依赖 App 存活**，MIUI 的后台冻结/自启动白名单不影响送达。真正受影响的是透传消息和 App 内自建长连接 —— 这也是 OctoIM 必须保留厂商通道兜底、不能只靠 WuKongIM 长连接的原因。 |
| **通知图标** | 可放 drawable `mipush_notification`（大图标）/ `mipush_small_notification`（小图标）定制；MIUI 上统一显示应用 icon。当前工程未提供这两个资源，走默认逻辑。 |

---

## 9. OctoIM 待办清单（按优先级）

| # | 事项 | 优先级 | 归属 |
|---|---|---|---|
| 1 | 确认小米运营平台已申请**私信 channel** 并审核通过，把 `channel_id` 配到 octo-server `Push.MI.ChannelID`；未配置将导致 27001 或降级到单设备单日 1 条 | **P0** | 服务端 / 运营 |
| 2 | 私信**模板 id** 接入（2026-12-31 前必须闭环，否则影响下发）；`push_mi.go` 当前未传 | **P0** | 服务端 |
| 3 | 通知点击直达会话：`notify_effect=2` + `intent_uri` + 承接 Activity 解析 `MiPushMessage` | P1 | 服务端 + 客户端 |
| 4 | 登出时调用 `MiPushClient.unregisterPush()` 并解绑服务端 token（`WKPushApplication.addListener()` 中 `wk_logout` 的 `unRegisterDeviceToken` 目前是注释状态），否则换账号会串推送 | P1 | 客户端 |
| 5 | 开启 R8 混淆后补 `-keep class com.chat.push.push.XiaoMiMessageReceiver { *; }` | P2 | 客户端 |
| 6 | 量级上来后从 `v4/message/regid` 切到 `v2/multi_messages/regids` 批量下发，缓解 QPS | P2 | 服务端 |
| 7 | 建立动态关闭率监控（阈值 0.05%），超标会被降额 | P2 | 运营 |

> 第 4 项是**功能性缺陷**而非合规项：A 账号登出后 regId 仍绑在服务端，B 账号登录同一设备时，A 的离线消息仍可能推到这台设备。

---

## 10. 关键代码索引

| 位置 | 内容 |
|---|---|
| `MyLibs/xiaomipush/build.gradle:1-2` | AAR 7.9.2-C 打包成 project 依赖 |
| `wkpush/build.gradle:22-23` | `XIAOMI_APP_ID` / `XIAOMI_APP_KEY` 注入 BuildConfig |
| `wkpush/build.gradle:58` | 依赖 `:MyLibs:xiaomipush` |
| `wkpush/src/main/AndroidManifest.xml:20-35` | `XiaoMiMessageReceiver` 声明 |
| `wkpush/src/main/java/com/chat/push/WKPushApplication.java` | `initXiaoMiPush()` + `getPushToken()` 通道选择 |
| `wkpush/src/main/java/com/chat/push/push/XiaoMiMessageReceiver.java` | regId 回调与上报 |
| `wkpush/src/main/java/com/chat/push/service/PushModel.java:55-88` | `device_type="MI"` 回填与 token 上报 |
| `wkpush/consumer-rules.pro:17-19` | MiPush 混淆规则 |
| `app/src/main/java/com/octoim/app/TSApplication.kt:57-63` | 主进程门禁（MiPush 多进程约束的保障） |
| octo-server `modules/webhook/push_mi.go` | 服务端下发实现（只读参考） |
| octo-server `modules/webhook/api.go:97-101` | `DeviceTypeMI` 路由注册（只读参考） |

---

## 11. 参考资料

- [推送服务 — 小米澎湃 OS 开发者平台](https://dev.mi.com/xiaomihyperos/ability/mipush)
- [推送产品说明（pId=1533）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1533)
- [Android 客户端 SDK 集成指南（AAR 版，pId=1544）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1544)
- [推送消息限制说明（pId=1656）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1656)
- [小米推送 2026 年消息分类新规（pId=2321）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2321)
- [服务端错误码参考（pId=1560）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1560)
- [推送服务启用指南（pId=1542）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1542)
- [小米推送模板接入指南（pId=2314）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2314)
- [小米推送订阅消息接入指南（pId=2320）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2320)
- 运营平台：`https://admin.xmpush.xiaomi.com/`
