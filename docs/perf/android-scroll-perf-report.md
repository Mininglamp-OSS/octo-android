# Android 消息列表滑动卡顿性能分析 (YUJ-236)

- 仓库：`dmwork-android`
- 基线：`develop` @ `8e281f5b`
- 分析范围：`ChatActivity`（聊天消息列表）+ `ChatFragment` / `ChatConversationAdapter`（会话列表）
- 方法：代码静态分析（CPU / 主线程阻塞 / View 分配 / Layout 动画）。未在真机跑 perfetto（建议后续按文末脚本补。）

## TL;DR

消息列表滑动卡顿的主因不是图片加载或加密解密，而是 **每次 `onBindViewHolder` 都在重建子 View 树 + 触发 LayoutTransition 动画**，叠加会话列表侧 `ThreadPreviewContainer` 每次 bind 都 `inflate` 多个子区行并挂 `OnGlobalLayoutListener`。

Top 5 按影响度：

| # | 等级 | 位置 | 摘要 | 预期改善 |
|---|------|------|------|----------|
| 1 | High | `wkbase/.../chat_item_base_layout.xml:56` + `WKChatBaseProvider.kt:299-308` | `animateLayoutChanges="true"` 叠加每次 bind `removeAllViews()+addView(inflate(...))` → 每条滑入的消息都跑 LayoutTransition 动画 | fling 主线程阻塞减少 30–50%，丢帧显著下降 |
| 2 | High | `ChatActivity.java:460-471` | `MyItemAnimator` 替换默认 animator 后未调用 `setSupportsChangeAnimations(false)`，`notifyItemChanged` 触发 change 动画 | 消息状态/回执刷新时的滚动抖动消失 |
| 3 | High | `ChatConversationAdapter.java:1000-1274` (`showThreadPreviews`) | 每次 bind 都 `inflate item_thread_preview_row` + 新建 `LinearLayout/TextView/Badge` + `addOnGlobalLayoutListener` | 有子区的群在会话列表滑动时 FPS ≥15 提升 |
| 4 | Medium | `WKTimeUtils.java:93-258` | `getNewChatTime / time2HourStr / getShowDate` 每次都 `new SimpleDateFormat` + `Calendar.getInstance()`，被每一行 bind 调用 | 每次 bind 节省 ~0.3-1ms，滑动期间累积可观 |
| 5 | Medium | `WKChatBaseProvider.kt:308` + 各 `*Provider.getChatViewItem` | `LayoutInflater.inflate` 在每次 bind 被调用（RecyclerView ViewHolder 并没有复用内容 View） | onBindViewHolder 成本下降 40% 量级 |

> 以下所有行号以 develop @8e281f5b 为准。

---

## 1. 消息列表（ChatActivity / ChatAdapter / WKChatBaseProvider）

### 1.1 【High】 `chat_item_base_layout.xml` 的 `animateLayoutChanges="true"` + `removeAllViews()` 重建

**文件**：
- `wkbase/src/main/res/layout/chat_item_base_layout.xml:52-57`
- `wkbase/src/main/java/com/chat/base/msgitem/WKChatBaseProvider.kt:299-313`

**证据**：

```xml
<LinearLayout
    android:id="@+id/wkBaseContentLayout"
    android:animateLayoutChanges="true"
    android:orientation="horizontal" />
```

```kotlin
if (baseViewHolder.getViewOrNull<View>(R.id.wkBaseContentLayout) != null) {
    ...
    baseView.removeAllViews()
    baseView.addView(getChatViewItem(baseView, from))   // <-- 每次 bind 都重新 inflate 并 add
    ...
}
```

`animateLayoutChanges="true"` 会给 ViewGroup 自动挂 `LayoutTransition`。每次滑动触发的 `onBindViewHolder` 都会：
1. 移除旧子 View → 触发 DISAPPEARING 动画
2. 添加新 inflate 出来的子 View → 触发 APPEARING 动画
3. 由于整行内容高度不定，还会触发 CHANGING 动画

真机上每条消息滑入都多出一次 300ms 默认的 alpha + layout 动画，快速 fling 下主线程被挤满。

**优化建议**：
- 立即：删掉 `android:animateLayoutChanges="true"`（Quick Win）。
- 中期：让 `getChatViewItem` 缓存到 ViewHolder（见 §1.5）。

