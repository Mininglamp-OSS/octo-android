package com.chat.uikit.search.service;

import com.chat.uikit.search.SearchUserEntity;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 2020-07-20 21:18
 * 搜索
 */
public interface SearchService {
    @GET("user/search")
    Observable<SearchUserEntity> searchUser(@Query("keyword") String keyword);
}
