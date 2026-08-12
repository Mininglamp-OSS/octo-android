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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * `data:` URI 解码单测。
 *
 * base64 分支通过 [decodeDataUri] 的解码器接缝注入 JVM 的 `java.util.Base64` 覆盖 ——
 * host-side 单测（`returnDefaultValues=true`）拿不到 `android.util.Base64` 的真实实现。
 * `getMimeDecoder()` 与 `android.util.Base64.DEFAULT` 语义一致（标准字母表 + 容忍换行）。
 */
class OctoDataUriResolverTest {

    /** 记录是否真的走到解码器 —— 越界用例要证明**解码前**就被拒了。 */
    private class RecordingBase64 : (String) -> ByteArray? {
        var invoked = false
        override fun invoke(payload: String): ByteArray? {
            invoked = true
            return java.util.Base64.getMimeDecoder().decode(payload)
        }
    }

    private fun decode(url: String) = String(OctoDataUriResolver.decodeDataUri(url), Charsets.UTF_8)

    @Test
    fun `percent encoded svg decodes to markup`() {
        val url = "data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3C%2Fsvg%3E"
        assertEquals("""<svg xmlns="http://www.w3.org/2000/svg"></svg>""", decode(url))
    }

    /** `#` 必须原样保留 —— 用 Uri.parse 取 SSP 会把它当 fragment 分隔符截断后半段。 */
    @Test
    fun `hash in fill color survives`() {
        val url = "data:image/svg+xml,%3Cpath%20fill%3D%22%23333%22%2F%3E"
        assertEquals("""<path fill="#333"/>""", decode(url))
    }

    /** `+` 是 path 数据的合法字符，按 RFC 3986 应保持字面量（不是表单编码的空格）。 */
    @Test
    fun `plus is not turned into space`() {
        val url = "data:image/svg+xml,%3Cpath%20d%3D%22M1+2%22%2F%3E"
        assertEquals("""<path d="M1+2"/>""", decode(url))
    }

    /**
     * 非 UTF-8 的转义序列必须按字节保留 —— 走 String 中转会被替换成 U+FFFD，
     * percent-encoded 的二进制图（PNG 魔数 89 50 4E 47）就毁了。
     */
    @Test
    fun `non utf8 escapes survive as raw bytes`() {
        // %89PNG → 89 50 4E 47，正是 PNG 魔数
        val bytes = OctoDataUriResolver.decodeDataUri("data:image/png,%89PNG")
        assertEquals(4, bytes.size)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    /** 畸形转义（`%` 后不是两位 hex）按字面量处理，不抛异常。 */
    @Test
    fun `malformed escape is kept literal`() {
        assertEquals("100%25", decode("data:,100%2525"))
        assertEquals("a%zz", decode("data:,a%zz"))
    }

    /** RFC 2397 允许省略 mediatype。 */
    @Test
    fun `omitted mediatype is accepted`() {
        assertEquals("<svg/>", decode("data:,%3Csvg%2F%3E"))
    }

    @Test
    fun `scheme is case insensitive`() {
        assertEquals("<svg/>", decode("DATA:image/svg+xml,%3Csvg%2F%3E"))
    }

    @Test
    fun `non data uri is rejected`() {
        assertThrowsIO { decode("https://example.com/a.svg") }
    }

    @Test
    fun `missing comma is rejected`() {
        assertThrowsIO { decode("data:image/svg+xml") }
    }

    @Test
    fun `empty payload is rejected`() {
        assertThrowsIO { decode("data:image/svg+xml,") }
    }

    // ---- base64 分支 ----

    /** PNG 魔数 `89 50 4E 47 0D 0A 1A 0A` 的 base64 形态。 */
    @Test
    fun `base64 payload decodes to raw bytes`() {
        val bytes = OctoDataUriResolver.decodeDataUri(
            "data:image/png;base64,iVBORw0KGgo=",
            RecordingBase64()
        )
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            bytes
        )
    }

    /** `;base64` 标记大小写不敏感，且容忍参数间空格（`data:image/png; base64,...`）。 */
    @Test
    fun `base64 marker is case insensitive and space tolerant`() {
        val expected = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(
            expected,
            OctoDataUriResolver.decodeDataUri("data:image/png;BASE64,iVBORw0KGgo=", RecordingBase64())
        )
        assertArrayEquals(
            expected,
            OctoDataUriResolver.decodeDataUri("data:image/png; base64,iVBORw0KGgo=", RecordingBase64())
        )
    }

    /** base64 payload 里 `+` `/` 属标准字母表，必须交给解码器而不是被当 percent 语义处理。 */
    @Test
    fun `base64 standard alphabet is decoded not percent unescaped`() {
        val bytes = OctoDataUriResolver.decodeDataUri("data:;base64,//79", RecordingBase64())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte()), bytes)
    }

    // ---- 内存上限（回归：上限必须在解码前/解码中生效，不能事后补刀）----

    /**
     * 超长 base64 payload 必须在**调用解码器之前**就被拒 —— `Base64.decode` 会一次性
     * 分配完整结果数组，解码完再查上限等于让畸形 payload 先把内存吃掉。
     */
    @Test
    fun `oversized base64 payload is rejected before decoding`() {
        val recorder = RecordingBase64()
        val payload = "A".repeat(OctoDataUriResolver.MAX_BASE64_CHARS + 1)
        assertThrowsIO {
            OctoDataUriResolver.decodeDataUri("data:image/png;base64,$payload", recorder)
        }
        assertFalse("解码器不应被调用：越界判定必须发生在解码前", recorder.invoked)
    }

    /** 刚好压线的 base64 payload 不应被上限误杀。 */
    @Test
    fun `base64 payload at the char limit is accepted`() {
        val payload = "A".repeat(OctoDataUriResolver.MAX_BASE64_CHARS)
        val bytes = OctoDataUriResolver.decodeDataUri("data:image/png;base64,$payload", RecordingBase64())
        assertTrue(
            "压线 payload 解码后应落在 MAX_BYTES 内，实际 ${bytes.size}",
            bytes.isNotEmpty() && bytes.size <= OctoDataUriResolver.MAX_BYTES
        )
    }

    /**
     * percent 分支边解边查上限：必须**解码过程中**抛，而不是缓冲完整个 payload 再由
     * 末尾的兜底校验补刀。靠异常消息区分这两条路径。
     */
    @Test
    fun `oversized percent payload is rejected mid decode`() {
        val payload = "A".repeat(OctoDataUriResolver.MAX_BYTES + 1024)
        try {
            decode("data:image/svg+xml,$payload")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(
                "应由解码中的增量上限拦截，实际消息：${e.message}",
                e.message?.contains("解码中") == true
            )
        }
    }

    private fun assertThrowsIO(block: () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(true)
        }
    }
}
