package com.chat.uikit.thread.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.uikit.group.service.entity.GroupMdEntity;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.chat.uikit.thread.service.entity.ThreadMember;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ThreadService {

    @GET("groups/{groupNo}/threads")
    Observable<List<ThreadEntity>> listThreads(@Path("groupNo") String groupNo, @Query("page") int page, @Query("limit") int limit);

    @POST("groups/{groupNo}/threads")
    Observable<ThreadEntity> createThread(@Path("groupNo") String groupNo, @Body JSONObject body);

    @GET("groups/{groupNo}/threads/{shortId}")
    Observable<ThreadEntity> getThreadDetail(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @POST("groups/{groupNo}/threads/{shortId}/archive")
    Observable<CommonResponse> archiveThread(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @POST("groups/{groupNo}/threads/{shortId}/unarchive")
    Observable<CommonResponse> unarchiveThread(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @HTTP(method = "DELETE", path = "groups/{groupNo}/threads/{shortId}", hasBody = false)
    Observable<CommonResponse> deleteThread(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @POST("groups/{groupNo}/threads/{shortId}/join")
    Observable<CommonResponse> joinThread(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @POST("groups/{groupNo}/threads/{shortId}/leave")
    Observable<CommonResponse> leaveThread(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @GET("groups/{groupNo}/threads/{shortId}/members")
    Observable<List<ThreadMember>> getThreadMembers(@Path("groupNo") String groupNo, @Path("shortId") String shortId, @Query("keyword") String keyword, @Query("page") int page, @Query("limit") int limit);

    @GET("groups/{groupNo}/threads/{shortId}/md")
    Observable<GroupMdEntity> getThreadMd(@Path("groupNo") String groupNo, @Path("shortId") String shortId);

    @PUT("groups/{groupNo}/threads/{shortId}/md")
    Observable<CommonResponse> updateThreadMd(@Path("groupNo") String groupNo, @Path("shortId") String shortId, @Body JSONObject body);

    @GET("thread/channel/{channelID}/membersync")
    Observable<List<ThreadMember>> syncThreadMembers(@Path("channelID") String channelID, @Query("version") long version, @Query("limit") int limit);
}
