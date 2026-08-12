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

package com.chat.uikit.chat.msgmodel

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import io.adaptivecards.renderer.GenericImageLoaderAsync
import io.adaptivecards.renderer.IResourceResolver
import io.adaptivecards.renderer.http.HttpRequestResult
import java.io.IOException

/**
 * `data:` URI 图片解析器（AC SDK 的 [IResourceResolver] 扩展点，按 scheme 注册）。
 *
 * ## 为什么需要
 * 服务端的 `ai.reasoning-process` 模版把折叠箭头做成 **SVG data URI**
 * （`data:image/svg+xml,%3Csvg...`），并在其上挂 `selectAction: Action.ToggleVisibility`。
 *
 * SDK 的 [GenericImageLoaderAsync.loadImage] 取图分三步：①按 scheme 查 ResourceResolver
 * ②本地资源 ③在线 URL。我们此前只注册了 [OctoAdaptiveImageLoader]（`IOnlineImageLoader`），
 * 它挂在第③步、且要先 `new URL(path)` 成功——而 `data:` 不是 Java `URL` 支持的协议，
 * 直接 `MalformedURLException`，回退 imageBaseUrl（空）后抛 IOException。
 *
 * 结果是箭头图标渲染为空 → **没有可点区域** → 卡片无法展开/收起。web 正常是因为浏览器
 * 原生支持 data URI。本 resolver 补上第①步这条通道。
 *
 * ## 支持的形态
 * - `data:image/svg+xml,<percent-encoded>`（模版当前用法）
 * - `data:image/png;base64,<base64>`
 * - 省略 mediatype 的 `data:,xxx`（RFC 2397 允许）
 *
 * 解码沿用 [OctoAdaptiveImageLoader.decodeImageBytes]：SVG 走 AndroidSVG，位图走降采样。
 * 与 http 图片只差"取字节"这一步。
 */
object OctoDataUriResolver : IResourceResolver {

    private const val TAG = "OctoDataUriResolver"

    /** scheme 名，注册与解析共用，避免两处写字符串漂移。 */
    const val SCHEME = "data"

    /**
     * 单张图解码后的字节上限（2MB），与 [OctoAdaptiveImageLoader] 的 MAX_BYTES 对齐。
     *
     * **两条分支都在解码前/解码中生效，不是事后校验** —— 事后校验挡不住任何东西：
     * 畸形 payload 已经把内存吃完了才抛异常。见 [MAX_BASE64_CHARS] 与 [percentDecode]。
     *
     * `internal` 而非 `private`：让单测按真实上限构造越界用例，避免测试里复制一份常量后漂移。
     */
    internal const val MAX_BYTES = 2 * 1024 * 1024

    /**
     * base64 形态**解码前**的字符上限。
     *
     * `Base64.decode` 一次性分配完整结果数组，没有流式/带上限的入口，所以只能按输入
     * 长度判。标准 base64 是 4 字符 → 3 字节，故 payload 长度超过 `MAX_BYTES/3*4`
     * 时解码结果必然越界；换行等空白只会让结果更小，而"90% 是空白"的 payload 也不是
     * 合法内联图，一并拒掉即可。
     */
    internal const val MAX_BASE64_CHARS = MAX_BYTES / 3 * 4

    /**
     * percent 形态缓冲区的初始容量（8KB）。折叠箭头 SVG 约 1KB，按 `payload.length`
     * 预分配等于信任不可信长度；且它甚至不是解码结果的上界——多字节字符 1 char → 3 byte，
     * 结果最多可达 3 倍长度，预分配后还要成倍扩容 + `toByteArray()` 再拷一份。
     */
    private const val PERCENT_BUF_INITIAL = 8 * 1024

    override fun resolveImageResource(
        url: String,
        loader: GenericImageLoaderAsync?
    ): HttpRequestResult<Bitmap> = resolve(url)

    override fun resolveImageResource(
        url: String,
        loader: GenericImageLoaderAsync?,
        maxWidth: Int
    ): HttpRequestResult<Bitmap> = resolve(url)

