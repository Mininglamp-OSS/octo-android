package com.chat.uikit.search.service;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.uikit.search.SearchUserEntity;

/**
 * 2019-11-20 14:08
 * 搜索
 */
public class SearchModel extends WKBaseModel {
    private SearchModel() {
    }

    private static class SearchModelBinder {
        private static final SearchModel searchModel = new SearchModel();
    }

    public static SearchModel getInstance() {
        return SearchModelBinder.searchModel;
    }

    /**
     * 搜索
     *
     * @param keyword
     * @param isearchLisenter
     */
    public void searchUser(String keyword, final IsearchLisenter isearchLisenter) {
        request(createService(SearchService.class).searchUser(keyword), new IRequestResultListener<SearchUserEntity>() {
            @Override
            public void onSuccess(SearchUserEntity result) {
                isearchLisenter.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                isearchLisenter.onResult(code, msg, null);
            }
        });
    }

    public interface IsearchLisenter {
        void onResult(int code, String msg, SearchUserEntity searchUserEntity);
    }
}
