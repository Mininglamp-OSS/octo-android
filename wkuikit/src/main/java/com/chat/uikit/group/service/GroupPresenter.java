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

package com.chat.uikit.group.service;


import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;

import java.lang.ref.WeakReference;

/**
 * 2019-11-30 10:33
 * 群相关
 */
public class GroupPresenter implements GroupContract.GroupPresenter {

    private final WeakReference<GroupContract.GroupView> groupView;

    public GroupPresenter(GroupContract.GroupView _groupView) {
        groupView = new WeakReference<>(_groupView);
    }


    @Override
    public void getGroupInfo(String groupNo) {
        GroupModel.getInstance().getGroupInfo(groupNo, (code, msg, groupEntity) -> {
            if (code == HttpResponseCode.success) {
                if (groupView.get() != null) {
                    groupView.get().onGroupInfo(groupEntity);
                }
            } else WKToastUtils.getInstance().showToastNormal(msg);
        });
    }


    @Override
    public void updateGroupSetting(String groupNo, String key, int value) {
        GroupModel.getInstance().updateGroupSetting(groupNo, key, value, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                if (groupView.get() != null) groupView.get().onRefreshGroupSetting(key, value);
            } else WKToastUtils.getInstance().showToastNormal(msg);
        });
    }

    @Override
    public void getQrData(String groupNo) {
        GroupModel.getInstance().getGroupQr(groupNo, (code, msg, day, qrCode, expire, inviteUrl) -> {
            if (groupView.get() != null) {
                if (code == HttpResponseCode.success) {
                    groupView.get().setQrData(day, qrCode, expire, inviteUrl);
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            }
        });
    }

    @Override
    public void getMyGroups() {
        GroupModel.getInstance().getMyGroups((code, msg, list) -> {
            if (groupView.get() != null) {
                if (code == HttpResponseCode.success) {
                    groupView.get().setMyGroups(list);
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            }
        });
    }

    @Override
    public void showLoading() {

    }
}
