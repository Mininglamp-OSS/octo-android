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