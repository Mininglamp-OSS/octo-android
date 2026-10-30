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

import java.util.concurrent.ConcurrentHashMap

class WKProgressManager private constructor() {

    /** Session-scoped key used only by file attachment uploads. */
    data class FileUploadProgressKey(val uid: String, val sessionVersion: Long, val clientMsgNo: String, val clientSeq: Long)

    companion object {
        val instance: WKProgressManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            WKProgressManager()
        }
    }

    private val progressList = ConcurrentHashMap<Any, IProgress>()
    private data class FileUploadTask(@Volatile var progress: Int)
    private val fileUploadTasks = ConcurrentHashMap<FileUploadProgressKey, FileUploadTask>()

    data class FileUploadTaskSnapshot(val progress: Int)

    interface IProgress {
        fun onProgress(tag: Any?, progress: Int)
        fun onSuccess(tag: Any?, path: String?)
        fun onFail(tag: Any?, msg: String?)
    }

    fun registerProgress(tag: Any, progress: IProgress) {
        progressList[tag] = progress
        if (tag is FileUploadProgressKey) {
            fileUploadTasks[tag]?.let { progress.onProgress(tag, it.progress) }
        }
    }

    fun unregisterProgress(tag: Any) {
        progressList.remove(tag)
    }

    fun unregisterProgress(tag: Any, progress: IProgress) { progressList.remove(tag, progress) }

    /** Starts a file upload independently of whether a message row currently observes it. */
    @Synchronized
    fun beginFileUpload(key: FileUploadProgressKey): Boolean {
        if (fileUploadTasks.containsKey(key)) return false
        fileUploadTasks[key] = FileUploadTask(0)
        progressList[key]?.onProgress(key, 0)
        return true
    }

    fun getFileUploadTask(key: FileUploadProgressKey): FileUploadTaskSnapshot? =
        fileUploadTasks[key]?.let { FileUploadTaskSnapshot(it.progress) }

    @Synchronized
    fun finishFileUpload(key: FileUploadProgressKey) {
        fileUploadTasks.remove(key)
    }

    /** Completes an attachment upload after its SDK callback has persisted the message content. */
    fun completeUploadSuccess(tag: Any, filePath: String?) {
        if (tag is FileUploadProgressKey) finishFileUpload(tag)
        progressList.remove(tag)?.onSuccess(tag, filePath)
    }

    /** Completes an attachment upload after its SDK callback has persisted send failure. */
    fun completeUploadFailure(tag: Any, message: String?) {
        if (tag is FileUploadProgressKey) finishFileUpload(tag)
        progressList.remove(tag)?.onFail(tag, message)
    }

    internal fun onSuccess(tag: Any, filePath: String) {
        progressList.remove(tag)?.onSuccess(tag, filePath)
    }


    internal fun onFail(tag: Any, msg: String) {
        progressList.remove(tag)?.onFail(tag, msg)
    }

    internal fun seekProgress(tag: Any?, progress: Int) {
        if (tag is FileUploadProgressKey) {
            val task = fileUploadTasks[tag] ?: return
            task.progress = progress.coerceIn(0, 100)
        }
        progressList[tag]?.onProgress(tag, progress)
    }
}
