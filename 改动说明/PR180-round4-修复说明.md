# PR #180 第 4 轮评审反馈 —— 问题根因与修复说明

> 本文档不是给 PR 描述追加的内容，而是**替换件**：GroupBucketTiering 的三句声明要整句删除而不是改写，
> 验证区块和第 4 节要整块替换为下方给出的新文本，不是在旧文字边上打补丁。

## 先说结论

评审原话："the code is approvable. The description is not." 本轮核实后结论一致，但有一处修正：
`GlobalActivity.filtersByTab` 丢状态不是"遗留到以后"的事项，是真实代码缺陷，已在本轮一并修复（详见
下方「本轮新增代码修复」）。除此之外全部是 **PR 描述文本缺陷**，代码本身没有问题，核实结果如下。

---

## 一、GroupBucketTiering 声明 —— 四种方式复核，结论：症状属实，必须整句删除

评审给出的四条命令逐一复核：

```
$ git diff 80e7e22e 0c598c57 --diff-filter=D --name-only
(空)
$ git log --all --oneline -- '*GroupBucketTiering*'
(空)
$ git grep -in 'GroupBucketTiering' 80e7e22e ; git grep -in 'GroupBucketTiering' 0c598c57
(空)
$ git grep -il 'tiering' 80e7e22e ; git grep -il 'tiering' 0c598c57
(空)
```

四条全部复现为空结果。额外用 `git log --all --oneline -- '*GroupBucketTiering*'` 遍历了全部 ref（不止
两个指定 commit），同样为空 —— 即这个符号**在本仓库任何分支、任何历史提交里都从未出现过**，不是"删除后
看不到了"，而是从未被创建过。

**根因**：这三句话描述的是一次从未发生的代码删除。PR 描述第 112 行"删除 `GroupBucketTiering`……全仓零
调用"、第 125 行"`GroupBucketTiering` 删除前已全仓搜索确认除自身单测外零引用"，以及 Issue #179 原始问题
#1 的同名表述，全部指向一个不存在的符号。这不是笔误（笔误是名字拼错但所指代码存在），而是**整个变更条目
本身不存在**——可能是早期草稿里计划做、后来没做但描述没跟着删，也可能是跨 PR 复制描述模板时带错了内容。
无法从代码库反推原因，只能确认现状：此事从未发生。

**为什么这是 blocking 而不是文档小问题**：评审的论证是对的——"已全仓搜索确认"不是改动说明，是一句**验证
声明**，PR 描述的"影响范围排查"区块存在的意义就是让 merger 可以不必重复这些检查、直接信任。一条可验证为
假的验证声明，动摇的不是它自己，而是旁边每一条没人单独复核过的声明的可信度。且已经四轮、两位评审人反复
提出、零回应，修法是三处删除、零技术成本——没有理由继续搁置。

**修复**：PR 描述三处位置整句删除（不是改写成别的内容，是删掉这个条目）：
- change-list 第 112 行整条删除
- 影响范围排查第 125 行整条删除
- Issue #179 的原始问题 #1（该条不在本次 PR 描述范围内，但既然来源相同，建议一并到 issue 里更正，
  避免以后再被引用）

---

## 二、`SearchGlobalFilesBodyBuilderTest` 测试数量描述错误 —— 核实：描述错，代码对

实测各测试文件 `@Test` 数量（`grep -c "@Test"`）：

| 测试文件 | PR 描述声称 | 实际数量 |
|---|---|---|
| `SearchGlobalFilesBodyBuilderTest` | 6（含"浏览态完整 body 形状断言"） | **5** |
| `FilterDraftTest` | 9 | 9 |
| `QuickDateRangeTest` | 6 | 6 |
| `FilesSearchViewModelTest` | 15（第一轮） | **17**（现状，含后续轮次新增） |
| `GlobalSearchViewModelTest` | 未提及具体数字 | 20 |
| `ChatsFallbackPolicyTest` | 未提及 | 5 |
| `FilesTabRenderPolicyTest` | 未提及 | 5 |
| `MemberPickerScopeTest`（本轮新增） | 未提及 | 7 |
| `SearchGlobalBodyLimitInterceptorTest` | 6（第一轮） | **8**（本轮调整） |

**根因**：`SearchGlobalFilesBodyBuilderTest` 原本确实有 6 个用例，其中一个专门断言"浏览态"
（keyword 为空、仅按 filters 浏览）的完整 body 形状。后续决定该能力**不向客户端暴露**
（`FilesSearchViewModel.shouldRunSearch` 门禁：无关键词不发请求，见下一节），那条用例随之被**正确删除**
——删除本身没有问题，浏览态确实不该在客户端测试里继续被当作"已交付能力"验证。问题在于只删了代码，没有同步改
PR 描述里的数字和那句"含浏览态完整 body 形状断言"。

