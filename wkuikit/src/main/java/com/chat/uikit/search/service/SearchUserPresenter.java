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


import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;

import java.lang.ref.WeakReference;

/**
 * 2019-11-20 14:13
 */
public class SearchUserPresenter implements SearchContract.SearchUserPresenter {
    private final WeakReference<SearchContract.SearchUserView> userViewWeakReference;

    public SearchUserPresenter(SearchContract.SearchUserView searchUserView) {
        userViewWeakReference = new WeakReference<>(searchUserView);
    }

    @Override
    public void searchUser(String keyword) {
        SearchModel.getInstance().searchUser(keyword, (code, msg, searchUserEntity) -> {
            if (code == HttpResponseCode.success) {
                if (userViewWeakReference.get() != null)
                    userViewWeakReference.get().setSearchUser(searchUserEntity);
            } else WKToastUtils.getInstance().showToastFail(msg);
        });
    }

    @Override
    public void showLoading() {

    }
}
