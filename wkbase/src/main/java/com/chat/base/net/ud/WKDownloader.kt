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