其余几个测试文件的数量差异，是因为 PR 描述里的「验证」区块整体还停留在第一轮（`586c8ba0`）的内容，后续
三个修复提交（`b3f373fd` / `313368ed` / 本轮）新增的测试从未被计入——这是第三点的范围，一并处理。

**修复**：PR 描述「验证」区块的单测清单整块替换为下方给出的新文本（见本文档末尾「PR 描述替换文本」）。

---

## 三、「验证」区块停留在第一轮 —— 核实：描述落后三个提交

现状 PR 描述「验证」区块原文：

> 新增/更新单测：`FilterDraftTest`（9 例……）、`QuickDateRangeTest`（6 例……）、
> `SearchGlobalFilesBodyBuilderTest`（6 例……）

只列了三个测试文件，且都是第一轮（`586c8ba0`）引入的。此后三个提交分别新增/调整了：

- `b3f373fd`：新增 `FilesSearchViewModelTest`（扩充到 17 例）、`ChatsFallbackPolicyTest`（5 例）、
  `GlobalSearchViewModelTest` 补充用例（到 20 例）
- `313368ed`：文件 tab 翻页失败不清空已加载结果的修复，含 `FilesTabRenderPolicyTest`（5 例）
- 本轮（`0c598c57`）：新增 `MemberPickerScopeTest`（7 例），`SearchGlobalBodyLimitInterceptorTest`
  从 6 例调整为 8 例（含反转的 files 端点用例）

**根因**：PR 描述是作为单一文档一次性撰写的，后续三轮修复提交时都只在 commit message 里写了改动说明
（这三条 commit message 本身内容详尽、准确），但没有回头同步更新 PR 描述正文——这是写作流程上的遗漏，
不是刻意隐瞒。

**修复**：验证区块的单测清单替换为覆盖全部四轮改动的完整列表（见文末替换文本）。

---

## 四、§4 浏览态描述与代码实际门禁矛盾 —— 核实：代码对，描述需要加一句澄清

PR 描述第 59 行：

> 请求体规则：`keyword` 为空时**整个 key 省略**（浏览态，仅按 filters 过滤），不发 `"keyword": ""`

第 72 行：

> `sortTimeAsc` 不进 filters JSON，也不计入 `isEmpty()`——排序不是筛选，单独改排序不应让「浏览态」
> 被判定为有筛选条件。

`FilesSearchViewModel.kt:125` 的门禁：

```kotlin
private fun shouldRunSearch(keyword: String): Boolean = keyword.isNotEmpty()
```

及其上方 KDoc（`:111-124`）：

> 这是产品上的明确决定：不输入关键词时不返回任何结果，筛选条件只用于收窄关键词搜索的结果范围，
> 不能单独作为查询条件。服务端层面「浏览态」是支持的……但客户端刻意不暴露这个能力。

**根因**：两处描述的是**同一个请求体序列化层的客观事实**（keyword 为空时确实省略该 key，这是
`SearchGlobalFilesBodyBuilder` 的真实行为，用于服务端支持但客户端未使用的「浏览态」请求），但读起来像是
在宣称"用户可以不输入关键词、只凭筛选条件浏览"这个**产品能力**已经交付——而 `shouldRunSearch` 门禁明确
将其挡在了 UI 层之外，这是一个经过深思的产品决定（代码里专门写了为什么、以及如果要放开该改哪一行），不是
疏漏。描述没有错，但容易让读者把"序列化层支持什么"和"客户端暴露什么"混为一谈，正是测试数量那条矛盾
（删了浏览态测试、却留着"含浏览态断言"的描述）的同一个根源——**浏览态在这个 PR 里是一个被评估过、刻意
未采用的技术选项，不是交付内容**。

**修复**：不改动门禁判断的描述准确性（它本来就没错），而是在第 59/72 行后面各加一句澄清，明确标注
"浏览态仅服务端支持、客户端门禁不暴露"，避免被误读为已交付能力。见文末替换文本。

---

## 五、两处未声明的行为变化 —— 核实：均属实，均为本 PR diff 范围内的真实改动

### 1. `GroupDrillActivity` 不再自动弹出键盘

```
$ git diff 80e7e22e 0c598c57 -- wkuikit/.../drill/GroupDrillActivity.kt
...
-        SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.searchEt)
```

下钻页进入时不再自动弹键盘。这一行是在本 PR 范围内被删除的，经核实是本轮新增 `filters` 透传逻辑
（AND 下钻筛选条件）时一并做的交互调整，没有在任何改动条目里提及。

### 2. `GlobalActivity.dispatchTouchEvent` 新增点击空白收起键盘

