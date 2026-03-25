package com.chat.base.search

import com.alibaba.fastjson.JSONObject
import com.chat.base.entity.GlobalSearch
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface IService {
    @POST("search/global")
    fun search(@Body json: JSONObject, @Query("space_id") spaceId: String?): Observable<GlobalSearch>
}