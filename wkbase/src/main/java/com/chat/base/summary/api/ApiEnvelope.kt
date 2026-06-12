/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.api

/**
 * Summary API 响应包: { code, message, data }。
 *
 * 项目内通用 [com.chat.base.net.entity.CommonResponse] 用的是 status/msg, 与
 * 总结后端约定不同(对齐 octo-web/dmworksummary), 因此这里独立一份。
 *
 * code == 0 视为成功。
 */
class ApiEnvelope<T> {
    @JvmField
    var code: Int = 0

    @JvmField
    var message: String? = null

    @JvmField
    var data: T? = null

    val isSuccess: Boolean get() = code == 0
}
