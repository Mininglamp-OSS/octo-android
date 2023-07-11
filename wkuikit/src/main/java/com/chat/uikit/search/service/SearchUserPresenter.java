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
