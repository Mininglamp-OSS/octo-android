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