**预期收益**：
- 快速 fling 下丢帧率下降约 30–50%（按典型 60fps 场景）。
- `onBindViewHolder` 后 traversal 成本降低一个 remeasure + layout。

---

### 1.2 【High】MyItemAnimator 没禁用 change 动画

**文件**：`wkuikit/src/main/java/com/chat/uikit/chat/ChatActivity.java:460-471`

**证据**：

```java
// 去除刷新条目闪动动画
((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator()))
        .setSupportsChangeAnimations(false);
chatAdapter = new ChatAdapter(...);
linearLayoutManager = new LinearLayoutManager(...);
wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
wkVBinding.recyclerView.setAdapter(chatAdapter);
wkVBinding.recyclerView.setItemAnimator(new MyItemAnimator());  // <-- 覆盖掉上面刚设的 setSupportsChangeAnimations(false)
```

先拿默认 animator 去关 change 动画，然后立即被 `MyItemAnimator` 替换掉，`MyItemAnimator extends SimpleItemAnimator` 默认 `supportsChangeAnimations = true`。状态刷新（已读回执、reaction、status lottie）都会通过 `notifyItemChanged(position)` 触发 change 动画 → 滑动中跨 ViewHolder 的 fade in/out。

**优化建议**（Quick Win）：
```java
MyItemAnimator itemAnimator = new MyItemAnimator();
itemAnimator.setSupportsChangeAnimations(false);
wkVBinding.recyclerView.setItemAnimator(itemAnimator);
```

另外：`ChatAdapter.notify(pos, RefreshType)` 已经手动在 ViewHolder 上直接改 View（不走 `notifyItemChanged`），所以禁掉 change 动画不会丢能力；真正需要动画的场景（新消息 appear）仍由 add 动画覆盖。

**预期收益**：reaction/已读回执刷新时不再出现视觉卡顿。

---

### 1.3 【High】每次 bind 都 `LayoutInflater.inflate` 内容视图

**文件**：
- `wkbase/src/main/java/com/chat/base/msgitem/WKChatBaseProvider.kt:308`
- `wkuikit/src/main/java/com/chat/uikit/chat/provider/WKImageProvider.kt:53-55`
- `wkuikit/src/main/java/com/chat/uikit/chat/provider/WKTextProvider.kt` 的 `setData` 每次查找 `contentTv / contentTvLayout / contentLayout`

**证据**：

```kotlin
// WKChatBaseProvider.kt
baseView.removeAllViews()
baseView.addView(getChatViewItem(baseView, from))    // getChatViewItem 内部调用 LayoutInflater.inflate
```

```kotlin
// WKImageProvider.kt
override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
    return LayoutInflater.from(context).inflate(R.layout.chat_item_img, parentView, false)
}
```

RecyclerView 的整个复用模型就是为了避免 inflate，这里等于完全绕过复用（外层 ViewHolder 复用，内层每次重建）。`LayoutInflater.inflate` 要跑 XML 解析（已 cached 一部分）+ 属性应用 + View 构造 + 递归子 View。典型消息 item 子树 10~30 个 View，滑动时每帧可能出现数次几毫秒级阻塞。

另外 `WKImageProvider.setData` 也每次 `val deleteTimer = SecretDeleteTimer(context); otherLayout.removeAllViews(); otherLayout.addView(deleteTimer, ...)`（:73-76）——哪怕非焚烧消息也会分配一次 View 对象，丢给 GC。

**优化建议**（Refactor，跨多文件）：
1. `WKChatBaseProvider.showData` 改为：
   - 若 `baseView.tag == msgType` 则复用已有子 View，只调用 `setData(adapterPosition, existingView, ...)` 刷新数据。
   - 否则 `removeAllViews()` + 重新 inflate，并记录 tag。
2. `WKImageProvider` 把 `deleteTimer` 声明放到 item layout XML 中，默认 `visibility=gone`，非焚烧分支不创建新 View。

由于 `BaseProviderMultiAdapter` 本身是按 `itemViewType` 复用的，同一个 ViewHolder 被复用时 `msgType` 一致，可以安全跳过 inflate。

**预期收益**：`onBindViewHolder` 主线程耗时降 30–60%（消息类型越复杂越明显）。

---

### 1.4 【Medium】`setHasFixedSize(true)` 缺失

