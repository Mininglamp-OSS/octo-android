# fix(file): 修复同名文件缓存撞车导致点开旧内容，下载改为原子落地

## 背景

同一会话里先后收到两个同名但内容不同的文件，点开第二个时会直接打开第一个的旧内容，不触发重新下载。根因是下载缓存的判断键是**文件名**，而文件名由发送方提供、可以重复，不包含消息唯一标识。

合并转发详情页问题更严重：其下载目录是全局共享的 `forward/`，不按会话或消息隔离，跨会话同名文件也会互相命中。

详见 `ISSUE-同名文件下载打开旧内容.md`。

## 改动内容

### 1. 缓存键改为 `clientMsgNO`（`WKFileProvider.kt`）

下载路径不再使用发送方可控的 `fileContent.name`，改为由消息唯一标识推导：

```
chatDownloadFileDir/{channelType}/{channelID}/{clientMsgNO}.{ext}
```

同名文件因为 `clientMsgNO` 不同而天然落在不同路径，撞车的可能性从结构上消除，不需要再依赖文件名去重。

原始文件名与磁盘路径彻底解耦：磁盘上是 `clientMsgNO.ext`，而 BottomSheet 标题、预览页标题、保存建议名、分享标题、MIME 类型判断全部改用 `fileContent.name`。`openFileWithOptions()` / `openFileDirectlyStatic()` 为此新增 `displayName` 参数（默认 `null` 时回落 `file.name`，不影响其他调用方）。

`clientMsgNO` 与 `extension` 都可能来自服务端/对端 JSON（`decodeMsg` 用 `optString`，服务端同步路径也是直接赋值），直接拼进路径存在 `../` 穿越风险，因此新增 `buildCacheFileName()` 统一构造缓存文件名，内部对两者都取 basename、替换危险字符、去除 `..`，保证结果是单段安全名。

随之删除已无调用方的 `sanitizeFileName()` / `truncateToByteLength()` / `MAX_FILENAME_BYTES`：它们原本是为了处理超长中文/emoji 文件名导致的 `ENAMETOOLONG`，而新方案下文件名是定长的 `clientMsgNO.ext`（约 45 字节），该风险已不存在。

> 注：`clientMsgNO` 只在**普通聊天**链路可用——那里的 `WKMsg` 来自 `message` 表，`clientMsgNO` 是真实持久化主键。合并转发详情页的子消息不满足这个前提，用的是另一套键，见第 4 节。

### 2. 缓存有效性补充大小校验（`WKFileProvider.kt`）

新增 `isValidCache()`，在 `localPath` 分支和下载目录分支都要求 `file.length() == fileContent.size` 才认定缓存有效，中断残留的半截文件不会再被当作可用缓存。`size <= 0`（服务端未下发）时退化为仅判断存在，保持兼容。

发送方场景已确认不受影响：发送侧 `localPath` 与 `size = file.length()` 同源，大小必然精确匹配，不会出现发送方被迫重新下载自己文件的情况。

### 3. 下载成功回写 `localPath`（`WKFileProvider.kt`）

下载成功后把实际路径写回 `fileContent.localPath` 并持久化，后续点击直接命中本地文件，不再依赖"文件名撞目录"这种隐式缓存机制。

持久化走新增的 `MsgManager.updateContentLocalPath()`，**不是** `updateContentAndRefresh(clientMsgNo, model, ...)`。后者用 `model.encodeMsg()` **整列覆盖** `content`，而 `WKFileContent.encodeMsg` 只输出 `name` / `extension` / `size` / `url` / `localPath` 五个字段；`content` 顶层还存着一批模型不持有、但 `MsgManager.getMsgContentModel` 每次读库都要解析的协议字段（`mention` / `reply` / `entities` / `robot_id` / `space_id` / `from_uid`）。整列覆盖会把它们永久抹掉，且该行不会再从服务端重新拉取，后果是：

- **Space 隔离失效**：`SpaceFilter.extractSpaceIdFromMsg` 直接读 `content` 里的 `space_id`，`ConversationPreviewFilter.isMessageCrossSpace` 用它判跨 Space。字段丢失后走 fail-open，跨 Space 的 DM 预览不再被隐藏
- **@ 提及状态失效**：`mention` 全树（`all` / `humans` / `ais` / `uids`）都从顶层解析，带 @ALL 的文件消息下载后标志位丢失

`flame` / `flame_second` 有独立列兜底，不受影响。

