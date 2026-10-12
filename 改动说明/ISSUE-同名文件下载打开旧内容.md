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

文件下载成功后没有把实际落盘路径回写到 `fileContent.localPath`（语音消息 `WKVoiceProvider` 是有回写的），导致接收方每次点击都只能靠"文件名撞目录"来判断是否已缓存——这是该 bug 的结构性来源。

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
| `wkuikit/src/main/java/com/chat/uikit/chat/provider/WKVoiceProvider.kt` | 正确实现的参照：用 `clientMsgNO` 做文件名并回写 `localPath` |

## 修复方向

1. 下载路径以 `clientMsgNO` 作为缓存键，不使用发送方可控的文件名
2. 判断缓存有效性时补充文件大小校验，拒绝半截文件
3. 下载成功后回写 `localPath` 并持久化
4. 合并转发目录按消息隔离，去掉全局共享目录与常量兜底名
5. 下载改为先写临时文件、完成后原子 rename；失败时清理残留并回调失败
