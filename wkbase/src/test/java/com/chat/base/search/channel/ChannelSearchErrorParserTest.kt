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

package com.chat.base.search.channel

import com.chat.base.search.channel.dto.SearchErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSearchErrorParserTest {

    @Test
    fun parses_validation_failed() {
        val json = """
            {"error":{
                "code":"err.server.messages_search.validation_failed",
                "message":"keyword too long",
                "details":{"field":"keyword","reason":"max_length","max_length":64},
                "http_status":400
            }}
        """.trimIndent()
        val parsed = ChannelSearchErrorParser.parse(json)!!
        assertEquals(SearchErrorCode.VALIDATION_FAILED, parsed.code)
        assertEquals("keyword too long", parsed.message)
        assertEquals(0, parsed.retryAfterSec)
    }

    @Test
    fun parses_rate_limited_with_retry_after() {
        val json = """
            {"error":{
                "code":"err.server.messages_search.rate_limited",
                "message":"too many requests",
                "details":{"retry_after":3},
                "http_status":429
            }}
        """.trimIndent()
        val parsed = ChannelSearchErrorParser.parse(json)!!
        assertEquals(SearchErrorCode.RATE_LIMITED, parsed.code)
        assertEquals(3, parsed.retryAfterSec)
    }

    @Test
    fun returns_null_on_legacy_status_msg_envelope() {
        // 旧 ResponseExceptionHandle 的 { status, msg } 形态没有 error 字段，应该解析为 null
        val legacy = """{"status":400,"msg":"bad request"}"""
        assertNull(ChannelSearchErrorParser.parse(legacy))
    }

    @Test
    fun returns_null_on_blank_or_garbage() {
        assertNull(ChannelSearchErrorParser.parse(null))
        assertNull(ChannelSearchErrorParser.parse(""))
        assertNull(ChannelSearchErrorParser.parse("not-json"))
        assertNull(ChannelSearchErrorParser.parse("{}"))
        assertNull(ChannelSearchErrorParser.parse("""{"error":{}}"""))
        assertNull(ChannelSearchErrorParser.parse("""{"error":{"code":""}}"""))
    }
}
