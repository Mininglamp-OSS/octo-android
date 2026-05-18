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
import com.chat.base.utils.WKLogUtils
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import okio.buffer
import okio.sink
import java.io.IOException


class FileRequestBody(private val requestBody: RequestBody, private val tag: Any) : RequestBody() {
    private var mCurrentLength: Long = 0
    private var bufferedSink: BufferedSink? = null
    var handler = Handler(Looper.getMainLooper())
    override fun contentLength(): Long {
        return requestBody.contentLength()
    }

    override fun isOneShot(): Boolean {
        return false
    }
    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }

    override fun writeTo(sink: BufferedSink) {
        mCurrentLength = 0
        val contentLength = contentLength()
        val forwardingSink: ForwardingSink = object : ForwardingSink(sink) {
            @Throws(IOException::class)
            override fun write(source: Buffer, byteCount: Long) {
                mCurrentLength += byteCount
                val f1 = mCurrentLength / contentLength.toFloat()
                handler.post {
                    var p = (f1 * 100).toInt()
                    if (p > 100) {
                        p = 100
                    }
                    WKProgressManager.instance.seekProgress(tag, p)
                }
                super.write(source, byteCount)
            }
        }
        val bufferedSink: BufferedSink = forwardingSink.buffer()
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}