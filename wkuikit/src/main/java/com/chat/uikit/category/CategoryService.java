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

package com.chat.uikit.category;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface CategoryService {

    @GET("spaces/{spaceId}/categories")
    Observable<List<CategoryEntity>> list(@Path("spaceId") String spaceId);

    @POST("spaces/{spaceId}/categories")
    Observable<CategoryEntity> create(@Path("spaceId") String spaceId, @Body JSONObject body);

    @PUT("spaces/{spaceId}/categories/{categoryId}")
    Observable<CommonResponse> update(@Path("spaceId") String spaceId,
                                      @Path("categoryId") String categoryId, @Body JSONObject body);

    @DELETE("spaces/{spaceId}/categories/{categoryId}")
    Observable<CommonResponse> delete(@Path("spaceId") String spaceId,
                                      @Path("categoryId") String categoryId);

    @PUT("spaces/{spaceId}/categories/sort")
    Observable<CommonResponse> sort(@Path("spaceId") String spaceId, @Body JSONObject body);

    @PUT("groups/{groupNo}/category")
    Observable<CommonResponse> moveGroup(@Path("groupNo") String groupNo, @Body JSONObject body);
}
