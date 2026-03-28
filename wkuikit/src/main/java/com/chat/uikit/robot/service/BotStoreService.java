package com.chat.uikit.robot.service;

import com.chat.uikit.robot.entity.BotStoreEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface BotStoreService {
    @GET("robot/space_bots")
    Observable<List<BotStoreEntity>> getSpaceBots(@Query("space_id") String spaceId);
}