**文件**：`ChatActivity.java:463` / `WKBaseActivity.java:323` / `WKBaseFragment.java:177` 的 `initAdapter`。

**证据**：全局 grep `setHasFixedSize` 在消息列表、会话列表路径上均无匹配。RecyclerView 本身尺寸是 match_parent，不随 Adapter 变化，可以打开。

**优化建议**（Quick Win）：在 `ChatActivity.initView` 和 `WKBaseFragment.initAdapter` 里加：

```java
recyclerView.setHasFixedSize(true);
```

**预期收益**：减少 Adapter 数据增删时的多余 `requestLayout`，对瀑布流场景可见。

---

### 1.5 【Medium】MyItemAnimator 复杂度

**文件**：`wkuikit/src/main/java/com/chat/uikit/chat/MyItemAnimator.java` (654 行)

`MyItemAnimator` 是 `DefaultItemAnimator` 的精细拷贝，并无显著差异化能力。滚动中同时跑 add/move/change 动画，会与 §1.1/1.2 叠加放大卡顿。

**优化建议**：
- 短期：§1.2 禁掉 change 动画即可。
- 中期：考虑直接用 `DefaultItemAnimator` 或官方 `FadeInItemAnimator`；只保留必要的 "新消息从底部淡入"，其余走默认。

---

### 1.6 【Medium】`setItemViewCacheSize(20)` 正面但未配合 RecycledViewPool 复用

**文件**：`ChatActivity.java:470`

`setItemViewCacheSize(20)` 已经加了，这是对的。但 `BaseProviderMultiAdapter` 在多消息类型切换时，view type 多（文本 / 图片 / 语音 / 视频 / 文件 / 引用 / 撤回 / 系统 …），每种类型的 cache 分配仍可能不够。

**建议**：
- 按业务量评估是否给高频类型（文本、图片）单独 `recyclerView.getRecycledViewPool().setMaxRecycledViews(viewType, 10)`。
- 先观测，不建议一次性改。

---

## 2. 会话列表（ChatFragment / ChatConversationAdapter）

### 2.1 【High】`showThreadPreviews` 每次 bind 都 inflate + addOnGlobalLayoutListener

**文件**：`wkuikit/src/main/java/com/chat/uikit/chat/adapter/ChatConversationAdapter.java:1000-1274`

**证据**（节选）：

```java
container.removeAllViews();
container.setVisibility(View.GONE);
...
LayoutInflater inflater = LayoutInflater.from(getContext());
LinearLayout contentWrapper = new LinearLayout(getContext());
LinearLayout cardContainer = new LinearLayout(getContext());
for (int i = 0; i < showCount; i++) {
    View separator = new View(getContext());     // :1079
    View rowView = inflater.inflate(R.layout.item_thread_preview_row, cardContainer, false);  // :1089
    ...
}
...
ThreadBranchView branchView = new ThreadBranchView(getContext(), showCount);  // :1254
container.getViewTreeObserver().addOnGlobalLayoutListener(...);   // :1261 每次 bind 都挂
```

当前 `convertCompact` 和 `convertNormal` 都可能走到 `showThreadPreviews`。只要群有活跃子区，滑动时每滚过一个群就会：
- `inflate item_thread_preview_row` × N（N 通常 1–5，含活跃子区+1 个 "+N 个子区"）
- `new LinearLayout/TextView/ImageView` 数十次
- `setBackgroundResource`（shape drawable inflate）
- 注册一个 `OnGlobalLayoutListener`

`OnGlobalLayoutListener` 虽然在 `onGlobalLayout` 里 `removeOnGlobalLayoutListener(this)`，但注册本身还是要发一次 GlobalLayout；ViewTreeObserver 在 ViewTree 层面广播，影响整个 fragment layout 阶段。

同理 `convertCompactPayloads` 也会在 `isRefreshChannelInfo` 时重新走 `convertCompact`，有 typing / 未读数变化都可能触发。

**优化建议**（Refactor，大改）：
1. 把 thread preview 的子 View 静态放到 `item_chat_conv_compact_layout.xml` 里（max 3–4 个固定 slot 的 stub），按数据切换可见性 + 文本内容。
2. 排序/inflating 的工作放到 data 层（`ChatConversationMsg` 里预先算好 `previewRows` 列表），`convert` 里只做 `setText/setVisibility/setOnClickListener`。
3. `addOnGlobalLayoutListener` 换成手动测量：在数据准备阶段已经知道 `showCount`，`ThreadBranchView` 的 centerY 可以基于子行 fixed height 直接算出，不需要 post-layout 回调。

