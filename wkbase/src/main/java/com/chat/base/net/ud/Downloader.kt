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

        /** 下载中临时文件的保留子目录名，清扫逻辑（WKFileUtils.sweepStaleDownloadTmp）与之对齐。 */
        const val PARTIAL_DIR_NAME: String = ".partial"

        val instance: Downloader by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            Downloader()
        }
    }

    // pauseDownload / resumeDownload 已删除：全仓库无调用方，且实现与"断点续传"语义不符。
    // pauseDownload 置中间态后立刻 call.cancel()，cancel 会让 calculate() 里阻塞的
    // read() 抛异常，控制流进 catch（清 .tmp + 置 ERROR + 移除任务）而不是停在中间态的
    // 干净 break —— 只有恰好停在循环顶部才走得到 break。随后 resumeDownload 连任务都
    // 找不到。加上对无 Range 头的全新响应做 inputStream.skip(hasDownloadSize)
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
        // 文件行会起两个任务写同一个 .part，两个 FileOutputStream 交错写入产生错乱文件。
        //
        // 订阅必须走 trySubscribe 而不是 addCallback：取任务与加订阅是两个时刻，中间
        // 任务可能已经终态并清空了订阅列表，此时 addCallback 追加的回调永远不会被读取
        // （B-2：进度条永久卡住）。trySubscribe 在同一把任务锁内判定——入队成功返回
        // null，已终态则返回终态结果由这里立即回放。
        while (true) {
            val running = mDownloadTasks.putIfAbsent(taskKey, task)
            if (running == null) {
                // 本次调用拿到了新任务的所有权
                task.trySubscribe(callback)
                realDownload(task)
                return
            }
            Log.d(TAG, "$taskUrl is already downloading, join in-flight task")
            val terminal = running.trySubscribe(callback)
            if (terminal == null) return  // 已成为在途任务的订阅者，等它通知
            // 已终态：该任务马上/已经被摘表。回放终态给本次调用方，不再起新任务——
            // 成功时文件已落地，调用方 onComplete 里会自行校验 exists()。
            replayTerminal(running, terminal, callback)
            return
        }
    }

    private fun taskKey(url: String, file: File): String = url + "#" + file.absolutePath

    /**
     * 把一个已终态任务的结果回放给迟到的订阅者（只通知它一个，不碰该任务的其他人）。
     *
     * 必须 post 到主线程：与正常终态通知同一线程语义，上层 UI 回调（隐藏进度条、
     * toast、打开文件）都假定自己在主线程。
     */
    private fun replayTerminal(
        mTask: Task,
        terminal: Task.Terminal,
        callback: Triple<OnDownload?, OnComplete?, OnFail?>
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                when (terminal) {
                    is Task.Terminal.Success -> callback.second?.invoke(mTask.url, terminal.file)
                    is Task.Terminal.Failure -> callback.third?.invoke(mTask.url, terminal.msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "replay terminal callback error: ${e.message}")
            }
        }
    }

    private fun realDownload(mTask: Task) {
        com.chat.base.utils.WKFileUtils.getInstance().markDownloadStarted()
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

    /**
     * 目标文件对应的临时文件路径：<父目录>/.partial/<目标名>.part。
     *
     * 不再用 <目标路径>.tmp：冷启动清扫按后缀匹配，而正式缓存名的扩展名取自
     * 发送方可控的展示名（WKFileProvider.buildCacheFileName），一个叫 backup.tmp
     * 的附件会被缓存成 <key>.tmp 并在每次冷启动被当成残留删掉，形成永久重下
     * 循环。改为专用保留目录后，清扫只扫 .partial/，正式缓存名再也不会被误判。
     */
    private fun tmpFileFor(file: File): File {
        val parent = file.parentFile
        val dir = File(parent, PARTIAL_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, file.name + ".part")
    }

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
        // 置终态 + 取走订阅者（同一把任务锁内原子完成）。此刻之后任何 trySubscribe
        // 都会走回放分支，不会有订阅者落进这个再也不被读取的列表（B-2）。
        val subscribers = mTask.markTerminalAndDrain(Task.Terminal.Failure(msg))
        // 按身份移除：同 (url, path) 若已被新任务重占（极端时序），不能误删它的条目
        mDownloadTasks.remove(taskKey(mTask.url, mTask.file), mTask)
        Handler(Looper.getMainLooper()).post {
            for (c in subscribers) {
                try {
                    c.third?.invoke(mTask.url, msg)
                } catch (e: Exception) {
                    Log.e(TAG, "onFail callback error: ${e.message}")
                }
            }
        }
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
                        // rename 失败（如跨挂载点）时退化为"复制到第二临时名 + rename"。第二次
                        // rename 也失败时直接判失败，绝不再 copyTo(finalFile)——那条路径会先删
                        // 目标再流式写，中途 IOException（现实里就是 ENOSPC）会把半截文件留在
                        // 最终路径，恰好推翻"最终路径永不出现半截"的约束（P2-5）。宁可本次失败、
                        // 保住原有完整目标文件。
                        val copyTmp = File(tmpFile.absolutePath + ".copytmp")
                        var landed = false
                        try {
                            tmpFile.copyTo(copyTmp, overwrite = true)
                            landed = copyTmp.renameTo(finalFile)
                        } finally {
                            copyTmp.delete()
                            tmpFile.delete()
                        }
                        if (!landed) {
                            dispatchFail(mTask, "rename to final path failed")
                            break
                        }
                    }
                    // 见 dispatchFail：置终态 + 取走订阅者在同一把任务锁内原子完成，迟到的
                    // trySubscribe 会拿到 Success 回放而不是落进空列表（B-2）。
                    val subscribers = mTask.markTerminalAndDrain(Task.Terminal.Success(finalFile))
                    mDownloadTasks.remove(taskKey(mTask.url, mTask.file), mTask)
                    Handler(Looper.getMainLooper()).post {
                        for (c in subscribers) {
                            try {
                                c.second?.invoke(mTask.url, finalFile)
                            } catch (e: Exception) {
                                Log.e(TAG, "onComplete callback error: ${e.message}")
                            }
                        }
                    }
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