package com.chat.base.net.ud

import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal class Task(
    var url: String,
    var hasDownloadSize: Long = 0,
    var inputStream: InputStream? = null,
    var fileOutputStream: FileOutputStream? = null,
    var status: DownloadStatus = DownloadStatus.START,
    var errorMsg: String? = null,
    var call: Call? = null,
    val request: Request,
    val file: File,
    var contentSize: Long = 0
)


internal enum class DownloadStatus {
    START, DOWNLOADING, ERROR, PAUSED, RESUME
}


typealias OnDownload = (String, Int) -> Unit
typealias OnFail = (String, String) -> Unit
typealias OnComplete = (String, file: File) -> Unit