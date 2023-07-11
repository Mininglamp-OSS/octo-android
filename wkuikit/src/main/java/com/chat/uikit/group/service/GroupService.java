package com.chat.uikit.group.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.uikit.group.GroupEntity;
import com.chat.uikit.group.service.entity.GroupMember;
import com.chat.uikit.group.service.entity.GroupQr;
import com.chat.uikit.group.service.entity.H5ConfirmUrl;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 2020-07-20 21:22
 * 群相关
 */
public interface GroupService {
    @POST("group/create")
    Observable<GroupEntity> createGroup(@Body JSONObject jsonObject);

    @POST("groups/{groupNo}/members")
    Observable<CommonResponse> addGroupMembers(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);

    @POST("groups/{groupNo}/member/invite")
    Observable<CommonResponse> inviteGroupMembers(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);

    @GET("groups/{groupNo}/member/h5confirm")
    Observable<H5ConfirmUrl> getH5confirmUrl(@Path("groupNo") String groupNo, @Query("invite_no") String invite_no);

    @GET("groups/{groupNo}")
    Observable<GroupEntity> getGroupInfo(@Path("groupNo") String groupNo);

    @GET("groups/{groupNo}/membersync")
    Observable<List<GroupMember>> syncGroupMembers(@Path("groupNo") String groupNo, @Query("limit") int limit, @Query("version") long version);

    @PUT("groups/{groupNo}/setting")
    Observable<CommonResponse> updateGroupSetting(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);

    @PUT("groups/{groupNo}")
    Observable<CommonResponse> updateGroupInfo(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);

    @HTTP(method = "DELETE", path = "groups/{groupNo}/members", hasBody = true)
    Observable<CommonResponse> deleteGroupMembers(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);

    @PUT("groups/{groupNo}/members/{uid}")
    Observable<CommonResponse> updateGroupMemberInfo(@Path("groupNo") String groupNo, @Path("uid") String uid, @Body JSONObject jsonObject);

    @GET("groups/{groupNo}/qrcode")
    Observable<GroupQr> getGroupQr(@Path("groupNo") String groupNo);

    @GET("group/my")
    Observable<List<GroupEntity>> getMyGroups();

    @POST("groups/{group_no}/exit")
    Observable<CommonResponse> exitGroup(@Path("group_no") String group_no);

    @GET("groups/{group_no}/members")
    Observable<List<GroupMember>> groupMembers(@Path("group_no") String groupNO, @Query("keyword") String keyword, @Query("page") int page, @Query("limit") int limit);
}
