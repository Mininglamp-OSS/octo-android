package com.chat.base.net.ud

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.IOException

class FileRequestBody(private val requestBody: RequestBody, private val tag: Any) : RequestBody() {
    private var mCurrentLength: Long = 0
    var handler = Handler(Looper.getMainLooper())
    override fun contentLength(): Long {
        return requestBody.contentLength()
    }

    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }

    override fun writeTo(sink: BufferedSink) {
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
        val bufferedSink = forwardingSink.buffer()
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}