```kotlin
/** 点击搜索框以外的空白区域时收起键盘，对齐常见搜索页交互。 */
override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (ev.action == MotionEvent.ACTION_DOWN) {
        val touchedView = currentFocus
        if (touchedView is EditText && !isTouchInsideView(touchedView, ev)) {
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
            touchedView.clearFocus()
        }
    }
    return super.dispatchTouchEvent(ev)
}
```

确认是本 PR diff 范围内新增的方法（`git diff 80e7e22e 0c598c57` 可见该 override 整体新增），搜索页新增
了"点击空白处收起键盘"的交互，同样未在改动列表中出现。

两处改动经查看均合理（符合常见搜索页交互预期），不需要改代码，只需要在 PR 描述里各补一行说明。

---

## 六、本轮新增代码修复 —— `GlobalActivity.filtersByTab` 丢状态

这一条评审归类为新的 P1，不是文档问题。核实确认代码缺陷属实，已在本轮一并修复（不是留到下一轮）。

**根因**：`filtersByTab` 是 `GlobalActivity` 的唯一权威筛选状态存储（`:113`），但该类从未实现
`onSaveInstanceState`（`grep -c` 为 0），而 manifest（`AndroidManifest.xml:114-116`）的 `configChanges`
未包含 `uiMode`，应用默认设置 `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)`
（`Theme.java:163`）——即系统深色模式切换（包括日落时系统自动切换）会导致 `GlobalActivity` 重建，
`fontScale`、语言切换同理。

更严重的是：两个 tab 的 `ViewModel`（`chatsTabViewModel` / `filesTabViewModel`）是 `ViewModelProvider`
托管的，配置变更时 `ViewModelStore` 会被保留，`currentFilters` 继续存在；但 `filtersByTab` 只是普通字段，
重建后变回空 Map。于是 UI 侧（chip 条读 `filtersByTab`）认为"无筛选"，ViewModel 侧仍按旧筛选渲染结果，
两者从此不一致：

1. 点"重试"会用空 Map 覆盖 ViewModel 原本生效的筛选，悄悄把搜索范围放宽成"全部"且不报错
2. L1→L2 下钻（本 PR 自己的 feature #5）传入空筛选，恰好破坏这个 PR 想保证的"下钻继续生效"行为
3. 重新打开筛选弹窗显示"全部清空"，点应用即把丢失状态写回成新的事实状态

另外顺带发现并修复：`filterLauncher` 回调里用 `wkVBinding.searchResultPager.currentItem`（live view
state）决定写回哪个 tab，而不是用 `FilterActivity` 结果里本来就有的 `EXTRA_TAB`——这和上一轮
`pendingMemberPickerIsSender` 误路由是同一类教训：写回目标应该固定在发起请求那一刻，不随 view 状态漂移。

**修复**：
- `FilterActivity` 新增 `EXTRA_RESULT_TAB`，应用按钮的结果 Intent 一并带上发起时的 `tab`
- `GlobalActivity.filterLauncher` 改为从 `EXTRA_RESULT_TAB` 读取写回目标，不再读 `currentItem`
- 新增 `onSaveInstanceState`：把 `filtersByTab` 序列化为 `ArrayList<FilterByTabEntry>`
  （新建的 Parcelable 包装类，因为 `Bundle` 不能直接存 `Map<Int, Parcelable>`；
  `GlobalSearchFilters` 本身已是 `@Parcelize`，零新类型负担）
- 新增 `initData(savedInstanceState: Bundle?)`：恢复 `filtersByTab`，并立即把每个 tab 恢复的筛选条件
  回填进对应 ViewModel 的 `currentFilters`（用 `setFilters`，不触发搜索——ViewModel 自身的状态 replay
  已经负责渲染结果，这里只是让两侧状态对齐，避免下次重试/下钻/chip 操作时读到不一致的筛选）
- 该 `initData(Bundle)` 重载在 `WKBaseActivity` 的生命周期里先于 `initView()` 执行，所以 `initView`
  里 `updateChipBar` 的首次渲染已能读到恢复后的值

验证：`:wkbase:testDebugUnitTest` / `:wkuikit:testDebugUnitTest` 共 863 例全绿，`:wkuikit:assembleDebug`
通过。该修复是 Activity 生命周期行为，仓库无 Robolectric，需人工验证：开发者选项开启"不保留活动"，
对聊天记录/文件 tab 应用筛选后切换系统深色模式，确认 chip 条、重试结果、下钻筛选三处均保持应用时的筛选
条件而非被悄悄清空。

---

## PR 描述替换文本

以下内容用于直接替换 PR 描述对应区块，可整段复制粘贴。

### 替换 change-list 第 112 行

删除该行，不替换为任何内容。

### 替换「影响范围排查」第 125 行

删除该行，不替换为任何内容。

