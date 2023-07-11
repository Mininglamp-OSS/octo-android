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
