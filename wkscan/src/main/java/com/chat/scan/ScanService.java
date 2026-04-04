package com.chat.scan;

import com.alibaba.fastjson.JSONObject;
import com.chat.scan.entity.ScanResult;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * 2020-07-26 22:05
 * 扫描
 */
public interface ScanService {
    @GET
    Observable<ScanResult> getScanResult(@Url String url);

    @GET("groups/{groupNo}/scanjoin")
    Observable<ResponseBody> scanJoinGroup(
            @Path("groupNo") String groupNo,
            @Query("auth_code") String authCode
    );
}
