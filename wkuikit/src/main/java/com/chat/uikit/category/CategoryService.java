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
