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

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chat.base.net.CommonRequestParamInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class Downloader private constructor() {
    private val mDownloadTasks: ConcurrentHashMap<String, Task> by lazy {
        ConcurrentHashMap<String, Task>()
    }
    private val mOnDownloadHashMap: ConcurrentHashMap<String, Triple<OnDownload?, OnComplete?, OnFail?>> by lazy {
        ConcurrentHashMap<String, Triple<OnDownload?, OnComplete?, OnFail?>>()
    }
    private val mCalls: ConcurrentHashMap<String, Call> by lazy {
        ConcurrentHashMap<String, Call>()
    }
    private val mOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(TIME_OUT, TimeUnit.SECONDS)
            .addInterceptor(CommonRequestParamInterceptor())
            .build()
    }

//    private val mHandler: Handler = Handler(Looper.myLooper()!!)

    companion object {
        private const val TAG: String = "Downloader"
        private const val TIME_OUT: Long = 5 * 60

        val instance: Downloader by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            Downloader()
        }
    }

    // pauseDownload / resumeDownload 已删除：全仓库无调用方，且实现与"断点续传"语义不符。
    // pauseDownload 置 PAUSED 后立刻 call.cancel()，cancel 会让 calculate() 里阻塞的
    // read() 抛异常，控制流进 catch（清 .tmp + 置 ERROR + 移除任务）而不是 PAUSED 的
    // 干净 break —— 只有恰好停在循环顶部才走得到 break。随后 resumeDownload 连任务都
    // 找不到。加上 RESUME 分支对无 Range 头的全新响应做 inputStream.skip(hasDownloadSize)
    // 本身也不成立。保留一个不能用的 API 比没有更糟，需要时按真实 Range 续传重新实现。

    private fun String.validateMusicType(): Boolean {
        return this.validateType(".mp3")
                || this.validateType(".wav")
                || this.validateType(".aac")
                || this.validateType(".flac")
                || this.validateType(".ape")
    }

    private fun String.validateVideoType(): Boolean {

        return this.validateType(".mp4")
                || this.validateType(".3gp")
                || this.validateType(".avi")
                || this.validateType(".mkv")
                || this.validateType(".wmv")
                || this.validateType(".mpg")
                || this.validateType(".vob")
                || this.validateType(".flv")
                || this.validateType(".swf")
                || this.validateType(".mov")
                || this.validateType(",rmvb")
                || this.validateType(".mpeg4")
    }

    private fun String.validateType(fileType: String): Boolean {
        return this.endsWith(fileType, true)
    }

    private fun String.validatePicType(): Boolean {
        return this.validateType(".png")
                || this.validateType(".jpg")
                || this.validateType(".jpeg")
                || this.validateType(".gif")
                || this.validateType(".webp")

    }

    private fun String.getFileType(): String {
        return this.substring(this.lastIndexOf("."), this.lastIndex + 1)
    }

    fun multiDownload(
        taskUrls: MutableList<String>, filenames: MutableList<String>,
        onDownload: OnDownload? = null,
        onComplete: OnComplete? = null,
        onFail: OnFail? = null
    ) {
        if (taskUrls.isEmpty() || filenames.isEmpty()) {
            Log.e(TAG, "taskUrls or filenames can not be null or empty")
            return
        }

        if (taskUrls.size != filenames.size) {
            Log.e(TAG, "the size of taskUrls and filenames must be the same")
            return
        }
        taskUrls.forEachIndexed { index, taskUrl ->
            validateNeedCallback(taskUrl, onDownload, onComplete, onFail)
            download(taskUrl, filenames[index], onDownload)
        }


    }

    private fun validateNeedCallback(
        taskUrl: String,
        onDownload: OnDownload? = null,
        onComplete: OnComplete? = null,
        onFail: OnFail? = null
    ) {
        if (onDownload == null && onComplete == null) {

        } else {
            val callback = Triple(onDownload, onComplete, onFail)
            mOnDownloadHashMap[taskUrl] = callback
        }

    }

    fun download(
        taskUrl: String,
        savePath: String,
        onDownload: OnDownload? = null,
        onComplete: OnComplete? = null,
        onFail: OnFail? = null
    ) {
        validateNeedCallback(taskUrl, onDownload, onComplete, onFail)
        val request = Request.Builder()
            .url(taskUrl)
            .build()
//        val type: String = when {
//            taskUrl.validateMusicType() -> Environment.DIRECTORY_MUSIC
//            taskUrl.validateVideoType() -> Environment.DIRECTORY_MOVIES
//            taskUrl.validatePicType() -> Environment.DIRECTORY_PICTURES
//            else -> {
//                Environment.DIRECTORY_DOWNLOADS
//            }
//        }
//        val dir = Environment.getExternalStoragePublicDirectory(type)
        val mFile = File(savePath)
        val task = Task(url = taskUrl, request = request, file = mFile)
        // 同一 URL 已在下载中且落盘路径相同，则直接复用（参照 iOS
        // WKMergeForwardDetailCell 的 downloadingMessages 守卫）。否则双击文件行会起两个
        // 任务写同一个 .tmp，两个 FileOutputStream 交错写入产生错乱文件；回调也会互相顶掉。
        // 上面的 validateNeedCallback 已把本次回调登记为该 URL 的最新回调，
        // 所以进行中的那个任务完成时，点击者依然能收到进度/完成/失败。
        //
        // 落盘路径不同时（同一 URL 被普通聊天与合并转发按不同键缓存）不能复用：
        // 复用会让后一个调用方拿到前者的路径。此时放行，各写各自的 .tmp，
        // 代价是重复下载一次，但结果正确。
        val running = mDownloadTasks.putIfAbsent(taskUrl, task)
        if (running != null) {
            if (running.file.absolutePath == mFile.absolutePath) {
                Log.d(TAG, "$taskUrl is already downloading, reuse in-flight task")
                return
            }
            mDownloadTasks[taskUrl] = task
        }
        realDownload(task)

    }

    private fun realDownload(mTask: Task) {
        mOkHttpClient
            .newCall(mTask.request).apply {
                mTask.call = this
            }.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // 清理残留的半截 .tmp 文件，并通知上层失败（否则 UI 进度条永远卡住）
                    dispatchFail(mTask, e.message ?: "unKnow error")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful && response.code == 200) {
                        val body = response.body
                        if (body == null) {
                            dispatchFail(mTask, response.message.ifEmpty { "empty body" })
                            return
                        }
                        mTask.contentSize = body.contentLength()
                        mTask.inputStream = body.byteStream()
                        if (mTask.fileOutputStream == null) {
                            // 先写 .tmp 临时文件，完成后原子 rename 到目标路径，避免中断残留半截文件
                            mTask.fileOutputStream = FileOutputStream(tmpFileFor(mTask.file))
                        }
                        calculate(mTask)
                    } else {
                        // HTTP 4xx/5xx：过期文件 URL 的 403/404/410 是最常见的真实失败路径，
                        // 此前只打日志直接 return，上层 onFail 永不执行、进度条永久卡住。
                        Log.e(TAG, response.message)
                        val msg = response.message.ifEmpty { "http " + response.code }
                        // 失败分支才关 response：成功分支的 body 流正由 calculate 持续读取，
                        // 在这里关会把它读到一半掐断。
                        response.close()
                        dispatchFail(mTask, msg)
                    }
                }

            })
    }

    /** 目标文件对应的临时文件路径（下载期间写入，完成后 rename）。 */
    private fun tmpFileFor(file: File): File = File(file.absolutePath + ".tmp")

    /**
     * 统一的失败收尾：清残留 .tmp、置 ERROR、主线程回调 onFail、注销回调与任务。
     *
     * 每条失败路径都必须走这里，否则上层的 onFail（隐藏进度条 + toast）不会执行，
     * 进度条会永久卡住 —— HTTP 4xx/5xx（过期 URL 的 403/404/410 是最常见的真实失败）
     * 此前就是直接 return，既不清理也不回调。
     */
    private fun dispatchFail(mTask: Task, msg: String) {
        cleanupTmpFile(mTask)
        mTask.status = DownloadStatus.ERROR
        mTask.errorMsg = msg
        val callback = mOnDownloadHashMap[mTask.url]
        Handler(Looper.getMainLooper()).post {
            callback?.third?.invoke(mTask.url, msg)
            unregisterCallback(mTask.url, callback)
        }
        mDownloadTasks.remove(mTask.url)
    }

    /**
     * 按身份注销回调：只有当表里仍是本任务注册的那个回调时才移除。
     *
     * 移除动作被 post 到主线程，执行时机不确定；若此间同 URL 的第二次 download() 已经
     * 通过 validateNeedCallback 注册了新回调，无条件 remove 会把新注册删掉，导致新任务
     * 的 calculate() 拿到 null callback —— 无进度、无完成、无失败，进度条再次永久卡住。
     */
    private fun unregisterCallback(
        url: String,
        callback: Triple<OnDownload?, OnComplete?, OnFail?>?
    ) {
        if (callback == null) {
            mOnDownloadHashMap.remove(url)
            return
        }
        // ConcurrentHashMap.remove(key, value)：原子条件删除，与新注册的 put 不会竞态
        mOnDownloadHashMap.remove(url, callback)
    }

    /** 删除残留的 .tmp 文件（下载失败/异常时调用）。 */
    private fun cleanupTmpFile(mTask: Task) {
        try {
            mTask.fileOutputStream?.close()
        } catch (_: Exception) {
        }
        mTask.fileOutputStream = null
        try {
            tmpFileFor(mTask.file).delete()
        } catch (_: Exception) {
        }
    }

    private fun calculate(mTask: Task) {
        val bytes = ByteArray(1024 * 4)
        val callback = mOnDownloadHashMap[mTask.url]
//        try {
//            val total = mTask.contentSize
//            var sum: Long = 0
//
//            val buffer = ByteArray(1024 * 2)
//            var len: Int
//            while (mTask.inputStream!!.read(buffer).also { len = it } != -1) {
//                mTask.fileOutputStream!!.write(buffer, 0, len)
//                sum += len.toLong()
//                val progress = (sum * 1.0f / total * 100).toInt()
//                // 下载中
//                mHandler.post {
//                    callback?.first?.invoke(
//                        mTask.url,
//                        progress
//                    )
//
//                }
//            }
//            mTask.fileOutputStream!!.flush()
//            mHandler.post {
//                callback?.second?.invoke(mTask.url, mTask.file)
//            }
//        } catch (e: IOException) {
//            mHandler.post {
//                callback?.third?.invoke(mTask.url, "")
//            }
//        } finally {
//            mTask.inputStream!!.close()
//            mTask.fileOutputStream!!.close()
//        }

        try {
            while (true) {
                // 注：PAUSED / RESUME 两个分支目前不可达（pauseDownload / resumeDownload
                // 已删除，见上方说明）。暂留分支与 DownloadStatus 枚举，等真正按 Range
                // 实现续传时复用；状态机本身不影响正常下载路径。
                if (mTask.status == DownloadStatus.PAUSED) {
                    break
                }
                if (mTask.status == DownloadStatus.RESUME) {
                    mTask.inputStream!!.skip(mTask.hasDownloadSize)
                    mTask.status = DownloadStatus.DOWNLOADING
                }
                val len = mTask.inputStream!!.read(bytes)
                if (len == -1) {
                    // 下载完成：关闭流后把 .tmp 原子 rename 到目标路径，再回调最终文件
                    try {
                        mTask.fileOutputStream!!.flush()
                    } catch (_: Exception) {
                    }
                    try {
                        mTask.fileOutputStream!!.close()
                    } catch (_: Exception) {
                    }
                    mTask.fileOutputStream = null
                    val finalFile = mTask.file
                    val tmpFile = tmpFileFor(finalFile)
                    if (!tmpFile.renameTo(finalFile)) {
                        // rename 失败（如跨挂载点）时退化为复制覆盖
                        tmpFile.copyTo(finalFile, overwrite = true)
                        tmpFile.delete()
                    }
                    Handler(Looper.getMainLooper()).post {
                        callback?.second?.invoke(mTask.url, finalFile)
                        // 按身份注销：避免删掉同 URL 第二次下载刚注册的新回调
                        unregisterCallback(mTask.url, callback)
                    }
                    mDownloadTasks.remove(mTask.url)
                    break
                }
                mTask.fileOutputStream!!.write(bytes, 0, len)
                mTask.hasDownloadSize += len
                Handler(Looper.getMainLooper()).post {
                    callback?.first?.invoke(
                        mTask.url,
                        ((mTask.hasDownloadSize * 1f / mTask.contentSize) * 100).toInt()
                    )

                }
            }
        } catch (e: Exception) {
            e.localizedMessage?.let { Log.e(TAG, it) }
            try {
                mTask.inputStream!!.close()
            } catch (_: Exception) {
            }
            // 清理半截 .tmp 文件并通知上层失败，避免残file被后续 exists() 当作有效缓存
            dispatchFail(mTask, e.message ?: "download error")
        }


    }

}