**预期收益**：有子区的群聊滑动 FPS 显著提升（预估 60 → 接近 60，卡顿条数减少 70%+）。

---

### 2.2 【Medium】`WKTimeUtils` 每次调用都 new SimpleDateFormat / Calendar

**文件**：`wkbase/src/main/java/com/chat/base/utils/WKTimeUtils.java:93-258`

**证据**：

```java
public String getNewChatTime(long timeStamp) {
    Calendar todayCalendar = Calendar.getInstance();  // 每次 new
    Calendar otherCalendar = Calendar.getInstance();  // 每次 new
    ...
}
public String time2HourStr(long timeStamp) {
    SimpleDateFormat sdf = is24Hour()
        ? new SimpleDateFormat("HH:mm", Locale.getDefault())
        : new SimpleDateFormat("a hh:mm", Locale.CHINESE);
    return sdf.format(new Date(timeStamp));
}
public String getYearTime(long time, String yearTimeFormat) {
    SimpleDateFormat format = new SimpleDateFormat(yearTimeFormat, Locale.getDefault());
    return format.format(new Date(time));
}
```

`SimpleDateFormat` 构造含 pattern 解析 + locale 数据加载，~0.3–1ms/次。`showTime`（ChatConversationAdapter:631）每条会话每次 bind 都调用 `getNewChatTime`，快速滑动时累积显著。

**优化建议**（Quick Win，本身是修 utils，单 PR 可完成）：

```java
private static final ThreadLocal<Map<String, SimpleDateFormat>> TL_FORMATS = ...;

private SimpleDateFormat formatFor(String pattern, Locale locale) {
    Map<String, SimpleDateFormat> map = TL_FORMATS.get();
    String key = pattern + "|" + locale.toString();
    SimpleDateFormat sdf = map.get(key);
    if (sdf == null) {
        sdf = new SimpleDateFormat(pattern, locale);
        map.put(key, sdf);
    }
    return sdf;
}
```

并复用单例 `Calendar` 的话需注意线程安全，建议仅主线程调用者用同一个 `Calendar` 实例（或每次 `timeInMillis=0` reset）。

**预期收益**：`showTime` 单次耗时降 70–90%，每帧 bind 60 个 time 格式化节省 ~10–20ms。

---

### 2.3 【Medium】`showReminders` / `showChannel` 重建子 View

**文件**：`ChatConversationAdapter.java:802-857, 859-950`

- `remindLayout.removeAllViews()` + `new TextView(context)` 多次（mention / draft / approve）
- `categoryLayout.removeAllViews()` + `Theme.getChannelCategoryTV(...)` 每次 bind 按分类添加 TextView
- `new PorterDuffColorFilter(...)` 每次 bind 都分配

**优化建议**：
- `mention / draft / approve` 三个 TextView 写进 item layout XML，bind 时只改 visibility + text。
- `PorterDuffColorFilter` 固定颜色的可用 `Theme.getCachedColorFilter(color)` 缓存（需新增工具）。
- 分类标签如果组合有限（官方/客服/访客/全员/部门/社区/Bot），可用一组预创建 TextView，按需 setVisibility。

**预期收益**：每行 bind 节省 3–6 次 View 构造 + 若干 drawable 分配。

---

### 2.4 【Medium】`filterAndDisplay` 全量 setList，缺 DiffUtil

**文件**：`ChatFragment.java:2007-2127`，调用 `chatConversationAdapter.setList(displayList)` → BaseQuickAdapter 内部 `notifyDataSetChanged`。

触发场景：
- Tab 切换
- `filterAndDisplay()` 被各种监听回调触发（消息入库、草稿变更、category 更新、typing、refresh）
- 列表 50+ 会话时，每次全量 rebind

**优化建议**（Refactor）：
- 用 BRVAH 的 `adapter.setDiffNewData(list, diffCallback)` 或 `AsyncListDiffer`。
- diff key：`sectionHeader.id` / `uiConversationMsg.channelID + channelType`。
- 注意内部 `ChatConversationMsg` 还有一堆 `isResetXxx` flag，diff 时需保留 payload。

**预期收益**：Tab 切换时不再抖动，且 scroll position 保留。