    private fun resolve(url: String): HttpRequestResult<Bitmap> = try {
        val bytes = decodeDataUri(url)
        HttpRequestResult(OctoAdaptiveImageLoader.decodeImageBytes(bytes))
    } catch (t: Throwable) {
        // 失败即降级为空图，与"没有本 resolver"的 baseline 等价，不让单个坏图标毙掉整卡。
        Log.w(TAG, "data URI 解码失败 (len=${url.length})", t)
        HttpRequestResult(t as? Exception ?: RuntimeException(t))
    }

    /**
     * `data:[<mediatype>][;base64],<data>` → 原始字节。
     *
     * 不用 `Uri.parse` 取 scheme-specific part：SVG payload 里带 `#`（如 fill="#333"）
     * 会被当 fragment 截断。这里按第一个逗号手工切分。
     *
     * @param base64Decode base64 解码器接缝。默认 `android.util.Base64`；host-side 单测
     *   （`returnDefaultValues=true`）拿不到真实实现（返回 null），由测试注入 JVM 的
     *   `java.util.Base64` 来覆盖这条分支。
     */
    internal fun decodeDataUri(
        url: String,
        base64Decode: (String) -> ByteArray? = { Base64.decode(it, Base64.DEFAULT) }
    ): ByteArray {
        if (!url.startsWith("$SCHEME:", ignoreCase = true)) {
            throw IOException("not a data URI")
        }
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("data URI missing comma")

        val meta = url.substring("$SCHEME:".length, comma)
        val payload = url.substring(comma + 1)
        if (payload.isEmpty()) throw IOException("data URI empty payload")

        val isBase64 = meta.split(';').any { it.trim().equals("base64", ignoreCase = true) }
        val bytes: ByteArray? = if (isBase64) {
            if (payload.length > MAX_BASE64_CHARS) {
                throw IOException("data URI base64 payload 超过 $MAX_BASE64_CHARS 字符上限")
            }
            // DEFAULT 即标准字母表（+/），data URI 用的就是它；解码时本身容忍换行。
            base64Decode(payload)
        } else {
            percentDecode(payload)
        }
        if (bytes == null || bytes.isEmpty()) throw IOException("data URI decoded to 0 byte")
        // 兜底精确校验：base64 的字符上限是保守估计（空白字符会让实际结果更小），
        // percent 分支边解边查最多溢出一个字符的字节数，都可能停在略超 MAX_BYTES 处。
        if (bytes.size > MAX_BYTES) throw IOException("data URI 超过 $MAX_BYTES 字节上限")
        return bytes
    }

    /**
     * RFC 3986 percent-decoding，**按字节**解，不绕 String。
     *
     * 不用 `URLDecoder`：它实现的是 `application/x-www-form-urlencoded` 语义
     * （`+` 解成空格），而 data URI 里 `+` 是字面量（SVG path 数据、`image/svg+xml`
     * 都会出现）。而且它返回 String —— percent-encoded 的二进制载荷（如
     * `%89PNG`）不是合法 UTF-8，转 String 会被替换成 U+FFFD，字节就毁了。
     */
    private fun percentDecode(payload: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(payload.length, PERCENT_BUF_INITIAL))
        var i = 0
        while (i < payload.length) {
            // 边解边查上限：缓冲区最多长到 MAX_BYTES + 一个字符的字节数就抛，
            // 不会先把畸形 payload 吃满内存再判越界。
            if (out.size() > MAX_BYTES) {
                throw IOException("data URI percent payload 解码中超过 $MAX_BYTES 字节上限")
            }
            val c = payload[i]
            if (c == '%' && i + 2 < payload.length) {
                val hi = Character.digit(payload[i + 1], 16)
                val lo = Character.digit(payload[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            // 非转义字符按 UTF-8 落字节（ASCII 走单字节，多字节字符也能正确编码）
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toByteArray()
    }
}
