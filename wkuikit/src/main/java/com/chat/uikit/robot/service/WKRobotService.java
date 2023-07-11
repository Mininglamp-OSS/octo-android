package com.chat.uikit.robot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.uikit.robot.entity.WKRobotInlineQueryResult;
import com.chat.uikit.robot.entity.WKSyncRobotEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WKRobotService {
    @POST("robot/sync")
    Observable<List<WKSyncRobotEntity>> syncRobot(@Body JSONArray jsonArray);

    @POST("robot/inline_query")
    Observable<WKRobotInlineQueryResult> inlineQuery(@Body JSONObject jsonObject);
}
