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

import com.chat.base.base.WKBasePresenter;
import com.chat.base.base.WKBaseView;
import com.chat.uikit.search.SearchUserEntity;

/**
 * 2019-11-20 14:11
 * 搜索
 */
public class SearchContract {
    public interface SearchUserPresenter extends WKBasePresenter {
        void searchUser(String keyword);
    }

    public interface SearchUserView extends WKBaseView {
        void setSearchUser(SearchUserEntity searchUser);
    }
}
