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

package com.chat.base.net

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONReader
import com.xinbida.wukongim.utils.WKHeapProbe
import okhttp3.ResponseBody
import retrofit2.Converter
import java.io.PushbackReader
import java.lang.reflect.Type

/**
 * 大响应体走流式解析，避免整包物化。
 *
 * 改动前是 `JSON.parseObject(value.string(), type)`：`ResponseBody.string()` 内部先
 * `Buffer.writeAll(source)` 把整个响应解压进 okio Segment 链，再整体转成一个 UTF-16 String，
 * 然后才建对象树——同一时刻内存里有三份。线上 OOM 有一条就死在第一步
 * （`okio.Segment.<init>`，栈退时释放 231MB），FastJson 还没跑到。
 *
 * 现在按大小分流：
 * - 响应 ≤ [STREAM_THRESHOLD_CHARS]：一次读完，走原来的 `JSON.parseObject(String, Type)`，
 *   行为与改动前完全一致（绝大多数接口走这条，不引入回归面）。
 * - 超过阈值：把已读的头部推回去，交给 [JSONReader] 增量解析。okio 不再缓冲整包，
 *   也不再产生整包 String，峰值只剩对象树本身。
 *
 * 注意 fastjson 1.2.83 的 `JSON.parseObject(InputStream, ...)` **不是流式**
 * （`JSON.java:569` 起先 `allocateBytes(64KB)` 再按 1.5 倍扩容 + arraycopy 读完整包），
 * 用它没有意义，必须走 `JSONReader(Reader)`。
 */
class FastJsonResponseBodyConverter<T>(
    private val type: Type
) : Converter<ResponseBody, T> {

    override fun convert(value: ResponseBody): T? {
        return value.use { body ->
            val reader = body.charStream()
            val head = CharArray(STREAM_THRESHOLD_CHARS)
            var headLen = 0
            while (headLen < head.size) {
                val read = reader.read(head, headLen, head.size - headLen)
                if (read == -1) break
                headLen += read
            }

            if (headLen < head.size) {
                // 小响应：整包已在手上，保持原路径
                if (headLen == 0) null else JSON.parseObject(String(head, 0, headLen), type)
            } else {
                val before = WKHeapProbe.usedNow()
                val result = PushbackReader(reader, headLen).use { pushback ->
                    pushback.unread(head, 0, headLen)
                    JSONReader(pushback).use { it.readObject<T>(type) }
                }
                WKHeapProbe.span(
                    "http JSONReader(stream)", ">${head.size}chars", before, WKHeapProbe.usedNow()
                )
                result
            }
        }
    }

    private companion object {
        /** 超过这个长度才值得流式。256K 字符 ≈ 512KB，远小于会话同步那种 350 万字符的响应。 */
        const val STREAM_THRESHOLD_CHARS = 256 * 1024
    }
}
