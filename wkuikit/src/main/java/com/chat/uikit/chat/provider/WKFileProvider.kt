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
import com.chat.base.config.WKConfig
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
import com.xinbida.wukongim.WKIMApplication
import com.xinbida.wukongim.db.WKDBHelper
import com.xinbida.wukongim.message.type.WKSendMsgResult
import java.io.File
import java.util.Locale

internal object FileDownloadSessionGuard {
    fun isCurrent(captured: WKConfig.SessionSnapshot, current: WKConfig.SessionSnapshot, appUid: String, helperIsCurrent: Boolean): Boolean =
        captured.version == current.version && captured.uid == current.uid && captured.token == current.token && appUid == captured.uid && helperIsCurrent

    fun persistIfCurrent(captured: WKConfig.SessionSnapshot, current: WKConfig.SessionSnapshot, appUid: String, helperIsCurrent: Boolean, persist: () -> Unit): Boolean {
        if (!isCurrent(captured, current, appUid, helperIsCurrent)) return false
        persist()
        return true
    }
}

internal class UploadProgressRegistration {
    enum class TerminalResult { ignored, success, failed }
    var listener: WKProgressManager.IProgress? = null
    var registered: Boolean = false
    var terminal: Boolean = false
    fun canRegister() = !terminal && !registered && listener != null
    fun complete(tag: Any?, expectedTag: Any, latest: WKFileContent?): TerminalResult {
        if (tag != expectedTag || terminal) return TerminalResult.ignored
        finish()
        return if (!latest?.url.isNullOrBlank()) TerminalResult.success else TerminalResult.failed
    }
    fun finish() { terminal = true; registered = false; listener = null }
}

class WKFileProvider : WKChatBaseProvider() {
    private class BindingState(var generation: Long, val clientSeq: Long, val identity: String, val session: WKConfig.SessionSnapshot, val dbHelper: WKDBHelper?, @Volatile var attached: Boolean = true, val uploadRegistration: UploadProgressRegistration = UploadProgressRegistration(), @Volatile var request: Long = 0, var terminalContent: WKFileContent? = null, var terminalFailed: Boolean = false, var attachListener: View.OnAttachStateChangeListener? = null)
    private fun isSessionCurrent(session: WKConfig.SessionSnapshot, helper: com.xinbida.wukongim.db.WKDBHelper? = null): Boolean {
        val current = WKConfig.getInstance().getSessionSnapshot()
        val app = WKIMApplication.getInstance()
        return FileDownloadSessionGuard.isCurrent(session, current, app.uid, helper == null || app.dbHelper === helper)
    }
    private fun isCurrent(view: View, binding: BindingState, request: Long = binding.request): Boolean {
        val current = view.findViewById<LinearLayout>(R.id.fileView).tag as? BindingState ?: return false
        val session = WKConfig.getInstance().getSessionSnapshot()
        return current === binding && current.identity == binding.identity && binding.attached && binding.request == request && binding.dbHelper != null && binding.session.version == session.version && binding.session.uid == session.uid && binding.session.token == session.token && isSessionCurrent(binding.session, binding.dbHelper)
    }

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
        fileView.layoutParams.width = minOf(getViewWidth(from, uiChatMsgItemEntity), (280 * context.resources.displayMetrics.density).toInt())

