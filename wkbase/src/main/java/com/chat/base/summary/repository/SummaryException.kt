/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary.repository

/**
 * Summary 模块业务异常,带 HTTP status (区分 409 编辑冲突) + 后端 message。
 */
class SummaryException(
    val httpStatus: Int,
    /** 后端 envelope.code, 0 = 成功 */
    val apiCode: Int,
    message: String?,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    val isConflict: Boolean get() = httpStatus == 409

    val isUnauthorized: Boolean get() = httpStatus == 401
}
