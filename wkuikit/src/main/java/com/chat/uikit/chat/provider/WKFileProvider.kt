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

        val fileContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKFileContent

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
        return basename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
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

        @JvmStatic
        fun openFileWithOptions(context: Context, file: File) {
            val activity = context as? Activity ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            val dialog = BottomSheetDialog(activity)
            val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_file_actions, null)

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
            if (ext in listOf(
                    "txt", "md", "json", "yaml", "yml", "xml", "csv", "log",
                    "conf", "cfg", "ini", "properties", "toml",
                    "html", "htm", "css", "js", "ts", "py", "go", "java", "kt",
                    "sh", "bat", "sql", "gradle", "swift", "c", "cpp", "h",
                    "rb", "php", "rs", "lua", "r", "pl", "env", "gitignore"
                )) {
                try {
                    val content = file.readText(Charsets.UTF_8).let {
                        if (it.length > 50000) it.substring(0, 50000) + "\n\n... (文件过大，仅显示前50000字符)" else it
                    }
                    val intent = Intent(context, TextPreviewActivity::class.java)
                    intent.putExtra("title", file.name)
                    intent.putExtra("content", content)
                    intent.putExtra("filePath", file.absolutePath)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    WKToastUtils.getInstance().showToastNormal(context.getString(R.string.str_file_not_exist))
                }
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
