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

package com.chat.base.search.global

import com.chat.base.base.WKBaseModel
import com.chat.base.net.IRequestResultErrorInfoListener
import com.chat.base.search.channel.ChannelSearchErrorParser
import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.SearchErrorCode
import com.chat.base.search.global.dto.SearchGlobalMessagesReq
import io.reactivex.rxjava3.core.Observable

/**
 * 全局聚合搜索（L2）数据编排层。负责：
 *  - 序列化请求体（[SearchGlobalMessagesBodyBuilder]）
 *  - 发起 `POST /v1/messages/_search_global_messages`
 *  - 把服务端结构化错误码解析进 [ChannelSearchOutcome]
 *
 * UI 层根据 `uiAction()` 决定是否回退（`FALLBACK_TO_LOCAL` 场景由 UI 层自行处理，
 * ViewModel 保持不依赖 IMSDK 以利单测）。
 *
 * `X-Space-Id` 由 `CommonRequestParamInterceptor` 自动注入。
 */
object SearchGlobalMessagesModel : WKBaseModel() {

    private val service by lazy { createService(SearchGlobalMessagesService::class.java) }

    fun searchMessages(
        req: SearchGlobalMessagesReq,
        callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
    ) {
        dispatch(service.searchGlobalMessages(SearchGlobalMessagesBodyBuilder.build(req)), callback)
    }

    private fun <T : Any> dispatch(
        observable: Observable<T>,
        callback: (ChannelSearchOutcome<T>) -> Unit,
    ) {
        requestAndErrorBack(observable, object : IRequestResultErrorInfoListener<T> {
            override fun onSuccess(result: T) {
                callback(ChannelSearchOutcome.success(result))
            }

            override fun onFail(code: Int, msg: String, errJson: String) {
                callback(mapFailure(code, msg, errJson))
            }
        })
    }

    private fun <T : Any> mapFailure(
        httpStatus: Int,
        msg: String,
        errJson: String,
    ): ChannelSearchOutcome<T> {
        val parsed = ChannelSearchErrorParser.parse(errJson)
        if (parsed != null) {
            return ChannelSearchOutcome.failure(
                httpStatus = httpStatus,
                errorCode = parsed.code,
                errorMessage = parsed.message.ifBlank { msg },
                retryAfterSec = parsed.retryAfterSec,
            )
        }
        // 与 [SearchGlobalGroupsModel.mapFailure] 完全对齐：L2 也无 channel 概念，
        // 裸 404（nginx 未挂路由）视为接口未部署 → 走本地兜底而不是 BLOCK_NOT_FOUND。
        val fallbackCode = when {
            httpStatus in 500..599 -> SearchErrorCode.UPSTREAM_UNAVAILABLE
            httpStatus == 429 -> SearchErrorCode.RATE_LIMITED
            httpStatus == 404 -> ChannelSearchOutcome.LOCAL_ERROR_NETWORK
            httpStatus == 400 -> SearchErrorCode.VALIDATION_FAILED
            httpStatus <= 0 -> ChannelSearchOutcome.LOCAL_ERROR_NETWORK
            else -> SearchErrorCode.INTERNAL
        }
        return ChannelSearchOutcome.failure(
            httpStatus = httpStatus,
            errorCode = fallbackCode,
            errorMessage = msg,
        )
    }
}
