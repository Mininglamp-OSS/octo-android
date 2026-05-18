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

package com.octoim.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.text.TextUtils
import android.widget.Toast
import com.chat.base.config.WKConfig
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.msgcontent.WKFileContent
import com.chat.uikit.message.MsgModel
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.msgmodel.WKImageContent
import com.xinbida.wukongim.msgmodel.WKMessageContent
import com.xinbida.wukongim.msgmodel.WKTextContent
import com.xinbida.wukongim.msgmodel.WKVideoContent
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class ShareReceiveActivity : Activity() {

    private var chooseChatStarted = false

    companion object {
        private val URL_WITH_SCHEME: Pattern = Pattern.compile("https?://\\S+")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (TextUtils.isEmpty(WKConfig.getInstance().token)) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val messageContents = parseShareIntent()
        if (messageContents.isEmpty()) {
            Toast.makeText(this, R.string.share_content_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ChooseChatActivity 内部已处理确认弹窗，回调触发时直接发送消息
        val chooseChatMenu = ChooseChatMenu(ChatChooseContacts { channelList ->
            if (channelList.isNullOrEmpty()) {
                finish()
                return@ChatChooseContacts
            }
            val spaceId = MsgModel.getInstance().getCurrentSpaceId()
            for (channel in channelList) {
                for (content in messageContents) {
                    // DM 消息注入 spaceId
                    if (!TextUtils.isEmpty(spaceId) && channel.channelType == WKChannelType.PERSONAL) {
                        content.spaceId = spaceId
                    }
                    val options = WKSendOptions()
                    options.setting.receipt = channel.receipt
                    WKIM.getInstance().msgManager.sendWithOptions(content, channel, options)
                }
            }
            val toast = Toast.makeText(this@ShareReceiveActivity, R.string.is_forward, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
            finish()
        }, messageContents)

        chooseChatMenu.singleSelect = true
        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView, chooseChatMenu)
        chooseChatStarted = true
    }

    override fun onResume() {
        super.onResume()
        // ChooseChatActivity 被关闭（用户取消）后回到此透明 Activity，直接 finish
        if (chooseChatStarted) {
            finish()
        }
    }

    private fun parseShareIntent(): List<WKMessageContent> {
        val action = intent.action ?: return emptyList()
        val type = intent.type ?: return emptyList()

        return when (action) {
            Intent.ACTION_SEND -> parseSingleShare(type)
            Intent.ACTION_SEND_MULTIPLE -> parseMultipleShare(type)
            else -> emptyList()
        }
    }

    private fun parseSingleShare(type: String): List<WKMessageContent> {
        if (type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val trimmed = text.trim()
                // 要求 URL 带 scheme，避免 Patterns.WEB_URL 对 "hello.co" 等误匹配
                val matcher = URL_WITH_SCHEME.matcher(trimmed)
                if (matcher.find()) {
                    val url = matcher.group()
                    val textAround = (trimmed.substring(0, matcher.start()) +
                            trimmed.substring(matcher.end())).trim()
                    val content = buildLinkMessage(url, textAround)
                    return listOf(WKTextContent(content))
                }
                return listOf(WKTextContent(text))
            }
            return emptyList()
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: return emptyList()
        val content = createContentFromUri(uri, type) ?: return emptyList()
        return listOf(content)
    }

    private fun parseMultipleShare(type: String): List<WKMessageContent> {
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (uris.isNullOrEmpty()) return emptyList()

        return uris.mapNotNull { uri -> createContentFromUri(uri, type) }
    }

    private fun buildLinkMessage(url: String, textTitle: String): String {
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: textTitle
        val parsed = Uri.parse(url)
        val host = parsed.host ?: ""
        val scheme = parsed.scheme ?: "https"
        val icon = "$scheme://$host/favicon.ico"
        val json = JSONObject().apply {
            put("title", title)
            put("url", url)
            put("icon", icon)
        }
        return "[链接]$json"
    }

    private fun createContentFromUri(uri: Uri, mimeType: String): WKMessageContent? {
        val fileName = getFileName(uri)
        val localPath = copyUriToCache(uri, fileName) ?: return null

        return when {
            mimeType.startsWith("image/") -> {
                val img = WKImageContent(localPath)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localPath, opts)
                img.width = opts.outWidth
                img.height = opts.outHeight
                img
            }
            mimeType.startsWith("video/") -> {
                val video = WKVideoContent()
                video.localPath = localPath
                video.size = File(localPath).length()
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(localPath)
                    video.width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0
                    video.height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0
                    video.second = (retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L) / 1000
                    // 提取视频第一帧作为封面
                    val coverBitmap = retriever.getFrameAtTime(0)
                    if (coverBitmap != null) {
                        val coverFile = File(cacheDir, "share/${System.currentTimeMillis()}_cover.jpg")
                        FileOutputStream(coverFile).use { out ->
                            coverBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        video.coverLocalPath = coverFile.absolutePath
                        coverBitmap.recycle()
                    }
                    retriever.release()
                } catch (_: Exception) { }
                video
            }
            else -> {
                val file = WKFileContent()
                file.localPath = localPath
                file.name = fileName
                file.size = File(localPath).length()
                val dotIndex = fileName.lastIndexOf('.')
                file.extension = if (dotIndex >= 0) fileName.substring(dotIndex + 1) else ""
                file
            }
        }
    }

    private fun copyUriToCache(uri: Uri, fileName: String): String? {
        return try {
            val shareDir = File(cacheDir, "share")
            if (!shareDir.exists()) shareDir.mkdirs()
            val destFile = File(shareDir, "${System.currentTimeMillis()}_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        // 1. 从 ContentResolver 查询 DISPLAY_NAME
        if (uri.scheme == "content") {
            try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        // 2. 如果拿到的名字看起来像 hash（微信常见），尝试从 Intent extras 获取原始文件名
        if (!name.isNullOrBlank() && looksLikeHash(name!!)) {
            val ext = name!!.substringAfterLast('.', "")
            val betterName = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: intent.clipData?.description?.label?.toString()
            if (!betterName.isNullOrBlank()) {
                // 确保带上扩展名
                name = if (ext.isNotEmpty() && !betterName.endsWith(".$ext", ignoreCase = true)) {
                    "$betterName.$ext"
                } else {
                    betterName
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "shared_file"
        }
        return name!!
    }

    /** 判断文件名主体部分是否像 hash（32位十六进制 或 hash+时间戳） */
    private fun looksLikeHash(fileName: String): Boolean {
        val baseName = fileName.substringBeforeLast('.')
        // 32位纯hex（MD5）或 hex+时间戳数字（微信常见格式）
        return baseName.matches(Regex("^[0-9a-fA-F]{32,}[_\\-]?\\d*$"))
    }
}
