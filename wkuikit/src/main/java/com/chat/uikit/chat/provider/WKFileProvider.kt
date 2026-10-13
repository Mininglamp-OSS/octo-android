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
        if (file.exists() && isValidCache(file, fileContent)) {
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
                        markDownloadVerified(downloadedFile)
                        // 回写 localPath 并持久化，下次点击直接命中本地文件。
                        // 必须走 updateContentLocalPath（merge 单键），不能用
                        // updateContentAndRefresh(model)——后者按 encodeMsg() 整列覆盖
                        // content，会抹掉顶层的 space_id / mention / reply / entities /
                        // robot_id（WKFileContent.encodeMsg 只输出 5 个字段），导致
                        // Space 隔离与 @ 提及状态静默失效。
                        fileContent.localPath = filePath
                        WKIM.getInstance().msgManager.updateContentLocalPath(
                            uiChatMsgItemEntity.wkMsg.clientMsgNO,
                            filePath,
                            false
                        )
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
    private fun isValidCache(file: File, fileContent: WKFileContent): Boolean =
        isValidFileCache(file, fileContent.size)

    companion object {

        /**
         * 本次进程内已完整下载并 rename 落地的文件路径，供 [isValidCache] 在发送方
         * 上报 `size` 不准时作为逃生通道（见该方法注释）。仅进程内有效，不做持久化。
         */
        private val verifiedDownloads: MutableSet<String> =
            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

        /** 记录一个已完整下载并落地的路径（下载成功回调里调用）。 */
        @JvmStatic
        fun markDownloadVerified(file: File) {
            verifiedDownloads.add(file.absolutePath)
        }

        /**
         * 缓存有效性：`size > 0` 时要求 `file.length() == size`，防止中断残留的半截文件
         * 被命中；`size <= 0`（服务端未下发）退化为仅判断存在。
         *
         * 例外：本次进程内已完整下载并落地过的路径直接放行。`size` 由发送方上报
         * （ChatActivity / ShareReceiveActivity / ChatPanelManager），一旦上报不准，
         * 严格相等会让这条消息陷入"每次点击都重下、下完又因不等而不认"的死循环
         * （onSuccess 只校验 exists() 所以仍会打开，但下次点击重复整个过程）。
         * 由于下载已改为写 .tmp + 完成后 rename，能出现在最终路径上的文件必然是完整的，
         * 此时以实际落地为准比以上报 size 为准更可靠。
         */
        @JvmStatic
        fun isValidFileCache(file: File, size: Long): Boolean {
            if (size <= 0L) return true
            if (file.length() == size) return true
            return verifiedDownloads.contains(file.absolutePath)
        }

        /**
         * 本地缓存文件名：`<缓存键>.<ext>`，以消息唯一标识做缓存键
         * （参照 iOS WKMediaUtil.getLocalPath），避免同名不同内容互相命中。
         *
         * 缓存键与 extension 都可能来自对端/服务端 JSON（`optString`），
         * 直接拼路径会被 `../` 穿越，故两者都只保留 basename 并清洗危险字符。
         *
         * 扩展名优先取自 `displayName`（即 `fileContent.name`）的后缀：展示层的
         * 预览类型、MIME、分享标题全部按 `name` 的后缀判定，若磁盘后缀改用
         * `fileContent.extension`，两者不一致时（name="report.pdf" 而
         * extension="bin"）会出现 MIME 为 pdf 但 content:// URI 以 .bin 结尾的错配，
         * 按后缀嗅探的接收方会判错类型。两者同源时结果不变。
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
            return "$key.$ext"
        }

        /** 去掉路径分隔符与危险字符，只留下单段安全名（防目录穿越）。 */
        private fun sanitizePathSegment(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            // File(..).name 取 basename，消除 "a/../b" 这类分隔符结构
            return File(raw).name
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace("..", "")
                .trim()
        }

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
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)
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
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)
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