新增的 `MsgDbManager.mergeContentFieldsWithClientMsgNo()` 改为「读出原始 content 字符串 → 只 put 指定键 → 写回」，不经过模型序列化，未知字段原样保留；读-改-写全程持有与其它 update 方法相同的 monitor，避免并发回写互相覆盖。`content` 不是合法 JSON 时直接放弃写入（整列覆盖会丢字段，宁可不写）。

`WKVoiceProvider.kt` 的既有回写同样存在这个问题（`WKVoiceContent.encodeMsg` 只输出 4 个字段），本次一并改为走 merge 方法收口。

### 4. 合并转发目录按消息隔离（`ChatMultiForwardDetailAdapter.kt`）

`handleForwardFileClick()` 新增 `cacheKey` 参数，文件名同样走 `buildCacheFileName()`。去掉了全局共享路径下的文件名拼接，以及 `name` 为空时退化成常量 `"file.dat"` 的兜底逻辑——后者会让所有无名文件共用同一个路径。同步补齐大小校验。

**缓存键不能用 `item.msg.clientMsgNO`**：合并转发的子消息是 `WKMultiForwardContent.decodeMsg` 的运行时反序列化产物，`decodeMsg` 只赋 `content` / `type` / `timestamp` / `messageID` / `fromUID` / `channelType`，**从不赋值 `clientMsgNO`** —— 该字段来自 `WKMsg` 构造函数的 `createClientMsgNO()` 兜底，是个随机 UUID。而两条进入详情页的路径（嵌套转发直接 `decodeMsg`；一级转发经 `serializeMsg → getMsgModel → getMsgContentModel`）每次打开都会重新 decode，所以这个 key 每次都变，会导致：

- 缓存永不命中，每次重开详情页都重新下载（比修复前用文件名做 key 还差）
- `forward/` 目录无界增长：每次打开写一个新 `<uuid>.<ext>`，无清理。该目录是 `getExternalFilesDir("chatDownloadFile")`，而 `DataCleanManager.clearAllCache` 只删 `getCacheDir()` / `getExternalCacheDir()`，**设置里的「清除缓存」覆盖不到**

改为 `forwardFileCacheKey()`：优先用 `messageID`（`decodeMsg` 从 payload 的 `message_id` 赋值，跨 decode 稳定），为空时回落 `fwd_<在 list 中的下标>`（下标在 msgList 内稳定，避免所有无 ID 文件塌到同一路径）。

该路径上**刻意不回写 `localPath`**：合并转发子消息在 `message` 表里没有自己的行（它只是父消息 `content` 里的一个 payload 条目），按任何键 UPDATE 都匹配不到行，是静默 no-op。重开命中靠的是稳定的 `cacheKey` 路径，不需要持久化。

### 5. 下载原子落地与失败清理（`Downloader.kt`）

- 下载期间写入 `{目标路径}.tmp`，完整收流后再 `renameTo()` 到目标路径；rename 失败（如跨挂载点）退化为复制覆盖后删除临时文件
- 新增统一的 `dispatchFail()`，所有失败路径（`onFailure` / body 为空 / HTTP 非 2xx / 读流异常）都走它：清残留 `.tmp` → 置 `ERROR` → 主线程回调 `onFail` → 注销回调与任务
- **补齐 HTTP 4xx/5xx 分支**：此前 `onResponse` 的非成功分支只打一行日志就 return，既不清理也不回调。过期文件 URL 的 403/404/410 是最常见的真实失败，结果是进度条永久卡住、无 toast、`mOnDownloadHashMap` 条目泄漏。body 为空分支同样如此。两处现已接入 `dispatchFail()`（失败分支额外 `response.close()`；成功分支刻意不关，body 流正由 `calculate` 读取）
- 完成回调改为传入 rename 后的最终文件，避免上层拿到 `.tmp` 路径
- **回调注销改为按身份**：新增 `unregisterCallback()`，用 `ConcurrentHashMap.remove(key, value)` 原子条件删除。此前成功回调里无条件 `remove(url)` 是 post 到主线程执行的，若此间同 URL 的第二次 `download()` 已注册新回调，会把新注册删掉，新任务的 `calculate()` 拿到 `null` callback —— 无进度、无完成、无失败，进度条再次卡住
- **新增同 URL 在途守卫**：`download()` 用 `mDownloadTasks.putIfAbsent()` 原子判重，同 URL 且落盘路径相同时直接复用在途任务。此前双击文件行会起两个任务写同一个 `.tmp`，两个 `FileOutputStream` 交错写入产生错乱文件。参照 iOS `WKMergeForwardDetailCell` 的 `downloadingMessages` 守卫（`startDownloadForMessage` 开头的 `if (downloadingMessages()[msgKey]) return`）。落盘路径不同时（同一 URL 被普通聊天与合并转发按不同键缓存）放行，各写各自的 `.tmp`，代价是重复下载一次但结果正确
- **删除 `pauseDownload()` / `resumeDownload()`**：全仓库无调用方，且实现与"断点续传"语义不符 —— `pauseDownload` 置 `PAUSED` 后立刻 `call.cancel()`，cancel 会让 `calculate()` 里阻塞的 `read()` 抛异常，控制流进 catch（清 `.tmp` + 置 `ERROR` + 移除任务）而不是 `PAUSED` 的干净 break，只有恰好停在循环顶部才走得到 break；随后 `resumeDownload` 连任务都找不到。加上 `RESUME` 分支对无 `Range` 头的全新响应做 `inputStream.skip(hasDownloadSize)` 本身也不成立。原 PR 描述里"暂停分支刻意不清理 `.tmp`，保留断点续传语义"的说法与代码不符，已一并删除该表述。`calculate()` 里的 `PAUSED` / `RESUME` 分支与 `DownloadStatus` 枚举暂留（已标注不可达），等真正按 `Range` 实现续传时复用

