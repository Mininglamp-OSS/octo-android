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

    private fun sanitizeFileName(name: String): String {
        // Extract basename to prevent path traversal (../ or /)
        val basename = File(name).name
        // Remove any remaining dangerous characters
        val cleaned = basename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        // Linux 文件系统 NAME_MAX = 255 字节 (UTF-8, 不是字符)。
        // 长中文/emoji 文件名（3~4 字节/字符）很容易越界，导致 FileOutputStream 抛
        // ENAMETOOLONG 静默失败，用户观感是"点了没反应"。留 55 字节余量。
        return truncateToByteLength(cleaned, MAX_FILENAME_BYTES)
    }

    /** 按 UTF-8 字节长度截断文件名，保留扩展名，按 code point 边界截取避免破坏字符。 */
    private fun truncateToByteLength(name: String, maxBytes: Int): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot) else ""
        val base = if (dot > 0) name.substring(0, dot) else name
        val extBytes = ext.toByteArray(Charsets.UTF_8).size
        val baseBudget = (maxBytes - extBytes).coerceAtLeast(1)

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
        return sb.toString() + ext
    }

    private fun handleFileClick(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        fileContent: WKFileContent,
        progressBar: ProgressBar
    ) {
        // Check local file first
        if (!TextUtils.isEmpty(fileContent.localPath)) {
            val file = File(fileContent.localPath)
            if (file.exists()) {
                openFile(file)
                return
            }
        }

        // Check download directory
        val downloadDir = WKConstants.chatDownloadFileDir +
                uiChatMsgItemEntity.wkMsg.channelType + "/" +
                uiChatMsgItemEntity.wkMsg.channelID + "/"
        WKFileUtils.getInstance().createFileDir(downloadDir)
        val rawName = fileContent.name ?: (uiChatMsgItemEntity.wkMsg.clientMsgNO + "." + fileContent.extension)
        val fileName = sanitizeFileName(rawName)
        val filePath = downloadDir + fileName
        val file = File(filePath)
        if (file.exists()) {
            openFile(file)
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
                        openFile(downloadedFile)
                    }
                }

                override fun onFail(tag: Any?, msg: String?) {
                    progressBar.visibility = View.GONE
                    WKToastUtils.getInstance()
                        .showToastNormal(context.getString(R.string.str_file_download_fail))
                }
            })
    }

    private fun openFile(file: File) {
        openFileWithOptions(context, file)
    }

    companion object {

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
        fun openFileWithOptions(context: Context, file: File) {
            val activity = context as? Activity ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            val dialog = BottomSheetDialog(activity)
            val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_file_actions, null)

            // 预览按钮：pdf / xlsx / 纯文本 显示，走 App 内预览；与"打开"（系统 Intent）分开走两条路
            // 避免影响原有"打开"逻辑。对齐 iOS WKSafeFilePreviewVC。xls / doc / ppt 老格式暂不支持。
            val ext = file.extension.lowercase(Locale.getDefault())
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
                        putExtra("title", file.name)
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
                openFileDirectlyStatic(context, file)
            }
            sheetView.findViewById<View>(R.id.actionSave).setOnClickListener {
                dialog.dismiss()
                val intent = Intent(context, FileSaveActivity::class.java)
                intent.putExtra("sourceFilePath", file.absolutePath)
                intent.putExtra("fileName", file.name)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            sheetView.findViewById<View>(R.id.actionShare).setOnClickListener {
                dialog.dismiss()
                try {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = getMimeType(file.name)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, file.name)
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                } catch (_: Exception) {}
            }
            sheetView.findViewById<View>(R.id.actionCancel).setOnClickListener {
                dialog.dismiss()
            }

            val fileNameTv = sheetView.findViewById<TextView>(R.id.sheetFileName)
            fileNameTv?.text = file.name

            dialog.setContentView(sheetView)
            dialog.show()
        }

        private fun openFileDirectlyStatic(context: Context, file: File) {
            val ext = file.extension.lowercase(Locale.getDefault())
            if (ext in TEXT_PREVIEW_EXTS) {
                // bounded 后主线程读上限 50k 字符 (≈200KB UTF-8) 通常 <10ms，避免旧
                // `readText()` 全文加载导致 100MB `.log` OOM/ANR (#92 review B5)。
                val content = TextPreviewLoader.readBounded(file, TEXT_PREVIEW_MAX_CHARS)
                val intent = Intent(context, TextPreviewActivity::class.java)
                intent.putExtra("title", file.name)
                intent.putExtra("content", content)
                intent.putExtra("filePath", file.absolutePath)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, getMimeType(file.name))
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
