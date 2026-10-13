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
            download(taskUrl, filenames[index], onDownload, onComplete, onFail)
        }


    }

    fun download(
        taskUrl: String,
        savePath: String,
        onDownload: OnDownload? = null,
        onComplete: OnComplete? = null,
        onFail: OnFail? = null
    ) {
        val callback = Triple(onDownload, onComplete, onFail)
        val request = Request.Builder()
            .url(taskUrl)
            .build()
        val mFile = File(savePath)
        // 任务表以 (url + 目标路径) 为键：同一文件 URL 会被不同入口缓存到不同路径
        // （普通聊天按 clientMsgNO、合并转发按消息键、保存视频按时间戳），只按 URL
        // 索引会让两个正交的任务互相驱逐在途状态，重新打开同 .tmp 的并发写入窗口。
        val taskKey = taskKey(taskUrl, mFile)
        val task = Task(url = taskUrl, request = request, file = mFile)
        // 同 (url, path) 已在下载中则复用在途任务，本次回调追加为它的订阅者
        // （参照 iOS WKMergeForwardDetailCell 的 downloadingMessages 守卫）。否则双击
        // 文件行会起两个任务写同一个 .tmp，两个 FileOutputStream 交错写入产生错乱文件。
        val running = mDownloadTasks.putIfAbsent(taskKey, task)
        if (running != null) {
            Log.d(TAG, "$taskUrl is already downloading, join in-flight task")
            running.addCallback(callback)
            return
        }
        task.addCallback(callback)
        realDownload(task)
    }

    private fun taskKey(url: String, file: File): String = url + "#" + file.absolutePath

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
                    try {
                        if (response.isSuccessful && response.code == 200) {
                            val body = response.body
                            mTask.contentSize = body.contentLength()
                            mTask.inputStream = body.byteStream()
                            if (mTask.fileOutputStream == null) {
                                // 先写 .tmp 临时文件，完成后原子 rename 到目标路径，避免中断残留半截文件。
                                // 构造可抛 FileNotFoundException（ENOSPC / EACCES / ENAMETOOLONG /
                                // 缺父目录）——OkHttp 对 onResponse 里抛出的异常只记日志、不重入
                                // onFailure，所以这里必须自捕获走 dispatchFail，否则进度条永久
                                // 卡住且任务滞留在 mDownloadTasks 里，把该 (url, path) 永久堵死。
                                mTask.fileOutputStream = FileOutputStream(tmpFileFor(mTask.file))
                            }
                            calculate(mTask)
                        } else {
                            // HTTP 4xx/5xx：过期文件 URL 的 403/404/410 是最常见的真实失败路径，
                            // 此前只打日志直接 return，上层 onFail 永不执行、进度条永久卡住。
                            Log.e(TAG, response.message)
                            // 失败分支才关 response：成功分支的 body 流正由 calculate 持续读取，
                            // 在这里关会把它读到一半掐断。
                            response.close()
                            dispatchFail(
                                mTask,
                                response.message.ifEmpty { "http " + response.code }
                            )
                        }
                    } catch (t: Throwable) {
                        // 覆盖 onResponse 主体的一切异常：含 FileOutputStream 构造的
                        // FileNotFoundException 与 calculate 循环外的 Error。response 若还
                        // 开着先关（calculate 内部失败时 response 已由流关闭语义收尾，再关
                        // 一次是幂等的），然后统一走失败收尾。
                        try {
                            response.close()
                        } catch (_: Exception) {
                        }
                        dispatchFail(mTask, t.message ?: t.javaClass.simpleName)
                    }
                }

            })
    }

    /** 目标文件对应的临时文件路径（下载期间写入，完成后 rename）。 */
    private fun tmpFileFor(file: File): File = File(file.absolutePath + ".tmp")

    /**
     * 统一的失败收尾：清残留 .tmp、置 ERROR、通知本任务全部订阅者 onFail、按身份移除任务。
     *
     * 每条失败路径都必须走这里，否则上层的 onFail（隐藏进度条 + toast）不会执行，
     * 进度条会永久卡住 —— HTTP 4xx/5xx（过期 URL 的 403/404/410 是最常见的真实失败）
     * 与 FileOutputStream 构造异常此前就是直接 return，既不清理也不回调。
     *
     * 订阅者从 [Task.callbacks] 取，不按 URL 查全局表：失败的是哪个任务，就通知谁
     * 的订阅者，不会误伤同 URL 不同路径下还在正常下载的兄弟任务。
     */
    private fun dispatchFail(mTask: Task, msg: String) {
        cleanupTmpFile(mTask)
        mTask.status = DownloadStatus.ERROR
        mTask.errorMsg = msg
        val subscribers = synchronized(mTask.callbacks) { mTask.callbacks.toList() }
        mTask.callbacks.clear()
        Handler(Looper.getMainLooper()).post {
            for (c in subscribers) {
                try {
                    c.third?.invoke(mTask.url, msg)
                } catch (e: Exception) {
                    Log.e(TAG, "onFail callback error: ${e.message}")
                }
            }
        }
        // 按身份移除：同 (url, path) 若已被新任务重占（极端时序），不能误删它的条目
        mDownloadTasks.remove(taskKey(mTask.url, mTask.file), mTask)
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
        // 订阅者快照：下载过程中新加入的订阅者（守卫命中时追加）也会在进度回调里
        // 被遍历到——每次 post 前重新取快照即可。这里取一次用于主循环，完成/失败时
        // 再取最终快照。
        fun notifyProgress(progress: Int) {
            val subscribers = synchronized(mTask.callbacks) { mTask.callbacks.toList() }
            Handler(Looper.getMainLooper()).post {
                for (c in subscribers) {
                    try {
                        c.first?.invoke(mTask.url, progress)
                    } catch (e: Exception) {
                        Log.e(TAG, "onProgress callback error: ${e.message}")
                    }
                }
            }
        }

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
                    mTask.inputStream?.close()
                    mTask.inputStream = null
                    val finalFile = mTask.file
                    val tmpFile = tmpFileFor(finalFile)
                    if (!tmpFile.renameTo(finalFile)) {
                        // rename 失败（如跨挂载点）时退化为复制。不能直接 copyTo(finalFile)：
                        // 它先删目标再流式写，中途 IOException（现实里就是 ENOSPC，完整复制
                        // 需要双倍空间）会把半截文件留在最终路径上。改为复制到第二临时名、
                        // 成功后再 rename，失败则删干净第二临时名——最终路径上要么是完整的
                        // 旧文件，要么是完整的新文件，永远不会出现半截。
                        val copyTmp = File(finalFile.absolutePath + ".copytmp")
                        try {
                            tmpFile.copyTo(copyTmp, overwrite = true)
                            if (!copyTmp.renameTo(finalFile)) {
                                copyTmp.copyTo(finalFile, overwrite = true)
                                copyTmp.delete()
                            }
                        } finally {
                            copyTmp.delete()
                            tmpFile.delete()
                        }
                    }
                    val subscribers = synchronized(mTask.callbacks) { mTask.callbacks.toList() }
                    mTask.callbacks.clear()
                    Handler(Looper.getMainLooper()).post {
                        for (c in subscribers) {
                            try {
                                c.second?.invoke(mTask.url, finalFile)
                            } catch (e: Exception) {
                                Log.e(TAG, "onComplete callback error: ${e.message}")
                            }
                        }
                    }
                    // 按身份移除：同 (url, path) 若已被新任务重占（极端时序），不能误删它的条目
                    mDownloadTasks.remove(taskKey(mTask.url, mTask.file), mTask)
                    break
                }
                mTask.fileOutputStream!!.write(bytes, 0, len)
                mTask.hasDownloadSize += len
                notifyProgress(((mTask.hasDownloadSize * 1f / mTask.contentSize) * 100).toInt())
            }
        } catch (t: Throwable) {
            // Throwable 而非 Exception：write 循环里的 OOM 等 Error 也要走失败收尾，
            // 否则任务滞留、进度条卡住。
            t.localizedMessage?.let { Log.e(TAG, it) }
            try {
                mTask.inputStream!!.close()
            } catch (_: Exception) {
            }
            // 清理半截 .tmp 文件并通知上层失败，避免残file被后续 exists() 当作有效缓存
            dispatchFail(mTask, t.message ?: "download error")
        }


    }

}