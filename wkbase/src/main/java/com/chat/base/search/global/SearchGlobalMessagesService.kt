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

import com.alibaba.fastjson.JSONObject
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 全局聚合搜索 L2 端点（`POST /v1/messages/_search_global_messages`）。
 *
 * 契约与频道内 `_search_all` 完全对齐——响应体是 `{data: List<SearchAllHit>, pagination}`，
 * 其中 SearchAllHit 就是 [CombinedHit]（message 与 file 混排，按 result_type 判断）。
 *
 * L2 与 L1 是两条独立分页轴：L2 的 cursor 与 L1 无关，互不复用。
 * `X-Space-Id` / `token` 由 [com.chat.base.net.CommonRequestParamInterceptor] 自动注入。
 */
interface SearchGlobalMessagesService {
    @POST("messages/_search_global_messages")
    fun searchGlobalMessages(@Body body: JSONObject): Observable<CursorList<CombinedHit>>
}
