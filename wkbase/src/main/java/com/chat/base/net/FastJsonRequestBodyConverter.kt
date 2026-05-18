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

package com.chat.base.net
import com.alibaba.fastjson.JSON
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter

class FastJsonRequestBodyConverter<T> : Converter<T, RequestBody> {
    private val MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

    override fun convert(value: T): RequestBody {
        val jsonStr = JSON.toJSONString(value)
        return jsonStr.toRequestBody(contentType = MEDIA_TYPE)
    }
}