# 同名文件点开后打开的是旧文件内容

## 问题描述

同一会话里先后收到两个**文件名相同但内容不同**的文件，点开第二个文件时，直接打开的是第一个文件的内容，并且不会触发重新下载。

## 复现步骤

1. A 向 B 发送文件 `报价表.xlsx`（内容为版本 1）
2. B 点击该文件，正常下载并打开，内容正确
3. A 再次发送同名文件 `报价表.xlsx`（内容已改为版本 2）
4. B 点击第二条消息里的文件

**预期**：下载并打开版本 2 的内容
**实际**：瞬间打开，显示的是版本 1 的内容，没有任何下载过程

## 影响范围

- 普通聊天中的文件消息（单聊 / 群聊都有）
- 合并转发详情页里的文件，且影响面更大：该页的下载目录是全局共享的，**跨会话**的同名文件也会互相覆盖命中
- 走"预览"（PDF / xlsx / 纯文本）、"打开"、"分享"三条路径都会拿到错误的旧文件；只有"保存"因为走系统 SAF 由系统去重，不受影响

## 问题根源

下载缓存的判断键是**文件名**，而不是消息的唯一标识。

`WKFileProvider.handleFileClick()` 中，下载路径由发送方提供的 `fileContent.name` 直接拼成：

```kotlin
val rawName = fileContent.name ?: (clientMsgNO + "." + fileContent.extension)
val filePath = downloadDir + sanitizeFileName(rawName)
val file = File(filePath)
if (file.exists()) {
    openFile(file)   // 只要文件名撞上就直接打开并 return
    return
}
```

存在三个问题：

1. `clientMsgNO` 只在 `name == null` 时才会进入文件名。正常情况下文件名完全由发送方决定，**同名必然撞车**
2. `file.exists()` 是唯一的判断条件，不校验文件大小 / URL / 内容。而 `fileContent.size` 本来就是现成可用的（列表里显示文件大小时就在用它），却没参与校验
3. 合并转发详情页（`ChatMultiForwardDetailAdapter.handleForwardFileClick()`）的目录是全局共享的 `forward/`，不按会话或消息隔离，撞车范围比普通聊天更大；`name` 为空时还会退化成常量 `"file.dat"`，所有无名文件共用同一个路径

### 结构性成因

文件下载成功后没有把实际落盘路径回写到 `fileContent.localPath`（语音消息 `WKVoiceProvider` 是有回写的，但它的回写方式本身另有问题，见下文"回写 localPath 会抹掉 content 顶层协议字段"），导致接收方每次点击都只能靠"文件名撞目录"来判断是否已缓存——这是该 bug 的结构性来源。

### 关联问题：下载中断残留半截文件

`Downloader.kt` 直接用 `FileOutputStream` 写入目标路径，截断覆写、无原子落地：

- 下载中断（断网 / 杀后台）时，半截文件会留在目标路径上
- 这个残file会被下一次的 `file.exists()` 当作有效缓存**永久命中**，用户再也打不开正确内容
- 同时 `onFailure` 回调只把错误信息记录到 task 对象，既不删残file也不触发 `onFail`，上层已经写好的失败处理（隐藏进度条 + toast 提示）永远不会执行，UI 进度条会一直卡住

## 涉及文件

| 文件 | 角色 |
| --- | --- |
| `wkuikit/src/main/java/com/chat/uikit/chat/provider/WKFileProvider.kt` | 主问题点：文件消息点击、下载路径计算、缓存判断 |
| `wkuikit/src/main/java/com/chat/uikit/chat/adapter/ChatMultiForwardDetailAdapter.kt` | 合并转发详情中的文件点击，全局共享 `forward/` 目录 |
| `wkbase/src/main/java/com/chat/base/net/ud/Downloader.kt` | 下载实现：直接覆写目标文件、失败不清理残file、不回调失败 |
| `wkim/src/main/java/com/xinbida/wukongim/manager/MsgManager.java` | `updateContentAndRefresh(model)` 整列覆盖 content，回写 `localPath` 会丢顶层协议字段 |
| `wkim/src/main/java/com/xinbida/wukongim/db/MsgDbManager.java` | content 列的实际读写位置，merge 回写需在此实现 |
| `wkuikit/src/main/java/com/chat/uikit/chat/provider/WKVoiceProvider.kt` | 已有 `localPath` 回写，但同样走整列覆盖，存在相同的字段丢失隐患 |

## 关联问题：回写 localPath 会抹掉 content 顶层协议字段

`localPath` 回写如果走 `updateContentAndRefresh(clientMsgNo, model, ...)`，会用 `model.encodeMsg()` **整列替换** `content`。而 `WKFileContent.encodeMsg` 只输出 `name` / `extension` / `size` / `url` / `localPath`，`WKVoiceContent.encodeMsg` 只输出 4 个字段；`content` 顶层还存着一批模型不持有、但 `MsgManager.getMsgContentModel` 每次读库都要解析的字段：`mention` / `reply` / `entities` / `robot_id` / `space_id` / `from_uid`。

这些字段一旦被覆盖就永久丢失（该行不会再从服务端重新拉取），后果：

- **Space 隔离失效**：`SpaceFilter.extractSpaceIdFromMsg` 直接读 content 里的 `space_id`，`ConversationPreviewFilter.isMessageCrossSpace` 用它判跨 Space。字段丢失后走 fail-open，跨 Space 的 DM 预览不再被隐藏
- **@ 提及状态失效**：`mention` 全树从顶层解析，带 @ALL 的消息下载后标志位丢失

`flame` / `flame_second` 有独立列兜底，不受影响。触发条件极低——接收方点一下文件（纯只读操作）即触发。

## 关联问题：合并转发子消息的 clientMsgNO 是随机 UUID

合并转发的子消息是 `WKMultiForwardContent.decodeMsg` 的运行时反序列化产物。`decodeMsg` 只赋 `content` / `type` / `timestamp` / `messageID` / `fromUID` / `channelType`，**从不赋值 `clientMsgNO`** —— 该字段来自 `WKMsg` 构造函数的 `createClientMsgNO()` 兜底，是个随机 UUID。

两条进入详情页的路径（嵌套转发直接 `decodeMsg`；一级转发经 `serializeMsg → getMsgModel → getMsgContentModel`）每次打开都会重新 decode，所以该值每次都变。用它做缓存键会导致：

- 缓存永不命中，每次重开详情页都重新下载
- `forward/` 目录无界增长：每次打开写一个新 `<uuid>.<ext>`，无清理。该目录是 `getExternalFilesDir("chatDownloadFile")`，而 `DataCleanManager.clearAllCache` 只删 `getCacheDir()` / `getExternalCacheDir()`，设置里的「清除缓存」覆盖不到
- 该路径上的 `UPDATE ... WHERE client_msg_no = ?` 匹配不到任何行（子消息在 `message` 表里没有自己的行），是静默 no-op

## 修复方向

1. 普通聊天下载路径以 `clientMsgNO` 作为缓存键，不使用发送方可控的文件名
2. 判断缓存有效性时补充文件大小校验，拒绝半截文件
3. 下载成功后回写 `localPath` 并持久化，但必须走「merge 单键」而非整列覆盖，避免抹掉顶层协议字段；语音链路的既有回写一并收口
4. 合并转发目录按消息隔离，键用跨 decode 稳定的 `messageID`（缺失时回落下标复合键），不用 `clientMsgNO`；该路径不做 `localPath` 持久化（无对应 DB 行）
5. 下载改为先写临时文件、完成后原子 rename；失败时清理残留并回调失败
