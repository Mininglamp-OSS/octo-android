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

import com.alibaba.fastjson.JSONObject
import com.chat.base.search.channel.dto.AroundResult
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.dto.MediaHit
import com.chat.base.search.channel.dto.MessageHit
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 频道内搜索（OpenSearch 直读）。5 个端点路径均相对于 `<baseUrl>/v1/`。
 *
 * `X-Space-Id` 由 [com.chat.base.net.CommonRequestParamInterceptor] 自动注入，
 * 单聊（channel_type=1）必带。
 */
interface ChannelSearchService {
    @POST("messages/_search")
    fun searchMessages(@Body body: JSONObject): Observable<CursorList<MessageHit>>

    @POST("messages/_search_all")
    fun searchAll(@Body body: JSONObject): Observable<CursorList<CombinedHit>>

    @POST("messages/_search_media")
    fun searchMedia(@Body body: JSONObject): Observable<CursorList<MediaHit>>

    @POST("messages/_search_files")
    fun searchFiles(@Body body: JSONObject): Observable<CursorList<FileHit>>

    @POST("messages/_search_around")
    fun searchAround(@Body body: JSONObject): Observable<AroundResult>
}
