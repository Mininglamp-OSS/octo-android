package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface FollowService {

    @POST("follow/dm")
    Observable<CommonResponse> followDM(@Body JSONObject body);

    @HTTP(method = "DELETE", path = "follow/dm", hasBody = false)
    Observable<CommonResponse> unfollowDM(@Query("peer_uid") String peerUid);

    @POST("follow/channel/refollow")
    Observable<CommonResponse> refollowChannel(@Body JSONObject body);

    @POST("follow/channel/unfollow")
    Observable<CommonResponse> unfollowChannel(@Body JSONObject body);

    @POST("follow/thread")
    Observable<CommonResponse> followThread(@Body JSONObject body);

    @HTTP(method = "DELETE", path = "follow/thread", hasBody = false)
    Observable<CommonResponse> unfollowThread(@Query("thread_channel_id") String threadChannelId);

    @PUT("follow/sort")
    Observable<CommonResponse> sort(@Body JSONObject body);
}
