package com.chat.base.net.ud

import android.os.Handler
import android.os.Looper
import android.util.Log
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

    fun pauseDownload(url: String) {
        mDownloadTasks[url]?.status = DownloadStatus.PAUSED
        mDownloadTasks[url]?.call!!.cancel()
    }

    fun resumeDownload(taskUrl: String) {

        val task = mDownloadTasks[taskUrl]
        if (task == null) {
            Log.e(TAG, task + "is not exit")
            return
        }
        if (task.status != DownloadStatus.PAUSED) {
            Log.e(TAG, taskUrl + "is already downloading")
            return
        }
        task.status = DownloadStatus.RESUME
        realDownload(task)
    }

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
        mDownloadTasks[taskUrl] = task
        realDownload(task)

    }

    private fun realDownload(mTask: Task) {
        mOkHttpClient
            .newCall(mTask.request).apply {
                mTask.call = this
            }.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mTask.errorMsg = if (e.message == null) {
                        "unKnow error"
                    } else {
                        e.message
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful && response.code == 200) {
                        val body = response.body
                        if (body == null) {
                            mTask.errorMsg = response.message
                            return
                        }
                        mTask.contentSize = body.contentLength()
                        mTask.inputStream = body.byteStream()
                        if (mTask.fileOutputStream == null) {
                            mTask.fileOutputStream = FileOutputStream(mTask.file)
                        }
                        calculate(mTask)
                    } else {
                        Log.e(TAG, response.message)
                    }

                }

            })
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
                if (mTask.status == DownloadStatus.PAUSED) {
                    break
                }
                if (mTask.status == DownloadStatus.RESUME) {
                    mTask.inputStream!!.skip(mTask.hasDownloadSize)
                    mTask.status = DownloadStatus.DOWNLOADING
                }
                val len = mTask.inputStream!!.read(bytes)
                if (len == -1) {
                    Handler(Looper.getMainLooper()).post {
                        callback?.second?.invoke(mTask.url, mTask.file)
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
            mTask.inputStream!!.close()

        }


    }

}