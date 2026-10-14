/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.chat.provider

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConstants
import com.chat.base.msgcontent.WKFileContent
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.ud.WKDownloader
import com.chat.base.net.ud.WKProgressManager
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xinbida.wukongim.WKIM
import java.io.File
import java.util.Locale

class WKFileProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_file, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val fileView = parentView.findViewById<LinearLayout>(R.id.fileView)
        fileView.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)

        val fileNameTv = parentView.findViewById<TextView>(R.id.fileNameTv)
        val fileSizeTv = parentView.findViewById<TextView>(R.id.fileSizeTv)
        val fileIconIv = parentView.findViewById<ImageView>(R.id.fileIconIv)
        val progressBar = parentView.findViewById<ProgressBar>(R.id.fileProgressBar)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)

        val fileContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKFileContent
        if (fileContent == null) {
            fileNameTv.text = uiChatMsgItemEntity.wkMsg.baseContentMsgModel?.displayContent
                ?: context.getString(R.string.base_unknow_msg)
            fileSizeTv.text = ""
            return
        }

        fileNameTv.text = fileContent.name ?: ""
        fileSizeTv.text = formatFileSize(fileContent.size)

        setFileIcon(fileIconIv, fileContent.extension, fileContent.name)
        resetCellBackground(parentView, uiChatMsgItemEntity, from)

        // Upload progress for sending
        if (TextUtils.isEmpty(fileContent.url)) {
            WKProgressManager.instance.registerProgress(uiChatMsgItemEntity.wkMsg.clientSeq,
                object : WKProgressManager.IProgress {
                    override fun onProgress(tag: Any?, progress: Int) {
                        if (tag is Long && tag == uiChatMsgItemEntity.wkMsg.clientSeq) {
                            progressBar.visibility = View.VISIBLE
                            progressBar.progress = progress
                            if (progress >= 100) {
                                progressBar.visibility = View.GONE
                            }
                        }
                    }

                    override fun onSuccess(tag: Any?, path: String?) {
                        progressBar.visibility = View.GONE
                        if (tag != null) {
                            WKProgressManager.instance.unregisterProgress(tag)
                        }
                    }

                    override fun onFail(tag: Any?, msg: String?) {
                        progressBar.visibility = View.GONE
                        if (tag != null) {
                            WKProgressManager.instance.unregisterProgress(tag)
                        }
                    }
                })
        }

        // Click to download/open file
        contentLayout.setOnClickListener {
            handleFileClick(uiChatMsgItemEntity, fileContent, progressBar)
        }

        addLongClick(contentLayout, uiChatMsgItemEntity)
    }

    override val itemViewType: Int
        get() = WKContentType.WK_FILE

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        contentLayout.setAll(bgType, from, WKContentType.WK_FILE)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        addLongClick(contentLayout, uiChatMsgItemEntity)
    }

    private fun handleFileClick(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        fileContent: WKFileContent,
        progressBar: ProgressBar
    ) {
        // Check local file first（size 校验防止半截/过期文件被当作有效缓存）
        if (!TextUtils.isEmpty(fileContent.localPath)) {
            val file = File(fileContent.localPath)
            if (file.exists() && isValidCache(file, fileContent)) {
                openFile(file, fileContent.name)
                return
            }
        }

        // Check download directory
        val downloadDir = WKConstants.chatDownloadFileDir +
                uiChatMsgItemEntity.wkMsg.channelType + "/" +
                uiChatMsgItemEntity.wkMsg.channelID + "/"
        WKFileUtils.getInstance().createFileDir(downloadDir)
        // 缓存键 = clientMsgNO（参照 iOS WKMediaUtil.getLocalPath：路径由消息唯一标识结构性推导），
        // 不用发送方可控的 fileContent.name 做文件名，避免同名不同内容命中旧文件。
        // 原始文件名仅在展示层使用（BottomSheet 标题/分享/保存），与磁盘路径解耦。
        val fileName = buildCacheFileName(
            uiChatMsgItemEntity.wkMsg.clientMsgNO,
            fileContent.extension,
            fileContent.name
        )
        val filePath = downloadDir + fileName
        val file = File(filePath)
        if (file.exists() && isValidCache(file, fileContent, uiChatMsgItemEntity.wkMsg.clientMsgNO)) {
            openFile(file, fileContent.name)
            return
        }

        // Download file
        if (TextUtils.isEmpty(fileContent.url)) {
            WKToastUtils.getInstance()
                .showToastNormal(context.getString(R.string.str_file_not_exist))
            return
        }
        val downloadUrl = WKApiConfig.getShowUrl(fileContent.url)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        WKDownloader.instance.download(downloadUrl, filePath,
            object : WKProgressManager.IProgress {
                override fun onProgress(tag: Any?, progress: Int) {
                    progressBar.progress = progress
                }

                override fun onSuccess(tag: Any?, path: String?) {
                    progressBar.visibility = View.GONE
                    val downloadedFile = File(filePath)
                    if (downloadedFile.exists()) {
                        // 标记为"本次进程内已完整落地"，供 isValidCache 在发送方上报
                        // size 不准时放行，避免陷入每次点击都重下的死循环
                        markDownloadVerified(downloadedFile, uiChatMsgItemEntity.wkMsg.clientMsgNO)
                        // 回写 localPath 并持久化，下次点击直接命中本地文件。
                        // 必须走 updateContentLocalPath（merge 单键），不能用
                        // updateContentAndRefresh(model)——后者按 encodeMsg() 整列覆盖
                        // content，会抹掉顶层的 space_id / mention / reply / entities /
                        // robot_id（WKFileContent.encodeMsg 只输出 5 个字段），导致
                        // Space 隔离与 @ 提及状态静默失效。
                        fileContent.localPath = filePath
                        val merged = WKIM.getInstance().msgManager.updateContentLocalPath(
                            uiChatMsgItemEntity.wkMsg.clientMsgNO,
                            filePath,
                            false
                        )
                        if (!merged) {
                            // false = DB 里没有该 clientMsgNO 的行（如嵌套转发消息）或
                            // content JSON 已损坏——本次内存态 localPath 仍生效，仅缺持久化。
                            Log.w(TAG, "updateContentLocalPath merged nothing, clientMsgNO=${uiChatMsgItemEntity.wkMsg.clientMsgNO}")
                        }
                        openFile(downloadedFile, fileContent.name)
                    }
                }

                override fun onFail(tag: Any?, msg: String?) {
                    progressBar.visibility = View.GONE
                    WKToastUtils.getInstance()
                        .showToastNormal(context.getString(R.string.str_file_download_fail))
                }
            })
    }

    private fun openFile(file: File, displayName: String? = null) {
        openFileWithOptions(context, file, displayName)
    }

    /** 见 [isValidFileCache]。 */
    private fun isValidCache(file: File, fileContent: WKFileContent, identity: String? = null): Boolean =
        isValidFileCache(file, fileContent.size, identity)

    companion object {
        private const val TAG = "WKFileProvider"

        /**
         * 本次进程内已完整下载并 rename 落地的记录：路径 → (产生该文件的**原始身份串**, 落地字节数)。
         *
         * 记身份串而不是磁盘文件名：磁盘名是身份经 SHA-256（新路径）或经 sanitize+截断
         * （旧 clientMsgNO 路径）派生的结果。把派生结果当校验键，在派生不是单射的场合
         * 等于没有校验——两条消息塌到同一磁盘名时，它们的"校验键"也相同，A 的字节会被
         * B 当成有效缓存（B-1）。身份串保留原始字节，才能真正区分。
         *
         * LruCache 限容 256 条，避免随进程生命周期无界增长。
         */
        private class VerifiedEntry(val identity: String, val length: Long)

        private val verifiedDownloads = object : android.util.LruCache<String, VerifiedEntry>(256) {}

        /**
         * 记录一个已完整下载并落地的 (路径, 身份串, 字节数)（下载成功回调里调用）。
         *
         * @param identity 产生该文件的原始身份串。必须与后续 [isValidFileCache] 传入的
         *                 身份串同源（转发路径传 WKCacheKeys.entryIdentity 的结果，普通
         *                 聊天路径传 clientMsgNO）。为 null 时退化为 file.name——仅给
         *                 没有身份可绑的历史调用点兜底，新代码不要依赖。
         */
        @JvmStatic
        @JvmOverloads
        fun markDownloadVerified(file: File, identity: String? = null) {
            val id = identity ?: file.name
            synchronized(verifiedDownloads) {
                verifiedDownloads.put(file.absolutePath, VerifiedEntry(id, file.length()))
            }
        }

        /**
         * 缓存有效性。判定顺序是本函数的正确性核心，不可调换：
         *
         * 1. 该路径有本进程的落地记录时：**先比身份**。身份不符即判无效（返回 false），
         *    不管 size 是否相等。旧实现把 `length == size` 的快速返回放在身份检查之前，
         *    于是两条消息塌到同一路径、且 B 上报的 size 恰等于 A 的落地长度时，B 直接
         *    拿到 A 的字节（B-1 的等长分支）。身份相符时再要求磁盘长度与落地长度一致
         *    （防落地后被外部改动）。
         * 2. 没有落地记录时（冷启动后的历史缓存）：退化为 size 校验——`size > 0` 要求
         *    `file.length() == size`，`size <= 0`（服务端未下发）仅判存在。此时没有任何
         *    身份信息可比，这是已知的残留风险面，但不引入新的错配：路径本身已由
         *    SHA-256 身份派生（见 [buildCacheFileNameForIdentity]），不同身份不同路径。
         *
         * @param identity 请求方的原始身份串；null 时退化为 file.name（历史调用点兜底）
         */
        @JvmStatic
        @JvmOverloads
        fun isValidFileCache(file: File, size: Long, identity: String? = null): Boolean {
            val length = file.length()
            val requestIdentity = identity ?: file.name
            val entry = synchronized(verifiedDownloads) { verifiedDownloads.get(file.absolutePath) }
            if (entry != null) {
                // 有落地记录：身份是第一道门，等长不能绕过它
                if (entry.identity != requestIdentity) return false
                return entry.length == length
            }
            if (size <= 0L) return true
            return length == size
        }

        /**
         * 按"完整原始身份"派生磁盘缓存文件名：`<SHA-256(identity)>.<ext>`。
         *
         * 与 [buildCacheFileName] 的区别是**第一个参数不再是会被清洗/截断的键**，而是
         * 原始身份串（见 WKCacheKeys.entryIdentity）。basename 取身份串的 SHA-256 全量
         * 十六进制（64 字符，只含 [0-9a-f]），因此清洗与截断都不会再破坏唯一性——这是
         * B-1 的修复点：旧路径先把 messageID 的 `:` `?` 等字符统统替换成 `_`、再截到
         * 64 字符，`m:a` 与 `m?a` 塌成同一个磁盘名，先下载的那个的字节会被另一条消息
         * 当成缓存打开。
         *
         * 扩展名仍取自展示名后缀（与 [buildCacheFileName] 同源同理由：预览类型/MIME/
         * 分享标题都按展示名后缀判定），并同样过 sanitize + 字节截断。
         */
        @JvmStatic
        @JvmOverloads
        fun buildCacheFileNameForIdentity(
            identity: String,
            extension: String?,
            displayName: String? = null
        ): String {
            val base = com.chat.base.utils.WKCacheKeys.basenameForIdentity(identity)
            val fromDisplay = displayName?.substringAfterLast('.', "")
            val preferred = if (!fromDisplay.isNullOrEmpty()) fromDisplay else extension
            val ext = sanitizePathSegment(preferred?.removePrefix(".")).ifEmpty { "dat" }
            return truncateToByteLength("$base.$ext", MAX_FILENAME_BYTES)
        }

        /**
         * 本地缓存文件名：`<缓存键>.<ext>`，以消息唯一标识做缓存键
         * （参照 iOS WKMediaUtil.getLocalPath），避免同名不同内容互相命中。
         *
         * 缓存键与扩展名都可能来自对端/服务端 JSON（`optString`），直接拼路径会被
         * `../` 穿越，故两者都只保留 basename 并清洗危险字符。
         *
         * 扩展名优先取自 `displayName`（即 `fileContent.name`）的后缀：展示层的
         * 预览类型、MIME、分享标题全部按 `name` 的后缀判定，若磁盘后缀改用
         * `fileContent.extension`，两者不一致时（name="report.pdf" 而
         * extension="bin"）会出现 MIME 为 pdf 但 content:// URI 以 .bin 结尾的错配，
         * 按后缀嗅探的接收方会判错类型。两者同源时结果不变。
         *
         * 最终名按 UTF-8 字节截断到 [MAX_FILENAME_BYTES]：key 通常定长（~45 字节），
         * 但扩展名完全由对端控制、无上限，长中文/emoji 名（3~4 字节/字符）在
         * ext4/f2fs 的 NAME_MAX=255 下必然 `ENAMETOOLONG`，而该异常落在下载器的
         * 流构造处，用户观感是"点了没反应"。留出余量给下载器的 `.tmp` 后缀（4 字节）。
         */
        @JvmStatic
        @JvmOverloads
        fun buildCacheFileName(
            cacheKey: String?,
            extension: String?,
            displayName: String? = null
        ): String {
            val key = sanitizePathSegment(cacheKey).ifEmpty { "unknown" }
            val fromDisplay = displayName?.substringAfterLast('.', "")
            val preferred = if (!fromDisplay.isNullOrEmpty()) fromDisplay else extension
            val ext = sanitizePathSegment(preferred?.removePrefix(".")).ifEmpty { "dat" }
            return truncateToByteLength("$key.$ext", MAX_FILENAME_BYTES)
        }

        /** 去掉路径分隔符与危险字符，只留下单段安全名（防目录穿越）。 */
        private fun sanitizePathSegment(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            // File(..).name 取 basename，消除 "a/../b" 这类分隔符结构
            return File(raw).name
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace("..", "")
                .replace(Regex("[\\x00-\\x1f]"), "")
                .trim()
        }

        /**
         * 按 UTF-8 字节长度截断文件名，保留扩展名，按 code point 边界截取避免破坏字符。
         *
         * Linux NAME_MAX = 255 字节（UTF-8，不是字符）。上限取 [MAX_FILENAME_BYTES]
         * （200），预留 55 字节：4 字节给下载器的 `.tmp` 后缀，其余给挂载点/未来变化。
         * 溢出时先压缩扩展名到 16 字节，再截主名——扩展名对类型判定有意义，主名是
         * 定长键，被截后仍大概率唯一。
         */
        private fun truncateToByteLength(name: String, maxBytes: Int): String {
            if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
            val dot = name.lastIndexOf('.')
            val ext = if (dot > 0) name.substring(dot) else ""
            val base = if (dot > 0) name.substring(0, dot) else name
            val extBytes = ext.toByteArray(Charsets.UTF_8).size
            val finalExt = if (extBytes > MAX_EXT_BYTES) {
                // 扩展名本身超长（对端可构造）：截到 16 字节并保底可读。
                // 按 code point 而不是 UTF-16 Char 推进——逐 Char 会在 emoji 的代理对中间
                // 断开，产出半个字符的非法 UTF-8 序列（与上方"按 code point 边界"的契约矛盾）。
                var s = ""
                var used = 0
                var j = 0
                while (j < ext.length) {
                    val cp = ext.codePointAt(j)
                    val cc = Character.charCount(cp)
                    val cpStr = ext.substring(j, j + cc)
                    val b = cpStr.toByteArray(Charsets.UTF_8).size
                    if (used + b > MAX_EXT_BYTES) break
                    s += cpStr
                    used += b
                    j += cc
                }
                if (s.isEmpty()) ".dat" else s
            } else {
                ext
            }
            val baseBudget = (maxBytes - finalExt.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
            val sb = StringBuilder()
            var used = 0
            var i = 0
            while (i < base.length) {
                val cp = base.codePointAt(i)
                val charCount = Character.charCount(cp)
                val cpStr = base.substring(i, i + charCount)
                val chBytes = cpStr.toByteArray(Charsets.UTF_8).size
                if (used + chBytes > baseBudget) break
                sb.append(cpStr)
                used += chBytes
                i += charCount
            }
            if (sb.isEmpty()) sb.append("file")
            return sb.toString() + finalExt
        }

        private const val MAX_EXT_BYTES = 16

        /** 文件名 UTF-8 字节长度上限（Linux NAME_MAX=255，留 55 字节余量给临时后缀/挂载点差异）。 */
        private const val MAX_FILENAME_BYTES = 200

        /** 文本预览截断阈值（字符数），与 [TextPreviewActivity] 内部常量对齐。 */
        private const val TEXT_PREVIEW_MAX_CHARS = 50_000

        /** App 内以纯文本形式展示（走 [TextPreviewActivity]）的扩展名白名单。
         *  同时驱动"打开"路径的 dispatch 与 BottomSheet "预览"按钮的显隐。 */
        private val TEXT_PREVIEW_EXTS = setOf(
            "txt", "md", "json", "yaml", "yml", "xml", "csv", "log",
            "conf", "cfg", "ini", "properties", "toml",
            "html", "htm", "css", "js", "ts", "py", "go", "java", "kt",
            "sh", "bat", "sql", "gradle", "swift", "c", "cpp", "h",
            "rb", "php", "rs", "lua", "r", "pl", "env", "gitignore",
        )

        @JvmStatic
        fun openFileWithOptions(context: Context, file: File, displayName: String? = null) {
            val activity = context as? Activity ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            val dialog = BottomSheetDialog(activity)
            val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_file_actions, null)
            // 磁盘文件名已改为 clientMsgNO 键（与展示名解耦），展示层优先用消息里的原始文件名
            // 对外暴露的 content:// URI 也用四参 getUriForFile 覆盖 DISPLAY_NAME，否则分享/保存/ACTION_VIEW 的接收方会拿到 UUID 形状的缓存名。
            val display = displayName?.takeIf { it.isNotEmpty() } ?: file.name

            // 预览按钮：pdf / xlsx / 纯文本 显示，走 App 内预览；与"打开"（系统 Intent）分开走两条路
            // 避免影响原有"打开"逻辑。对齐 iOS WKSafeFilePreviewVC。xls / doc / ppt 老格式暂不支持。
            val ext = display.substringAfterLast('.', "").lowercase(Locale.getDefault())
            val previewClass: Class<*>? = when {
                ext == "pdf" -> PdfPreviewActivity::class.java
                ext == "xlsx" -> XlsxPreviewActivity::class.java
                ext in TEXT_PREVIEW_EXTS -> TextPreviewActivity::class.java
                else -> null
            }
            val previewView = sheetView.findViewById<View>(R.id.actionPreview)
            if (previewClass != null) {
                previewView.visibility = View.VISIBLE
                previewView.setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(context, previewClass).apply {
                        putExtra("title", display)
                        putExtra("filePath", file.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                previewView.visibility = View.GONE
            }

            sheetView.findViewById<View>(R.id.actionOpen).setOnClickListener {
                dialog.dismiss()
                openFileDirectlyStatic(context, file, display)
            }
            sheetView.findViewById<View>(R.id.actionSave).setOnClickListener {
                dialog.dismiss()
                val intent = Intent(context, FileSaveActivity::class.java)
                intent.putExtra("sourceFilePath", file.absolutePath)
                intent.putExtra("fileName", display)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            sheetView.findViewById<View>(R.id.actionShare).setOnClickListener {
                dialog.dismiss()
                try {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file, display)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = getMimeType(display)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, display)
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                } catch (_: Exception) {}
            }
            sheetView.findViewById<View>(R.id.actionCancel).setOnClickListener {
                dialog.dismiss()
            }

            val fileNameTv = sheetView.findViewById<TextView>(R.id.sheetFileName)
            fileNameTv?.text = display

            dialog.setContentView(sheetView)
            dialog.show()
        }

        private fun openFileDirectlyStatic(context: Context, file: File, displayName: String? = null) {
            // 磁盘文件名是 clientMsgNO 键，系统预览/打开的标题与 MIME 应基于原始展示名
            val display = displayName?.takeIf { it.isNotEmpty() } ?: file.name
            val ext = display.substringAfterLast('.', "").lowercase(Locale.getDefault())
            if (ext in TEXT_PREVIEW_EXTS) {
                // bounded 后主线程读上限 50k 字符 (≈200KB UTF-8) 通常 <10ms，避免旧
                // `readText()` 全文加载导致 100MB `.log` OOM/ANR (#92 review B5)。
                val content = TextPreviewLoader.readBounded(file, TEXT_PREVIEW_MAX_CHARS)
                val intent = Intent(context, TextPreviewActivity::class.java)
                intent.putExtra("title", display)
                intent.putExtra("content", content)
                intent.putExtra("filePath", file.absolutePath)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file, display)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, getMimeType(display))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                WKToastUtils.getInstance().showToastNormal(context.getString(R.string.str_file_no_app))
            } catch (_: Exception) {
                WKToastUtils.getInstance().showToastNormal(context.getString(R.string.str_file_not_exist))
            }
        }

        @JvmStatic
        fun setFileIcon(imageView: ImageView, extension: String?, fileName: String?) {
            var ext = extension?.removePrefix(".")?.lowercase(Locale.getDefault()) ?: ""
            if (ext.isEmpty() && !fileName.isNullOrEmpty()) {
                ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
            }
            val iconRes = when (ext) {
                "pdf" -> R.mipmap.ic_file_pdf
                "doc", "docx" -> R.mipmap.ic_file_word
                "xls", "xlsx" -> R.mipmap.ic_file_excel
                "ppt", "pptx" -> R.mipmap.ic_file_ppt
                "md", "markdown" -> R.mipmap.ic_file_markdown
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> R.mipmap.ic_file_video
                else -> R.drawable.ic_file_document
            }
            imageView.setImageResource(iconRes)
        }
        @JvmStatic
        fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024))
                else -> String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024 * 1024))
            }
        }

        fun getMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
            return when (ext) {
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                "txt" -> "text/plain"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "mkv" -> "video/x-matroska"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "apk" -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
        }
    }
}
