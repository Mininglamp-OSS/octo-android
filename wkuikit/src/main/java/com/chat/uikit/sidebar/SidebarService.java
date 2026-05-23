package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SidebarService {

    @POST("sidebar/sync")
    Observable<JSONObject> sync(@Body JSONObject body);
}
