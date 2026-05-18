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

package com.chat.base.app

import com.chat.base.entity.AppInfo
import com.chat.base.entity.AuthInfo
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface IAppService {
    @GET("apps/{app_id}")
    fun getAPPInfo(@Path("app_id") appID: String): Observable<AppInfo>

    @GET("openapi/authcode")
    fun getAuthCode(@Query("app_id")appID:String):Observable<AuthInfo>
}