---

### 2.5 【Low-Medium】item_chat_conv_layout.xml 嵌套 5 层

**文件**：`wkuikit/src/main/res/layout/item_chat_conv_layout.xml`

结构：
```
LinearLayout (root)
  └ LinearLayout (contentLayout)
      ├ FrameLayout (avatar area)
      └ LinearLayout (vertical)
          ├ LinearLayout (horizontal)
          │   ├ ConstraintLayout (name + tags + category)
          │   └ LinearLayout (status + time)
          └ LinearLayout (remind + content + badges)
  └ FrameLayout (threadPreviewContainer)
```

5 层嵌套 + 混合 LinearLayout 和 ConstraintLayout，measure 代价可观（LinearLayout 有 weight + double measure）。

**优化建议**（Refactor，不在 Quick Win 范畴）：
- 改为单根 `ConstraintLayout`，把所有元素放在同一层并用 guideline/barrier 约束；
- 保留 `threadPreviewContainer` 作为单独的 include（但不嵌套在 vertical LinearLayout 里）。

**预期收益**：measure pass 时间下降约 30%，带 thread 的群 item 更明显。

---

### 2.6 【Low】`parseLinkPreview` 每次 bind JSON 解析

**文件**：`ChatConversationAdapter.java:488-505` + `:434-437`。`[链接]` 消息在每行 bind 时都 `new JSONObject(jsonStr)`。

**优化建议**：结果缓存到 `ChatConversationMsg.cachedLinkPreview`（懒初始化），后续 bind 直接读缓存。

---

### 2.7 【Low】`setStatus` 每次分配 RLottieDrawable

**文件**：`ChatConversationAdapter.java:507-586`

```java
drawable = new RLottieDrawable(getContext(), R.raw.ticks_double, "ticks_double", dp(22), dp(22));
```

- 已发送/已读双勾、单勾都有 `Theme.getTicksSingleDrawable()` 可复用的静态版（代码里已用了），但 `playAnimation` 和 `error` 分支仍然 new。
- 若自己发送的消息很多（普通聊天场景不多，群里会稍多），仍有开销。

**优化建议**：按 status 类型做 drawable 池化（重量级，低优先）。

---

## 3. 数据层 / 其他

- **主线程耗时操作**：`formatSpans`（Markwon 渲染）已经在 `buildUiMsgList` 的后台线程里做（`ChatActivity.java:1412-1447` 注释里明示），✅ 好。会话列表的 `getContent` 是纯字符串拼接，OK。
- **图片加载**：Glide `RequestOptions` 每次 `new RequestOptions()`（`GlideRequestOptions.normalRequestOption`:36-47）。轻量级但每 bind 调用确有 GC 压力，可改单例 options 并 `clone()` 或直接复用。
- **DB 查询**：`findSystemBotSpaceContent`（ChatConversationAdapter:669-697）在 bind 里调用 `WKIM.getInstance().getMsgManager().searchMsgWithChannelAndContentTypes(..., 500, ...)` —— **这一条虽然仅对 SystemBot 且当前 Space 消息不匹配时才走，但一旦命中就是主线程跑 DB 查询 500 条**，滚动中若正好遇到跨 Space 系统 Bot 会触发严重掉帧。建议：异步执行 + 结果缓存到 `ChatConversationMsg`。
- **Paging / Flow / distinctUntilChanged**：项目走的是 BRVAH，不是 Paging3；无 Flow。

---

## 4. 交付物：优化方案

### 4.1 Quick Win（单 PR 可完成，建议 commit 分组）

1. **[perf-1]** `chat_item_base_layout.xml` 删除 `animateLayoutChanges="true"`（§1.1）。
2. **[perf-2]** `ChatActivity.initView`：`MyItemAnimator` 实例调 `setSupportsChangeAnimations(false)`；加 `recyclerView.setHasFixedSize(true)`（§1.2, §1.4）。
3. **[perf-3]** `WKBaseFragment.initAdapter` / `WKBaseActivity.initAdapter` 加 `recyclerView.setHasFixedSize(true)`（§1.4）。
4. **[perf-4]** `WKTimeUtils` 使用 `ThreadLocal<HashMap<String, SimpleDateFormat>>` 缓存（§2.2）。
5. **[perf-5]** `ChatConversationAdapter.parseLinkPreview` 结果缓存到 `ChatConversationMsg`（§2.6）。
6. **[perf-6]** `ChatConversationAdapter.findSystemBotSpaceContent` 改为 async + 缓存，避免主线程 DB 查询（§3 最后一条）。

