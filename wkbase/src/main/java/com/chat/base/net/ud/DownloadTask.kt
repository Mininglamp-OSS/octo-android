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