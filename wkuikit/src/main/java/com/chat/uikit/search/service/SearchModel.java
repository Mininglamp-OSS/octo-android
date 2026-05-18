/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.search.service;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.uikit.message.MsgModel;
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
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        request(createService(SearchService.class).searchUser(keyword, TextUtils.isEmpty(spaceId) ? null : spaceId), new IRequestResultListener<SearchUserEntity>() {
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