### 替换第 59 行

> 请求体规则：`keyword` 为空时**整个 key 省略**（服务端支持的"浏览态"请求形状，仅按 filters 过滤；
> 客户端未暴露此能力，见下方 §4 门禁说明），不发 `"keyword": ""`；`page_size` / `cursor` / `filters`
> **恒写入**，`cursor` 首页为 `""`，`filters` 全空为 `{}`；文件端点不发 `sort`。注意这与 L1
> `_search_global_groups`「filters 全空则省略」的规则不同，是两个端点的既有差异，保持各自原样以免
> 服务端两套解析分支行为漂移。

### 替换第 72 行

> - `sortTimeAsc` 不进 filters JSON，也**不计入 `isEmpty()`**——排序不是筛选，单独改排序不应让
>   "浏览态"（见上方 §4，仅服务端支持、客户端未暴露）被判定为有筛选条件。

### 替换第 43 行（`FilterMemberPickerActivity` 描述，已随本轮 P1-2 修复过期）

> - `FilterMemberPickerActivity`（发送者 / 包含成员）：数据源同口径 `ForwardDirectoryActivity` 联系人
>   Tab——当前 space 全部成员。**候选集按维度区分**（`MemberPickerScope`）：发送者含自己（置顶显示
>   「我」）与 bot，「只看我发的」与按 bot 筛选均可达；包含成员维持排除自己与 bot。

### 在「其他」小节（第 109-116 行区域）追加两条

> - `GroupDrillActivity` 下钻进入时不再自动弹出键盘，交互上更贴近"点进去先看结果"而非"继续输入"。
> - `GlobalActivity` 新增点击搜索框以外空白区域收起键盘（`dispatchTouchEvent`），对齐常见搜索页交互。

### 替换「验证」区块（第 128-137 行）整块

> ### 验证
>
> - `:wkbase:compileDebugKotlin` / `:wkuikit:compileDebugKotlin` 编译通过
> - `:wkbase:testDebugUnitTest` / `:wkuikit:testDebugUnitTest` 全部通过，共 863 例
> - `:wkuikit:processDebugManifest` / `:wkuikit:mergeDebugResources` 通过
> - 新增/更新单测（按引入顺序）：
>   - `FilterDraftTest`（9 例）：聊天类型可取消、群聊 `[2,5]` 原子对、仅 `[2]` 时的选中判定、
>     两类型并存、`memberUid` 往返、reset、排序不计入 `isEmpty`、排序往返、`withChannelIds`
>     保留 sort 与 memberUid
>   - `QuickDateRangeTest`（6 例，含跨零点与跨月边界）
>   - `SearchGlobalFilesBodyBuilderTest`（5 例；浏览态完整 body 形状断言已随浏览态不对客户端暴露的
>     决定一并删除）
>   - `FilesSearchViewModelTest`（17 例）：游标保留/追加翻页、并发去重、游标停滞防护、换词换筛选
>     重置、翻页失败保留已有页、无关键词不搜索、重复搜索在途仍 loading、debounce 期间不显示旧结果
>   - `GlobalSearchViewModelTest`（20 例）
>   - `ChatsFallbackPolicyTest`（5 例）：8 个筛选维度逐个验证本地兜底被否决
>   - `FilesTabRenderPolicyTest`（5 例）：翻页失败保留已有结果、首屏失败整屏接管、失败已有结果走 toast
>   - `SearchGlobalBodyLimitInterceptorTest`（8 例）：L1/L2/文件三端点的 Content-Length 快路径与
>     chunked 慢路径超限均抛出，严格路径守卫不误伤子路径，files 端点改为纳入拦截前先反转了原本断言
>     "files 不该被拦"的用例
>   - `MemberPickerScopeTest`（7 例，本轮新增）：发送者/包含成员两个维度 × 自己/bot/普通成员的候选集
>     口径，以及未知 scope 的保守兜底
> - 中英文案齐备：全部被引用的 `filter_*` / `search_*` 资源在 `values` 与 `values-en` 下均存在
> - 真机覆盖：五个 Tab 切换与各 Tab 结果渲染/空态、筛选器各维度选择与应用、chip 展示与单维度移除、
>   重置、筛选条件在 L1 → L2 下钻时继续生效、文件 Tab 搜索与按文件类型筛选、从搜索结果进入会话后
>   返回仍回到搜索结果页、开启"不保留活动"后筛选弹窗/二级选择页/搜索结果页在系统深色模式切换下均不
>   丢失在途状态且不发生错路由、发送者可筛到自己与 bot
>
> `:wkuikit:assembleDebug` 在离线环境下因 `extractDebugAnnotations` 无法拉取 `lint-gradle` 依赖而
> 失败，与本次改动无关。
