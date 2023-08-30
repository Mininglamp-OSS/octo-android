package com.chat.base.net.ud

import java.io.File

class WKDownloader private constructor() {

    companion object {
        val instance: WKDownloader by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            WKDownloader()
        }
    }

    fun download(url: String, savePath: String, iProgress: WKProgressManager.IProgress?) {
        Downloader.instance.download(url, savePath, object : OnDownload {
            override fun invoke(url: String, progress: Int) = if (iProgress != null) {
                iProgress.run { onProgress(url, progress) }
            } else {
                WKProgressManager.instance.seekProgress(url, progress)
            }

        }, object : OnComplete {
            override fun invoke(url: String, file: File) = if (iProgress != null) {
                iProgress.run { onSuccess(url, file.absolutePath) }
            } else {
                WKProgressManager.instance.onSuccess(url, file.absolutePath)
            }

        }, object : OnFail {
            override fun invoke(url: String, reason: String) = if (iProgress != null) {
                iProgress.run { onFail(url, reason) }
            } else {
                WKProgressManager.instance.onFail(url, reason)
            }
        })
    }
}