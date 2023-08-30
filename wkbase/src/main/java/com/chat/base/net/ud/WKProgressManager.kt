package com.chat.base.net.ud

import java.util.concurrent.ConcurrentHashMap

class WKProgressManager private constructor() {

    companion object {
        val instance: WKProgressManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            WKProgressManager()
        }
    }

    private var progressList: ConcurrentHashMap<Any, IProgress>? = null

    interface IProgress {
        fun onProgress(tag: Any?, progress: Int)
        fun onSuccess(tag: Any?, path: String?)
        fun onFail(tag: Any?, msg: String?)
    }

    fun registerProgress(tag: Any, progress: IProgress) {
        if (progressList == null) {
            progressList = ConcurrentHashMap()
        }
        progressList!![tag] = progress
    }

    fun unregisterProgress(tag: Any) {
        if (progressList != null) {
            progressList!!.remove(tag)
        }
    }

    internal fun onSuccess(tag: Any, filePath: String) {
        if (progressList != null) {
            progressList!![tag]!!.onSuccess(tag, filePath)
        }
    }


    internal fun onFail(tag: Any, msg: String) {
        if (progressList != null) {
            progressList!![tag]!!.onFail(tag, msg)
        }
    }

    internal fun seekProgress(tag: Any?, progress: Int) {
        if (progressList != null && progressList!!.containsKey(tag)) {
            progressList!![tag]!!.onProgress(tag, progress)
        }
    }
}