        val fileNameTv = parentView.findViewById<TextView>(R.id.fileNameTv)
        val fileSizeTv = parentView.findViewById<TextView>(R.id.fileSizeTv)
        val fileIconIv = parentView.findViewById<ImageView>(R.id.fileIconIv)
        val progressBar = parentView.findViewById<ProgressBar>(R.id.fileProgressBar)
        val extensionTv = parentView.findViewById<TextView>(R.id.fileExtensionTv)
        val actionButton = parentView.findViewById<View>(R.id.fileActionButton)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        val uploadLayout = parentView.findViewById<View>(R.id.fileUploadProgressLayout)
        val uploadBar = parentView.findViewById<ProgressBar>(R.id.fileUploadProgressBar)
        val uploadPercent = parentView.findViewById<TextView>(R.id.fileUploadProgressPercent)
        val previous = fileView.tag as? BindingState
        previous?.let { old -> synchronized(old) { old.uploadRegistration.listener?.let { WKProgressManager.instance.unregisterProgress(old.clientSeq, it) }; old.uploadRegistration.registered = false; old.uploadRegistration.listener = null } }
        previous?.attachListener?.let { fileView.removeOnAttachStateChangeListener(it) }
        val generation = (previous?.generation ?: 0) + 1
        val identity = "${uiChatMsgItemEntity.wkMsg.channelType}:${uiChatMsgItemEntity.wkMsg.channelID}:${uiChatMsgItemEntity.wkMsg.clientMsgNO}"
        val binding = BindingState(generation, uiChatMsgItemEntity.wkMsg.clientSeq, identity, WKConfig.getInstance().getSessionSnapshot(), WKIMApplication.getInstance().dbHelper)
        fileView.tag = binding
        binding.attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                synchronized(binding) {
                    binding.attached = true
                    binding.request++
                    if (binding.dbHelper != null && isSessionCurrent(binding.session, binding.dbHelper) && binding.uploadRegistration.canRegister()) {
                        binding.uploadRegistration.listener?.let { WKProgressManager.instance.registerProgress(binding.clientSeq, it); binding.uploadRegistration.registered = true }
                    } else if (binding.dbHelper == null || !isSessionCurrent(binding.session, binding.dbHelper)) {
                        binding.uploadRegistration.listener?.let { WKProgressManager.instance.unregisterProgress(binding.clientSeq, it) }
                        binding.uploadRegistration.registered = false
                    }
                }
                if (binding.dbHelper != null && isSessionCurrent(binding.session, binding.dbHelper)) {
                    recoverUploadTerminal(binding, uiChatMsgItemEntity, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton)
                } else {
                    contentLayout.isEnabled = false; actionButton.isEnabled = false
                }
            }
            override fun onViewDetachedFromWindow(v: View) { synchronized(binding) { binding.attached = false; binding.request++; binding.uploadRegistration.listener?.let { WKProgressManager.instance.unregisterProgress(binding.clientSeq, it); binding.uploadRegistration.registered = false } } }
        }
        fileView.addOnAttachStateChangeListener(binding.attachListener)
        progressBar.visibility = View.GONE
        progressBar.progress = 0
        uploadLayout.visibility = View.GONE; uploadBar.progress = 0; uploadPercent.text = ""
        extensionTv.visibility = View.VISIBLE; fileSizeTv.visibility = View.VISIBLE
        contentLayout.isEnabled = true; actionButton.isEnabled = true
        extensionTv.text = ""
        contentLayout.setOnClickListener(null)
        actionButton.setOnClickListener(null)

        val fileContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKFileContent
        if (fileContent == null) {
            fileNameTv.text = uiChatMsgItemEntity.wkMsg.baseContentMsgModel?.displayContent
                ?: context.getString(R.string.base_unknow_msg)
            fileSizeTv.text = ""
            fileIconIv.setImageResource(R.drawable.ic_file_document)
            return
        }

        fileNameTv.text = fileContent.name ?: ""
        fileSizeTv.text = formatFileSize(fileContent.size)
        extensionTv.text = (fileContent.name?.substringAfterLast('.', "")?.ifEmpty { fileContent.extension } ?: fileContent.extension).uppercase(Locale.ROOT)

        setFileIcon(fileIconIv, fileContent.extension, fileContent.name)
        resetCellBackground(parentView, uiChatMsgItemEntity, from)

        // Upload progress for sending
        if (TextUtils.isEmpty(fileContent.url)) {
            val alreadyFailed = uiChatMsgItemEntity.wkMsg.status == WKSendMsgResult.send_fail
            uploadLayout.visibility = if (alreadyFailed) View.GONE else View.VISIBLE; uploadBar.progress = 0; uploadPercent.text = context.getString(R.string.doc_share_upload_percent, 0); extensionTv.visibility = View.GONE; fileSizeTv.visibility = if (alreadyFailed) View.VISIBLE else View.GONE; contentLayout.isEnabled = false; actionButton.isEnabled = false
            if (alreadyFailed) {
                fileSizeTv.text = context.getString(R.string.doc_share_upload_failed)
            } else {
            val uploadListener = object : WKProgressManager.IProgress {
                    override fun onProgress(tag: Any?, progress: Int) {
                        if (tag is Long && tag == uiChatMsgItemEntity.wkMsg.clientSeq) {
                            val callbackRequest = binding.request
                            parentView.post {
                                if (!isCurrent(parentView, binding, callbackRequest)) return@post
                                if (synchronized(binding) { binding.uploadRegistration.terminal }) return@post
                                uploadLayout.visibility = View.VISIBLE; uploadBar.progress = progress.coerceIn(0, 100); uploadPercent.text = context.getString(R.string.doc_share_upload_percent, progress.coerceIn(0, 100)); extensionTv.visibility = View.GONE; fileSizeTv.visibility = View.GONE; contentLayout.isEnabled = false; actionButton.isEnabled = false
                                progressBar.visibility = View.GONE
                            }
                        }
                    }

                    override fun onSuccess(tag: Any?, path: String?) {
                        if (tag != binding.clientSeq) return
                        val latest = if (binding.dbHelper != null && isSessionCurrent(binding.session, binding.dbHelper)) WKIM.getInstance().msgManager.getWithClientMsgNO(uiChatMsgItemEntity.wkMsg.clientMsgNO) else null
                        val latestContent = latest?.baseContentMsgModel as? WKFileContent
                        val terminal = finishUploadListener(binding, this, tag, latestContent)
                        if (terminal == UploadProgressRegistration.TerminalResult.ignored) return
                        val terminalContent = if (terminal == UploadProgressRegistration.TerminalResult.success) latestContent ?: fileContent else fileContent
                        val terminalFailed = terminal != UploadProgressRegistration.TerminalResult.success
                        synchronized(binding) { binding.terminalContent = terminalContent; binding.terminalFailed = terminalFailed }
                        if (binding.dbHelper == null || !isSessionCurrent(binding.session, binding.dbHelper)) return
                        val callbackRequest = binding.request
                        parentView.post {
                            if (!isCurrent(parentView, binding, callbackRequest) || !isSessionCurrent(binding.session)) return@post
                            progressBar.visibility = View.GONE
                            renderUploadTerminal(binding, uiChatMsgItemEntity, terminalContent, terminalFailed, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton)
                        }
                    }

                    override fun onFail(tag: Any?, msg: String?) {
                        if (finishUploadListener(binding, this, tag, null) == UploadProgressRegistration.TerminalResult.ignored) return
                        synchronized(binding) { binding.terminalContent = fileContent; binding.terminalFailed = true }
                        val callbackRequest = binding.request
                        parentView.post { if (isCurrent(parentView, binding, callbackRequest)) { progressBar.visibility = View.GONE; renderUploadTerminal(binding, uiChatMsgItemEntity, fileContent, true, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton) } }
                    }
            }
            synchronized(binding) {
                binding.uploadRegistration.listener = uploadListener
                if (binding.dbHelper != null && isSessionCurrent(binding.session, binding.dbHelper)) {
                    WKProgressManager.instance.registerProgress(uiChatMsgItemEntity.wkMsg.clientSeq, uploadListener)
                    binding.uploadRegistration.registered = true
                }
            }
            recoverUploadTerminal(binding, uiChatMsgItemEntity, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton)
            }
        }

        // Click to download/open file
        contentLayout.setOnClickListener { (uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKFileContent)?.let { handleFileClick(parentView, uiChatMsgItemEntity, it, progressBar, binding) } }
        actionButton.setOnClickListener { (uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKFileContent)?.let { handleFileClick(parentView, uiChatMsgItemEntity, it, progressBar, binding) } }

        addLongClick(contentLayout, uiChatMsgItemEntity)
    }

    private fun recoverUploadTerminal(
        binding: BindingState,
        item: WKUIChatMsgItemEntity,
        fileNameTv: TextView,
        fileSizeTv: TextView,
        extensionTv: TextView,
        uploadLayout: View,
        contentLayout: BubbleLayout,
        actionButton: View
    ) {
        if (binding.dbHelper == null || !isSessionCurrent(binding.session, binding.dbHelper)) return
        val latest = WKIM.getInstance().msgManager.getWithClientMsgNO(item.wkMsg.clientMsgNO)
        if (latest == null || latest.clientMsgNO != item.wkMsg.clientMsgNO || latest.channelID != item.wkMsg.channelID || latest.channelType != item.wkMsg.channelType) return
        val latestContent = latest.baseContentMsgModel as? WKFileContent ?: return
        val succeeded = !latestContent.url.isNullOrBlank()
        val failed = !succeeded && latest.status == WKSendMsgResult.send_fail
        if (!succeeded && !failed) {
            val remembered = synchronized(binding) { binding.terminalContent to binding.terminalFailed }
            if (remembered.first != null) {
                renderUploadTerminal(binding, item, remembered.first!!, remembered.second, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton)
            }
            return
        }
        val listener = synchronized(binding) { binding.uploadRegistration.listener }
        if (listener != null) finishUploadListener(binding, listener, binding.clientSeq, latestContent)
        synchronized(binding) { binding.terminalContent = latestContent; binding.terminalFailed = failed }
        renderUploadTerminal(binding, item, latestContent, failed, fileNameTv, fileSizeTv, extensionTv, uploadLayout, contentLayout, actionButton)
    }

    private fun renderUploadTerminal(
        binding: BindingState,
        item: WKUIChatMsgItemEntity,
        content: WKFileContent,
        failed: Boolean,
        fileNameTv: TextView,
        fileSizeTv: TextView,
        extensionTv: TextView,
        uploadLayout: View,
        contentLayout: BubbleLayout,
        actionButton: View
    ) {
        if (!failed) item.wkMsg.baseContentMsgModel = content
        fileNameTv.text = content.name ?: ""
        fileSizeTv.text = if (failed) context.getString(R.string.doc_share_upload_failed) else formatFileSize(content.size)
        extensionTv.text = (content.name?.substringAfterLast('.', "")?.ifEmpty { content.extension } ?: content.extension).uppercase(Locale.ROOT)
        fileSizeTv.visibility = View.VISIBLE
        extensionTv.visibility = if (failed) View.GONE else View.VISIBLE
        uploadLayout.visibility = View.GONE
        contentLayout.isEnabled = !failed
        actionButton.isEnabled = !failed
    }

    private fun finishUploadListener(binding: BindingState, listener: WKProgressManager.IProgress, tag: Any?, latest: WKFileContent?): UploadProgressRegistration.TerminalResult {
        synchronized(binding) {
            val result = binding.uploadRegistration.complete(tag, binding.clientSeq, latest)
            if (result != UploadProgressRegistration.TerminalResult.ignored) WKProgressManager.instance.unregisterProgress(binding.clientSeq, listener)
            return result
        }
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
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        fileContent: WKFileContent,
        progressBar: ProgressBar,
        binding: BindingState
    ) {
        // 缓存身份 = (频道类型, 频道ID, clientMsgNO)。身份只在内存传递、不落盘，因此
        // 无需是"安全文件名"；磁盘名由 SHA-256(身份) 派生。
        //
        // 必须走 buildCacheFileNameForIdentity 而不是 buildCacheFileName：后者把键里的
        // `: ? * " < > |` 替换成下划线、删控制字符并截断，该变换不是单射——clientMsgNO
        // 不保证是本地 UUID 形状（WKProto:214 收消息解码与 MsgManager:1250 历史同步都
        // 直接采用服务端下发字符串，存储层只校验非空），`m:a` 与 `m?a` 会塌成同一磁盘名。
        // 进程内校验记录能拦住紧接着的误命中，但重启或 LruCache 驱逐后记录消失，无记录
        // 分支只比 size，B 会打开 A 的字节（B-1 普通聊天分支）。
        val identity = com.chat.base.utils.WKCacheKeys.chatFileIdentity(
            uiChatMsgItemEntity.wkMsg.channelType,
            uiChatMsgItemEntity.wkMsg.channelID,
            uiChatMsgItemEntity.wkMsg.clientMsgNO
        )
        if (!isCurrent(parentView, binding)) return
        val request = ++binding.request

        // Check local file first（size 校验防止半截/过期文件被当作有效缓存）
        //
        // 传 identity 而不省略：localPath 若是本应用下载回写的缓存路径，校验记录就是按
        // 该 identity 记的，传了才能命中 exemption（不传会退化成 file.name，与记录里的
        // identity 不等 → 已下载完的文件反被判无效、每次点击重下）。localPath 若是发送端
        // 原文件路径，该路径上本无校验记录，走无记录分支，传与不传结果一致。
        if (!TextUtils.isEmpty(fileContent.localPath)) {
            val file = File(fileContent.localPath)
            if (file.exists() && isValidCache(file, fileContent, identity)) {
                openFile(file, fileContent.name)
                return
            }
        }

        // Check download directory
        val downloadDir = WKConstants.chatDownloadFileDir +
                uiChatMsgItemEntity.wkMsg.channelType + "/" +
                uiChatMsgItemEntity.wkMsg.channelID + "/"
        WKFileUtils.getInstance().createFileDir(downloadDir)
        val fileName = buildCacheFileNameForIdentity(
            identity,
            fileContent.extension,
            fileContent.name
        )
        val filePath = downloadDir + fileName
        val file = File(filePath)
        if (file.exists() && isValidCache(file, fileContent, identity)) {
            // 新缓存命中：直接打开，刻意不去 stat 旧名路径。已迁移的消息不应为了清理
            // 每次点击都多一次 I/O。清理只在"确实要重新下载"时做（下一段）。
            openFile(file, fileContent.name)
            return
        }

        // 新缓存未命中 —— 此时清掉 merge-base 命名方案留下的同消息旧缓存，回收占盘。
        // 旧名只用于定位删除目标、绝不用于读取内容：旧命名是"按展示名"的，同名不同
        // 内容会互相命中（本 PR 最初要修的 bug），拿旧名去读等于让 B 打开 A 的字节。
        // 只删不读则安全——即使旧名因同名歧义指向另一条消息的文件，后果仅是那条消息
        // 下次重下一次。
        deleteLegacyChatCache(downloadDir, uiChatMsgItemEntity.wkMsg.clientMsgNO, fileContent)

        // Download file
        if (TextUtils.isEmpty(fileContent.url)) {
            WKToastUtils.getInstance()
                .showToastNormal(context.getString(R.string.str_file_not_exist))
            return
        }
        val downloadUrl = WKApiConfig.getShowUrl(fileContent.url)
        val downloadSession = WKConfig.getInstance().getSessionSnapshot()
        if (binding.session.version != downloadSession.version || binding.session.uid != downloadSession.uid || binding.session.token != downloadSession.token) return
        val app = WKIMApplication.getInstance()
        val downloadHelper = app.dbHelper
        val afterHelperSession = WKConfig.getInstance().getSessionSnapshot()
        if (downloadHelper == null || afterHelperSession.version != downloadSession.version || afterHelperSession.uid != downloadSession.uid || afterHelperSession.token != downloadSession.token || app.uid != downloadSession.uid || app.dbHelper !== downloadHelper || !isSessionCurrent(downloadSession, downloadHelper)) return
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        WKDownloader.instance.download(downloadUrl, filePath,
            object : WKProgressManager.IProgress {
                override fun onProgress(tag: Any?, progress: Int) {
                    progressBar.post { if (isCurrent(parentView, binding, request)) progressBar.progress = progress }
                }

                override fun onSuccess(tag: Any?, path: String?) {
                    progressBar.post { if (isCurrent(parentView, binding, request)) progressBar.visibility = View.GONE }
                    val downloadedFile = File(filePath)
                    if (downloadedFile.exists()) {
                        // Persistence belongs to the captured account, so detaching the recycled
                        // row does not cancel it. Keep account checks independent of View state.
                        val app = WKIMApplication.getInstance()
                        val helperAtCompletion = app.dbHelper
                        val appUidAtCompletion = app.uid
                        val resultSession = WKConfig.getInstance().getSessionSnapshot()
                        var merged = false
                        // The callback snapshot and captured helper must still identify this account
                        // immediately before the account-local write.
                        val persisted = FileDownloadSessionGuard.persistIfCurrent(
                            downloadSession, resultSession, appUidAtCompletion, helperAtCompletion === downloadHelper
                        ) {
                            // This block has no View checks: a detached but still valid account
                            // may complete its own database update.
                            markDownloadVerified(downloadedFile, identity)
                            fileContent.localPath = filePath
                            merged = WKIM.getInstance().msgManager.updateContentLocalPath(
                                uiChatMsgItemEntity.wkMsg.clientMsgNO,
                                filePath,
                                downloadHelper
                            )
                        }
                        if (!persisted) return
                        // 回写 localPath 并持久化，下次点击直接命中本地文件。
                        // 必须走 updateContentLocalPath（merge 单键），不能用
                        // updateContentAndRefresh(model)——后者按 encodeMsg() 整列覆盖
                        // content，会抹掉顶层的 space_id / mention / reply / entities /
                        // robot_id（WKFileContent.encodeMsg 只输出 5 个字段），导致
                        // Space 隔离与 @ 提及状态静默失效。
                        if (!merged) {
                            // false = DB 里没有该 clientMsgNO 的行（如嵌套转发消息）或
                            // content JSON 已损坏——本次内存态 localPath 仍生效，仅缺持久化。
                            Log.w(TAG, "updateContentLocalPath merged nothing, clientMsgNO=${uiChatMsgItemEntity.wkMsg.clientMsgNO}")
                        }
                        progressBar.post { if (isCurrent(parentView, binding, request) && isSessionCurrent(downloadSession, downloadHelper)) openFile(downloadedFile, fileContent.name) }
                    }
                }

                override fun onFail(tag: Any?, msg: String?) {
                    progressBar.post {
                        if (!isCurrent(parentView, binding, request)) return@post
                        progressBar.visibility = View.GONE
                        WKToastUtils.getInstance().showToastNormal(context.getString(R.string.str_file_download_fail))
                    }
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
         *    身份信息可比，安全性完全依赖**调用方维持的不变量**：所有受管理的缓存路径
         *    都必须由 [buildCacheFileNameForIdentity] 派生（SHA-256，不同身份必不同路径），
         *    于是"同一路径"已蕴含"同一身份"，size 比较只负责排除半截文件、不再承担区分
         *    身份的职责。若将来有调用点用 [buildCacheFileName] 这类有损派生拼受管理路径，
         *    本分支会重新变成错配面——那正是 B-1 两次复发的位置。
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
        @Deprecated(
            "有损派生：键里的 : ? * \" < > | 会被替换成下划线、控制字符被删、结果被截断，" +
                "不同键可塌成同一磁盘名（B-1 两次复发的根因）。受管理的缓存路径一律走 " +
                "buildCacheFileNameForIdentity。",
            ReplaceWith("buildCacheFileNameForIdentity(identity, extension, displayName)")
        )
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

        /**
         * merge-base（80e7e22e）普通聊天缓存的旧文件名，**仅用于定位删除目标**。
         *
         * 逐字复刻 merge-base 的算法，不要"顺手修正"：
         *   rawName = fileContent.name ?: (clientMsgNO + "." + extension)   ← 仅 null 触发回退，
         *             空字符串走 name 分支（merge-base 用的是 ?: 而非 isEmpty 判断）
         *   basename = File(rawName).name
         *   cleaned  = basename.replace([\\/:*?"<>|], "_")
         *   结果     = truncateToByteLength(cleaned, MAX_FILENAME_BYTES)
         *
         * 任何偏差都会算出不存在的路径 → 漏删（无害但无效）。注意 merge-base 的
         * truncateToByteLength 不压缩超长扩展名（那是本 PR 新增的），而当前文件里的
         * 同名私有函数**已经**带 MAX_EXT_BYTES 压缩。两者在 name ≤ 200 字节时结果
         * 一致（直接 return），超长时不一致——超长旧名漏删，可接受，不要为此复刻
         * 两份截断实现。
         */
        private fun legacyChatCacheName(clientMsgNO: String?, fileContent: WKFileContent): String {
            val rawName = fileContent.name
                ?: ((clientMsgNO ?: "") + "." + (fileContent.extension ?: ""))
            val cleaned = File(rawName).name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            return truncateToByteLength(cleaned, MAX_FILENAME_BYTES)
        }

        /**
         * 删除 merge-base 命名方案留下的同消息旧缓存（点击驱动、精确单点删除）。
         *
         * 刻意不做目录遍历 + 模式匹配：那条路要判断"哪些文件是旧名形状"，判据只能是
         * 否定式（basename 不是 64 位 hex），会误删将来任何非身份派生的命名——正是
         * P2-1（按后缀匹配清扫误删合法 .tmp 附件）的同款错误。精确路径删除零误删面。
         *
         * 代价：历史上后缀变化过的残留（同一消息先后落下不同 ext）删不掉，从未被再次
         * 点击的文件也不会被清理。彻底回收需要一次性版本门控清扫，属独立跟进项。
         */
        private fun deleteLegacyChatCache(
            downloadDir: String,
            clientMsgNO: String?,
            fileContent: WKFileContent
        ) {
            try {
                val legacyName = legacyChatCacheName(clientMsgNO, fileContent)
                if (legacyName.isEmpty()) return
                val legacy = File(downloadDir + legacyName)
                // 身份派生名是 64 位 hex，绝不可能等于旧名；这层判断只防将来有人改坏
                // legacyChatCacheName 导致自删新缓存。
                if (legacy.name.matches(Regex("[0-9a-f]{64}\\..*"))) return
                if (legacy.exists() && legacy.isFile) {
                    if (legacy.delete()) {
                        Log.i(TAG, "deleted legacy chat cache: ${legacy.name}")
                    } else {
                        Log.w(TAG, "delete legacy chat cache failed: ${legacy.name}")
                    }
                }
            } catch (e: Exception) {
                // 清理是尽力而为，绝不能影响下载主流程
                Log.w(TAG, "deleteLegacyChatCache error: ${e.message}")
            }
        }

        /**
         * 把任意不可信字符串收敛成单段安全文件名，供兄弟 provider（语音等）复用。
         *
         * 用于**路径构造**，不要用于缓存身份——有损变换、非单射（见
         * [buildCacheFileName] 的弃用说明）。调用方必须自己保证身份唯一性，
         * 或用 [buildCacheFileNameForIdentity]。
         */
        @JvmStatic
        fun safePathSegment(raw: String?, fallback: String = "unknown"): String =
            sanitizePathSegment(raw).ifEmpty { fallback }

        /**
         * SAF 保存建议名：清洗 + 按 UTF-8 字节上限截断。
         *
         * 与 [safePathSegment] 的区别是多一道 [truncateToByteLength]——SAF 的
         * createDocument 对超长名可能静默失败或被系统改写。身份串绝不可走这里。
         */
        @JvmStatic
        @JvmOverloads
        fun boundedSaveName(raw: String?, fallback: String = "file"): String =
            truncateToByteLength(sanitizePathSegment(raw).ifEmpty { fallback }, MAX_FILENAME_BYTES)

        /**
         * 去掉路径分隔符与危险字符，只留下单段安全名（防目录穿越）。
         *
         * pass 顺序是本函数的正确性核心，不要重排：
         *   1. `File(..).name` 取 basename，消除 "a/../b" 这类分隔符结构
         *   2. 删控制字符 —— **必须早于**点序列剥离
         *   3. 替换 Windows 保留字符
         *   4. 剥 ".."，再把纯点残留整体判空
         *
         * 第 2 步与第 4 步的顺序曾经相反，结果是 NUL 可以把 ".." 掩蔽过点序列剥离：
         * `".<NUL>."` 第一道 `.replace("..","")` 匹配不上（中间夹着 NUL），控制字符
         * 随后才被删掉，于是函数吐出 `".."`。同理 `"a<NUL>.<NUL>b"` → `"a..b"`。
         * 两个不同的 clientMsgNO 因此可以塌到同一路径段（语音侧即 B-1 同款误配：
         * 一条语音播另一条的字节）；且该残留离真正的父目录逃逸只差调用方在其后
         * 拼一个分隔符。控制字符先删掉，点序列就无法被掩蔽。
         *
         * 末尾的纯点判空同样必要：`"."` / `".."` / `"...."` 作为路径段都有特殊语义
         * （或退化成隐藏文件），一律交给调用方的 fallback。
         */
        private fun sanitizePathSegment(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            val cleaned = File(raw).name
                // 先删控制字符：否则 NUL 等不可见字符能把下面的 ".." 剥离掩蔽过去
                .replace(Regex("[\\x00-\\x1f\\x7f]"), "")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace("..", "")
                .trim()
            // 只剩点（或空）的残留没有可用语义，交给调用方 fallback
            return if (cleaned.all { it == '.' }) "" else cleaned
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
                intent.putExtra("fileName", boundedSaveName(display, "file"))
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