### 6. 磁盘后缀与展示后缀保持一致（`WKFileProvider.kt`）

`buildCacheFileName()` 新增可选 `displayName` 参数，扩展名优先取自 `fileContent.name` 的后缀，`extension` 作为回落。

展示层的预览类型、MIME、分享标题全部按 `name` 的后缀判定（第 1 节的解耦改动所致），若磁盘后缀仍只认 `fileContent.extension`，两者不一致时（`name="report.pdf"` 而 `extension="bin"`）会产生 MIME 为 `application/pdf` 但 `content://` URI 以 `.bin` 结尾的错配，按后缀嗅探而非 MIME 的接收方会判错类型。两者同源时结果不变。不是安全问题（文件在应用私有外部存储 + FileProvider 后面），但改动前两者是由构造保证一致的，不应由本次改动引入不一致。

### 7. 缓存大小校验的逃生通道（`WKFileProvider.kt`）

`isValidCache()` 提升为 companion 里的 `isValidFileCache(file, size)`（合并转发路径复用同一实现，不再各写一份内联判断），并新增例外：本次进程内已完整下载并落地过的路径直接放行，由 `markDownloadVerified()` 在下载成功回调里登记（进程内 `ConcurrentHashMap`，不持久化）。

原因：`size` 由发送方上报（`ChatActivity` / `ShareReceiveActivity` / `ChatPanelManager`），一旦上报不准，严格相等会让这条消息陷入"每次点击都重下、下完又因不等而不认"的死循环（`onSuccess` 只校验 `exists()` 所以仍会打开，但下次点击重复整个过程）。由于下载已改为写 `.tmp` + 完成后 rename，能出现在最终路径上的文件必然是完整的，此时以实际落地为准比以上报 `size` 为准更可靠。

### 8. 图片 / 视频预览链路

核对后未改动。`WKImageContent` 没有可用于校验的大小字段（仅 width/height），视频封面同理；且两处现有判断已包含 `exists() && length() > 0`。为此引入新字段收益不明确，本次不做改造。

同理，本次保留 `localPath` 作为持久化字段配合回写，没有改成按需计算的属性——那需要改动 `WKMediaMessageContent` 基类及其全部子类（图片/视频/语音/文件），影响面过大，不属于本次修复范围。

## 与 iOS 实现的对照

核对了 `octo-ios` 的同链路实现（`WKMediaUtil.m` / `WKMediaMessageContent.m` / `WKMergeForwardDetailCell.m` / `WKMergeForwardContent.m`），有三点值得记录：

1. **iOS 的 `localPath` 是计算属性，不是持久化字段**（`WKMediaMessageContent.m:52-56`）：
   `{messageFileRootDir}/{uid}/{channelType}/{channelId}/{clientMsgNo}{ext}`，每次访问重新拼。
   所以 iOS 结构上不存在本次 P1-2 那类"回写 content 丢字段"的问题 —— 它根本不写回。
   Android 要对齐需要改 `WKMediaMessageContent` 基类及其全部子类，影响面过大，本次仍保留
   `localPath` 持久化字段，改用 merge 回写来消除有损覆盖（见第 3 节）。

