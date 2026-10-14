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
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 全局文件搜索端点（`POST /v1/messages/_search_global_files`）。
 *
 * 服务端是否已上线此端点待确认（见 [SearchGlobalFilesReq] KDoc）；响应体假定与
 * L2 一致：`{data: List<FileHit>, pagination}`。
 *
 * `X-Space-Id` / `token` 由 [com.chat.base.net.CommonRequestParamInterceptor] 自动注入。
 */
interface SearchGlobalFilesService {
    @POST("messages/_search_global_files")
    fun searchGlobalFiles(@Body body: JSONObject): Observable<CursorList<FileHit>>
}
