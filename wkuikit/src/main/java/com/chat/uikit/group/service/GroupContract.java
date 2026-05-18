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

import com.chat.base.base.WKBasePresenter;
import com.chat.base.base.WKBaseView;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.uikit.group.GroupEntity;

import java.util.List;

/**
 * 2019-11-30 10:31
 * 群相关
 */
public class GroupContract {

    public interface GroupPresenter extends WKBasePresenter {

        void getGroupInfo(String groupNo);

        void updateGroupSetting(String groupNo, String key, int value);

        void getQrData(String groupNo);

        void getMyGroups();

    }

    public interface GroupView extends WKBaseView {

        void onGroupInfo(ChannelInfoEntity channelInfoEntity);

        void onRefreshGroupSetting(String key, int value);

        void setQrData(int day, String qrCode, String expire, String inviteUrl);

        void setMyGroups(List<GroupEntity> list);
    }
}
