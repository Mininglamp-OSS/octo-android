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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * `data:` URI 解码单测。只覆盖 percent-encoded 分支 —— base64 分支依赖
 * `android.util.Base64`，host-side 单测（`returnDefaultValues=true`）拿不到真实实现。
 */
class OctoDataUriResolverTest {

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

    private fun assertThrowsIO(block: () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(true)
        }
    }
}
