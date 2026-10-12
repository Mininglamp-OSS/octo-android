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

### 2. 缓存有效性补充大小校验（`WKFileProvider.kt`）

新增 `isValidCache()`，在 `localPath` 分支和下载目录分支都要求 `file.length() == fileContent.size` 才认定缓存有效，中断残留的半截文件不会再被当作可用缓存。`size <= 0`（服务端未下发）时退化为仅判断存在，保持兼容。

发送方场景已确认不受影响：发送侧 `localPath` 与 `size = file.length()` 同源，大小必然精确匹配，不会出现发送方被迫重新下载自己文件的情况。

### 3. 下载成功回写 `localPath`（`WKFileProvider.kt` / `ChatMultiForwardDetailAdapter.kt`）

下载成功后把实际路径写回 `fileContent.localPath` 并通过 `updateContentAndRefresh()` 持久化，与语音消息的既有做法保持一致。后续点击直接命中本地文件，不再依赖"文件名撞目录"这种隐式缓存机制。

### 4. 合并转发目录按消息隔离（`ChatMultiForwardDetailAdapter.kt`）

`handleForwardFileClick()` 新增 `clientMsgNO` 参数（由调用处从 `item.msg` 传入），文件名同样走 `buildCacheFileName()`。去掉了全局共享路径下的文件名拼接，以及 `name` 为空时退化成常量 `"file.dat"` 的兜底逻辑——后者会让所有无名文件共用同一个路径。同步补齐大小校验与 `localPath` 回写。

### 5. 下载原子落地与失败清理（`Downloader.kt`）

- 下载期间写入 `{目标路径}.tmp`，完整收流后再 `renameTo()` 到目标路径；rename 失败（如跨挂载点）退化为复制覆盖后删除临时文件
- 读流异常时关闭流、删除残留 `.tmp`、置 `ERROR` 状态，避免半截文件被后续 `exists()` 命中
- `onFailure` 此前只把错误写进 task 对象，不删残file也不回调，导致上层的 `onFail`（隐藏进度条 + toast）永远不执行、进度条永久卡住。现在补齐残file清理与主线程 `onFail` 回调，并清理回调注册表
- 完成回调改为传入 rename 后的最终文件，避免上层拿到 `.tmp` 路径
- 暂停分支刻意不清理 `.tmp`，保留断点续传语义（`realDownload` 仅在 `fileOutputStream == null` 时新建流，恢复时复用）

### 6. 图片 / 视频预览链路

核对后未改动。`WKImageContent` 没有可用于校验的大小字段（仅 width/height），视频封面同理；且两处现有判断已包含 `exists() && length() > 0`。为此引入新字段收益不明确，本次不做改造。

同理，本次保留 `localPath` 作为持久化字段配合回写，没有改成按需计算的属性——那需要改动 `WKMediaMessageContent` 基类及其全部子类（图片/视频/语音/文件），影响面过大，不属于本次修复范围。

## 影响范围

- 普通聊天文件消息的下载、缓存命中、预览/打开/分享/保存
- 合并转发详情页的文件下载与打开
- `Downloader` 为下载公共实现，语音等其他下载链路一并受益于原子落地与失败回调修复

旧版本遗留在原路径（以原始文件名命名）的缓存文件不会再被命中，首次点击会重新下载一次，之后走新路径缓存。

## 验证

- [x] `:wkbase:compileDebugKotlin` / `:wkuikit:compileDebugKotlin` 编译通过
- [x] 缓存文件名构造逻辑针对异常输入验证：`extension` 为 `null` / `""` / `"."` 时回落 `dat`；`.pdf` 归一为 `pdf`；多段扩展 `tar.gz` 保留；含 `../` 与路径分隔符的 `clientMsgNO`、`extension` 均收敛为单段安全名，无目录穿越
- [ ] 同一会话发送两个同名不同内容的文件，依次点开，确认各自下载并打开真实内容
- [ ] 下载过程中断网，确认 UI 收到失败提示而非进度条卡死；重试能正常下载，不会打开半截文件
- [ ] 合并转发详情页重复同名文件场景，确认按消息隔离生效
- [ ] 确认下载成功后 `localPath` 已回写，再次点击走本地命中分支
- [ ] 确认预览 / 打开 / 分享 / 保存四个入口展示的文件名仍是原始文件名，而非 `clientMsgNO.ext`
