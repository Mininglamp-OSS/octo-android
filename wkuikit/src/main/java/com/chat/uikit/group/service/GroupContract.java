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

        void setQrData(int day, String qrCode, String expire);

        void setMyGroups(List<GroupEntity> list);
    }
}
