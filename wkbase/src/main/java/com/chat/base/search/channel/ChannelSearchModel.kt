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

import com.chat.base.base.WKBaseModel
import com.chat.base.net.IRequestResultErrorInfoListener
import com.chat.base.search.channel.dto.AroundRequest
import com.chat.base.search.channel.dto.AroundResult
import com.chat.base.search.channel.dto.ChannelSearchReq
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.dto.MediaHit
import com.chat.base.search.channel.dto.MessageHit
import com.chat.base.search.channel.dto.SearchErrorCode
import io.reactivex.rxjava3.core.Observable

/**
 * 频道内搜索数据编排层。负责：
 *  - 序列化请求体（[ChannelSearchBodyBuilder]）
 *  - 发起 5 个 `/v1/messages/_search*` 请求
 *  - 把服务端结构化错误码解析进 [ChannelSearchOutcome]
 *
 * 本地兜底不在此层处理。UI 层根据 [uiAction] 判断是否调用 IMSDK
 * `WKIM.msgManager.searchWithChannel` 取本地结果，并在 [ChannelSearchOutcome.success] 时把
 * `fromLocalFallback=true` 标记上。
 *
 * `X-Space-Id` 由 `CommonRequestParamInterceptor` 自动注入。
 */
object ChannelSearchModel : WKBaseModel() {

    private val service by lazy { createService(ChannelSearchService::class.java) }

    fun searchMessages(
        req: ChannelSearchReq,
        callback: (ChannelSearchOutcome<CursorList<MessageHit>>) -> Unit,
    ) {
        dispatch(service.searchMessages(ChannelSearchBodyBuilder.build(req)), callback)
    }

    fun searchAll(
        req: ChannelSearchReq,
        callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
    ) {
        dispatch(service.searchAll(ChannelSearchBodyBuilder.build(req)), callback)
    }

    fun searchMedia(
        req: ChannelSearchReq,
        callback: (ChannelSearchOutcome<CursorList<MediaHit>>) -> Unit,
    ) {
        // 服务端：_search_media keyword 必须为空，否则 400 VALIDATION_FAILED。
        dispatch(service.searchMedia(ChannelSearchBodyBuilder.build(req, allowKeyword = false)), callback)
    }

    fun searchFiles(
        req: ChannelSearchReq,
        callback: (ChannelSearchOutcome<CursorList<FileHit>>) -> Unit,
    ) {
        dispatch(service.searchFiles(ChannelSearchBodyBuilder.build(req)), callback)
    }

    fun searchAround(
        req: AroundRequest,
        callback: (ChannelSearchOutcome<AroundResult>) -> Unit,
    ) {
        dispatch(service.searchAround(ChannelSearchBodyBuilder.buildAround(req)), callback)
    }

    private fun <T : Any> dispatch(observable: Observable<T>, callback: (ChannelSearchOutcome<T>) -> Unit) {
        requestAndErrorBack(observable, object : IRequestResultErrorInfoListener<T> {
            override fun onSuccess(result: T) {
                callback(ChannelSearchOutcome.success(result))
            }

            override fun onFail(code: Int, msg: String, errJson: String) {
                callback(mapFailure(code, msg, errJson))
            }
        })
    }

    private fun <T : Any> mapFailure(httpStatus: Int, msg: String, errJson: String): ChannelSearchOutcome<T> {
        val parsed = ChannelSearchErrorParser.parse(errJson)
        if (parsed != null) {
            return ChannelSearchOutcome.failure(
                httpStatus = httpStatus,
                errorCode = parsed.code,
                errorMessage = parsed.message.ifBlank { msg },
                retryAfterSec = parsed.retryAfterSec,
            )
        }
        // 无结构化 error body：按 HTTP 状态码兜底分类。
        // 注意：ResponseExceptionHandle 对非 HttpException（连接失败、超时、DNS 等传输层异常）分配 code = -1；
        // 任何非正数都视作传输层错误，走 LOCAL_ERROR_NETWORK → FALLBACK_TO_LOCAL，触发本地 IMSDK 兜底搜索。
        val fallbackCode = when {
            httpStatus in 500..599 -> SearchErrorCode.UPSTREAM_UNAVAILABLE
            httpStatus == 429 -> SearchErrorCode.RATE_LIMITED
            httpStatus == 404 -> SearchErrorCode.NOT_FOUND
            httpStatus == 400 -> SearchErrorCode.VALIDATION_FAILED
            httpStatus <= 0 -> ChannelSearchOutcome.LOCAL_ERROR_NETWORK
            else -> SearchErrorCode.INTERNAL
        }
        return ChannelSearchOutcome.failure(httpStatus = httpStatus, errorCode = fallbackCode, errorMessage = msg)
    }
}
