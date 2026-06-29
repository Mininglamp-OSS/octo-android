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

/**
 * Model 层 → UI 层的统一返回。`ok=true` 时 [data] 非空；`ok=false` 时 [errorCode] 必填。
 * 调用方按 [errorCode] 决定 UI 行为（toast / 顶部 banner / 输入禁用倒计时 / 本地兜底）。
 */
class ChannelSearchOutcome<T>(
    val ok: Boolean,
    val data: T? = null,
    val httpStatus: Int = 0,
    /** 服务端结构化错误码或本地态错误，[ChannelSearchOutcome] 内部枚举常量见 [SearchErrorCode] 与 [LOCAL_ERROR_*]。 */
    val errorCode: String? = null,
    val errorMessage: String? = null,
    /** 仅在 [SearchErrorCode.RATE_LIMITED] 时由服务端 details.retry_after 写入。 */
    val retryAfterSec: Int = 0,
    /** 是否为本地 IMSDK 兜底结果（仅消息维度）。UI 据此显示"离线结果"banner。 */
    val fromLocalFallback: Boolean = false,
) {
    companion object {
        /** 网络层异常（连接失败 / 超时），与服务端无关。UI 也应回退到本地兜底。 */
        const val LOCAL_ERROR_NETWORK = "client.channel_search.network"
        const val LOCAL_ERROR_PARSE = "client.channel_search.parse"
        const val LOCAL_ERROR_UNKNOWN = "client.channel_search.unknown"

        fun <T> success(data: T, fromLocalFallback: Boolean = false): ChannelSearchOutcome<T> =
            ChannelSearchOutcome(ok = true, data = data, httpStatus = 200, fromLocalFallback = fromLocalFallback)

        fun <T> failure(
            httpStatus: Int,
            errorCode: String,
            errorMessage: String? = null,
            retryAfterSec: Int = 0,
        ): ChannelSearchOutcome<T> =
            ChannelSearchOutcome(
                ok = false,
                httpStatus = httpStatus,
                errorCode = errorCode,
                errorMessage = errorMessage,
                retryAfterSec = retryAfterSec,
            )
    }
}

/** 服务端错误码归类成几个 UI 该响应的桶。 */
enum class ChannelSearchUiAction {
    /** 5xx / 网络异常：触发本地回退（仅消息维度），UI 顶部 banner 提示。 */
    FALLBACK_TO_LOCAL,

    /** 429 服务端限流：按 retry_after 倒计时禁用输入框，倒计时结束后用户可重试。 */
    RATE_LIMITED,

    /** 503 disabled：搜索功能未启用，禁用入口或显示 empty state，不再触发请求。 */
    FEATURE_DISABLED,

    /** 404 not_found：频道在 Space 维度不可见，阻断该 tab，提示"无法搜索"。 */
    BLOCK_NOT_FOUND,

    /** 400 validation_failed：客户端构造请求时出错，应埋点上报；UI Toast"搜索条件无效"。 */
    VALIDATION_ERROR,

    /** 500/未知 internal：通用错误，Toast"搜索失败，请重试"。 */
    GENERIC_ERROR,
}

/** [errorCode] → UI 行为映射，UI 层直接消费。 */
fun ChannelSearchOutcome<*>.uiAction(): ChannelSearchUiAction = when (errorCode) {
    SearchErrorCode.UPSTREAM_UNAVAILABLE,
    ChannelSearchOutcome.LOCAL_ERROR_NETWORK -> ChannelSearchUiAction.FALLBACK_TO_LOCAL

    SearchErrorCode.RATE_LIMITED -> ChannelSearchUiAction.RATE_LIMITED
    SearchErrorCode.DISABLED -> ChannelSearchUiAction.FEATURE_DISABLED
    SearchErrorCode.NOT_FOUND -> ChannelSearchUiAction.BLOCK_NOT_FOUND
    SearchErrorCode.VALIDATION_FAILED,
    SearchErrorCode.DEPTH_EXCEEDED -> ChannelSearchUiAction.VALIDATION_ERROR

    else -> ChannelSearchUiAction.GENERIC_ERROR
}