#### diff 草案（Quick Win 1–4 关键行）

```diff
-    <LinearLayout
-        android:id="@+id/wkBaseContentLayout"
-        android:layout_width="wrap_content"
-        android:layout_height="wrap_content"
-        android:animateLayoutChanges="true"
-        android:orientation="horizontal" />
+    <LinearLayout
+        android:id="@+id/wkBaseContentLayout"
+        android:layout_width="wrap_content"
+        android:layout_height="wrap_content"
+        android:orientation="horizontal" />
```

```diff
-        //去除刷新条目闪动动画
-        ((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
-        chatAdapter = new ChatAdapter(this, ChatAdapter.AdapterType.normalMessage);
-        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
-        wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
-        wkVBinding.recyclerView.setAdapter(chatAdapter);
-        wkVBinding.recyclerView.setItemAnimator(new MyItemAnimator());
+        chatAdapter = new ChatAdapter(this, ChatAdapter.AdapterType.normalMessage);
+        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
+        wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
+        wkVBinding.recyclerView.setHasFixedSize(true);
+        wkVBinding.recyclerView.setAdapter(chatAdapter);
+        MyItemAnimator itemAnimator = new MyItemAnimator();
+        itemAnimator.setSupportsChangeAnimations(false);
+        wkVBinding.recyclerView.setItemAnimator(itemAnimator);
```

```diff
// WKTimeUtils
-    public String time2HourStr(long timeStamp) {
-        SimpleDateFormat sdf;
-        if (is24Hour()) {
-            sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
-        } else {
-            sdf = new SimpleDateFormat("a hh:mm", Locale.CHINESE);
-        }
-        return sdf.format(new Date(timeStamp));
-    }
+    public String time2HourStr(long timeStamp) {
+        String pattern = is24Hour() ? "HH:mm" : "a hh:mm";
+        Locale loc = is24Hour() ? Locale.getDefault() : Locale.CHINESE;
+        return formatFor(pattern, loc).format(new Date(timeStamp));
+    }
+
+    private static final ThreadLocal<HashMap<String, SimpleDateFormat>> SDF_CACHE =
+            ThreadLocal.withInitial(HashMap::new);
+
+    private SimpleDateFormat formatFor(String pattern, Locale locale) {
+        HashMap<String, SimpleDateFormat> m = SDF_CACHE.get();
+        String key = pattern + "|" + locale.toString();
+        SimpleDateFormat sdf = m.get(key);
+        if (sdf == null) {
+            sdf = new SimpleDateFormat(pattern, locale);
+            m.put(key, sdf);
+        }
+        return sdf;
+    }
```

### 4.2 Refactor（多 PR / 跨模块）

- **[refactor-A]** `WKChatBaseProvider.showData` 增加子 View 复用：按 `msgType` tag 缓存 `getChatViewItem` 结果，bind 只刷数据。
- **[refactor-B]** `ChatConversationAdapter.showThreadPreviews` 由动态 inflate 改为 XML stub + visibility 切换。
- **[refactor-C]** `filterAndDisplay` 接入 BRVAH DiffUtil / AsyncListDiffer。
- **[refactor-D]** `item_chat_conv_layout.xml` 扁平化为单根 ConstraintLayout。
- **[refactor-E]** `showReminders / showChannel` 预建子 View，避免每 bind remove/add。

这些改动各自涉及多文件、行为影响面较大，建议分 PR 走 code review。

---

## 5. 真机验证建议（给 Yu）

### perfetto 采样
```bash
adb shell perfetto --buffer 64mb --txt -c - --out /data/misc/perfetto-traces/jank.pftrace <<EOT
buffers: { size_kb: 65536 }
data_sources {
  config { name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "power/cpu_frequency"
      atrace_categories: "view"
      atrace_categories: "gfx"
      atrace_categories: "rv"
      atrace_apps: "com.xinbida.tsdaodao"
    }
  }
}
duration_ms: 10000
EOT
```
拉回 `jank.pftrace` 用 https://ui.perfetto.dev 打开，关注 Janky frames 列表。

