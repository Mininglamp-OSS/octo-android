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
) {
    /**
     * 本任务的回调订阅者。回调归属任务而不是按 URL 存在全局表里，原因有二：
     *
     * 1. 在途守卫命中时（同 URL 同路径的第二次 download()），新调用方的回调在这里
     *    追加；任务完成/失败时逐个通知，第二个点击者才收得到进度与结果——若按 URL
     *    查表，任务只会用自己启动时捕获的那一个回调，后来者永远收不到。
     * 2. 同 URL 不同路径的两个任务并存时，各自通知各自的订阅者，不会把失败回调
     *    打到另一个还在正常下载的任务头上。
     */
    val callbacks =
        java.util.Collections.synchronizedList(ArrayList<Triple<OnDownload?, OnComplete?, OnFail?>>())

    /** 追加一个回调订阅（守卫命中时由 download() 调用）。 */
    fun addCallback(callback: Triple<OnDownload?, OnComplete?, OnFail?>) {
        callbacks.add(callback)
    }
}


internal enum class DownloadStatus {
    START, DOWNLOADING, ERROR, PAUSED, RESUME
}


typealias OnDownload = (String, Int) -> Unit
typealias OnFail = (String, String) -> Unit
typealias OnComplete = (String, file: File) -> Unit