2. **iOS 路径始终按频道隔离，没有共享的 `forward/` 目录**：合并转发子消息在
   `WKMessageUtil.toMessage` 里会拿到真实的 `channel`（`channel_id` / `channel_type` 随
   payload 下发，见 `messageToDict:` 第 149-152 行），因此落在各自频道目录下。
   Android 的 `WKMultiForwardContent.decodeMsg` 既不读也不写 `channel_id`，拿不到频道作用域，
   所以保留共享 `forward/` 目录，靠 `messageID` 本身的全局唯一性区分。

3. **iOS 的缓存键就是 `message.messageId`**（`WKMergeForwardDetailCell.m:120-122` 的
   `msgKey = @(message.messageId)`），与本次第 4 节的选择一致 —— 这也反向印证了用
   `clientMsgNO` 做合并转发的键是错的：iOS 的 `messageToDict:` 压根不下发 `client_msg_no`。

4. **iOS 有在途下载守卫**，即第 5 节新增的那条（`if (downloadingMessages()[msgKey]) return`）。

## 影响范围

- 普通聊天文件消息的下载、缓存命中、预览/打开/分享/保存
- 合并转发详情页的文件下载与打开
- `Downloader` 为下载公共实现，语音等其他下载链路一并受益于原子落地与失败回调修复
- 语音消息的 `localPath` 回写改走 merge 方法（修掉既有的 content 字段丢失隐患），播放链路行为不变
- 新增 `MsgManager.updateContentLocalPath()` / `MsgDbManager.mergeContentFieldsWithClientMsgNo()` 为纯新增 API，不改动既有 `updateContentAndRefresh` 的任何调用方（`WKConnection` 的两处传的是完整 content 字符串而非模型，无损，未改）

旧版本遗留在原路径（以原始文件名命名）的缓存文件不会再被命中，首次点击会重新下载一次，之后走新路径缓存。

## 验证

- [x] `:wkim:compileDebugJavaWithJavac` / `:wkbase:compileDebugKotlin` / `:wkuikit:compileDebugKotlin` 编译通过，`assembleDebug` 整包构建通过（无新增警告：`MsgManager` 的 unchecked 提示与 `Downloader.kt` 的 `Condition is always 'false'` 在干净基线上同样存在）
- [x] 缓存文件名构造逻辑针对异常输入验证：`extension` 为 `null` / `""` / `"."` 时回落 `dat`；`.pdf` 归一为 `pdf`；多段扩展 `tar.gz` 保留；含 `../` 与路径分隔符的缓存键、`extension` 均收敛为单段安全名，无目录穿越
- [x] merge 语义离线验证：对含 `space_id` / `mention` / `entities` / `reply` / `robot_id` / `type` 的真实形状 payload merge `localPath` 后，上述字段全部原样保留；对照 `encodeMsg()` 整列覆盖路径确认这些字段确实会被抹掉
- [ ] 同一会话发送两个同名不同内容的文件，依次点开，确认各自下载并打开真实内容
- [ ] 下载过程中断网，确认 UI 收到失败提示而非进度条卡死；重试能正常下载，不会打开半截文件
- [ ] 合并转发详情页：点开文件下载后**关闭再重开详情页**，确认第二次点击直接命中本地、不重新下载（验证 key 稳定性）
- [ ] 合并转发详情页重复同名文件场景，确认按消息隔离生效
- [ ] 确认下载成功后 `localPath` 已回写，再次点击走本地命中分支
- [ ] Space 场景回归：Space A 的 DM 收到带 `space_id` 的文件消息 → 点开下载 → 切到 Space B，确认该会话预览仍被正确隐藏（即 `space_id` 未被回写抹掉）
- [ ] 带 @ALL 的文件消息下载后，确认 @ 标记仍在
- [ ] 语音消息下载播放后，确认同上两项（`space_id` / @ 标记）未丢失，且播放、已读状态、红点行为不变
- [ ] 确认预览 / 打开 / 分享 / 保存四个入口展示的文件名仍是原始文件名，而非缓存键名
- [ ] 文件 URL 已过期（服务端返回 403/404）时点击，确认进度条隐藏 + 弹出失败 toast，而非永久卡住
- [ ] 快速双击文件行，确认只发起一次下载、打开的文件内容正确（无交错写入导致的损坏）
- [ ] 下载失败后再次点击同一文件，确认能正常重新发起下载（回调注册表未被泄漏的旧条目卡住）
