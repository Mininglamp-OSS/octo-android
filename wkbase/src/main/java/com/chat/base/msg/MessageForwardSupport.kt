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

package com.chat.base.msg

import com.chat.base.msgitem.WKContentType

/**
 * 逐条转发支持的消息类型白名单 —— 与 iOS WKApp.allowForwards 保持一致。
 *
 * 命中：原 baseContentMsgModel 直接转发；
 * 未命中：调用方降级为 WKTextContent(displayContent)。
 */
object MessageForwardSupport {

    @JvmStatic
    fun allowForward(contentType: Int): Boolean = when (contentType) {
        WKContentType.WK_TEXT,
        WKContentType.WK_IMAGE,
        WKContentType.WK_GIF,
        WKContentType.WK_VIDEO,
        WKContentType.WK_FILE,
        WKContentType.WK_MULTIPLE_FORWARD,
        WKContentType.richText -> true
        else -> false
    }
}
