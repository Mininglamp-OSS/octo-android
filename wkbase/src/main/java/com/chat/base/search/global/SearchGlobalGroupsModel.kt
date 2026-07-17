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
import com.chat.base.search.channel.dto.SearchErrorCode
import com.chat.base.search.global.dto.SearchGlobalGroupsReq
import com.chat.base.search.global.dto.SearchGlobalGroupsResp
import io.reactivex.rxjava3.core.Observable

/**
 * 全局聚合搜索（L1）数据编排层。负责：
 *  - 序列化请求体（[SearchGlobalGroupsBodyBuilder]）
 *  - 发起 `POST /v1/messages/_search_global_groups`
 *  - 把服务端结构化错误码解析进 [ChannelSearchOutcome]
 *
 * 本地兜底不在此层处理。UI 层根据 [com.chat.base.search.channel.uiAction] 判断是否
 * 调用 `WKIM.msgManager.search(keyword)` 取本地聚合结果，并在 [ChannelSearchOutcome.success]
 * 时把 `fromLocalFallback=true` 标记上。
 *
 * `X-Space-Id` 由 `CommonRequestParamInterceptor` 自动注入。
 */
object SearchGlobalGroupsModel : WKBaseModel() {

    private val service by lazy { createService(SearchGlobalGroupsService::class.java) }

    fun searchGroups(
        req: SearchGlobalGroupsReq,
        callback: (ChannelSearchOutcome<SearchGlobalGroupsResp>) -> Unit,
    ) {
        dispatch(service.searchGlobalGroups(SearchGlobalGroupsBodyBuilder.build(req)), callback)
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
        // 无结构化 error body：按 HTTP 状态码兜底分类。
        // ResponseExceptionHandle 对连接失败/超时等传输层异常分配 code = -1；
        // 任何非正数都视作传输层错误，走 LOCAL_ERROR_NETWORK → FALLBACK_TO_LOCAL 触发本地兜底。
        // 特殊：L1 端点没有 channel 概念，裸 404（nginx 未挂路由，body 非结构化）只可能是"接口未部署"，
        // 与频道内搜索的 404="频道不可见"语义完全不同，这里也走本地兜底而不是 BLOCK_NOT_FOUND。
        // 若服务端明确返回结构化 NOT_FOUND（如缺 X-Space-Id 且 RequireSpaceID=true），则 parsed 分支
        // 已提前 return，不会走到这里。
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