### systrace（兼容）
```bash
python systrace.py -t 10 -b 32768 -o jank.html view gfx rv wm sched freq
```

### Android Studio Profiler
- CPU → Sample Java Methods → 滑动场景录制 5s，看 `onBindViewHolder / inflate / format` 热点。
- Layout Inspector → 看 overdraw 层级。

### 目标指标
- 快速 fling 50 条消息：Janky frames < 5%。
- 会话列表 60 行 fling：主线程 bind 总时间 < 200ms。

---

## 6. Phase 2 日志 (YUJ-240)

Phase 1（YUJ-236）的追加 Quick Win，全部低风险改动，聚焦"滑动时减少无效工作量 + Glide 尺寸/缓存策略收敛"。

### 6.1 落地项

| 代号 | 项目 | 位置 |
|------|------|------|
| A2 | `setItemViewCacheSize` | `ChatActivity` 20（Phase 1 已调）；`ChatFragment` 2→15 |
| A3 | 滑动暂停 Glide，IDLE 恢复 | `ChatActivity` 原 OnScrollListener 追加；`ChatFragment` 新增独立 OnScrollListener |
| A4 | `RecycledViewPool.setMaxRecycledViews` | `ChatActivity.initView` — `WK_TEXT` / `WK_IMAGE` / `richText` 5→20 |
| A8 | Glide override + thumbnail + `DiskCacheStrategy.AUTOMATIC` | `GlideRequestOptions` / `GlideUtils` — 头像按 `ImageView.layoutParams` 推导 override；`showImg(w,h,…)` / `showAvatarImg` 追加 `.thumbnail(0.1f)` |

### 6.2 跳过项

- **A5 `setHasStableIds`** — `ChatAdapter` 存在本地占位消息（`messageID` 可能为空）+ 三索引并存，stable ids 有 duplicate 风险。留给 Phase 3 与 DiffUtil 改造一并做。
- **A6 嵌套 RV 预取** — 审计确认无实际目标：`reactionsView` 是 `FrameLayout`；`chat_item_card` 里的 RV `visibility=gone` 且 `WKCardProvider` 未绑 Adapter；thread preview 走 `inflater.inflate + addView`。
- **Refactor-B `showThreadPreviews` per-bind inflate 替换** — 行数动态 + per-row listener 重绑，非 1-stub+visibility 能替代，超 Phase 2 scope，留给 YUJ-237。

### 6.3 Review 修正 (Jerry-Xin @ 2026-05-02)

- **Blocking**：从 `WKBaseActivity.initAdapter` / `WKBaseFragment.initAdapter` 移除了 Phase 1 的 blanket `setHasFixedSize(true)` — 会抑制 `SearchAllActivity` 里三个 `wrap_content` RV（NestedScrollView 内）的 remeasure。该 flag 只保留在 `ChatActivity.initView`（message list）和 `ChatFragment.initView`（conversation list）两处，父容器均为 `match_parent`，显式开启。
- **W#2** 删除 `ChatActivity` 中已失效的 `DefaultItemAnimator.setSupportsChangeAnimations(false)` 配置（紧接着就被 `MyItemAnimator` 覆盖了）以及随之变成死 import 的 `androidx.recyclerview.widget.DefaultItemAnimator`。
- **W#5** `ChatFragment` 的 `setSupportsChangeAnimations(false)` 相对 `setAdapter` 的顺序模糊 — 移到 `initAdapter(...)` 之后并加 `instanceof DefaultItemAnimator` 守卫。

### 6.4 已知剩余成本（延后）

- **`WKTimeUtils.getNewChatTime`** 仍每次 `Calendar.getInstance()`（两次分配/调用）。ThreadLocal `Calendar` 复用有 `.clear()` / timezone 切换的 foot-gun，Phase 2 不动。Phase 1 的 SDF 缓存已拿掉更大的一块开销，剩余 `Calendar` 分配延后单独评估。

## 7. 不做的事

- UI 视觉风格 / Material 3 切换 - 不动。
- WuKongIM 协议层 / 端到端加密 - 不动。

## 8. 下一步建议

建议先起 Quick Win PR（§4.1 的 1–4）做基线验证；若 perfetto 跑出来 jank 下降明显，再推 Refactor-A / Refactor-B。High 级三项（§1.1 §1.2 §2.1）都是"代码已经这么写了就是卡"类问题，改完能直接体感。

---

