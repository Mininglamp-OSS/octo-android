package com.chat.uikit.contacts.service;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.ApplyDB;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.NewFriendEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.enity.UserInfo;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 2019-11-20 14:44
 * 好友管理
 */
public class FriendModel extends WKBaseModel {
    private FriendModel() {

    }

    private static class FriendModelBinder {
        private final static FriendModel friendModel = new FriendModel();
    }

    public static FriendModel getInstance() {
        return FriendModelBinder.friendModel;
    }

    /**
     * 申请加好友
     *
     * @param uid             用户ID
     * @param remark          申请备注
     * @param iCommonListener 返回
     */
    public void applyAddFriend(String uid, String vercode, String remark, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("to_uid", uid);
        jsonObject1.put("remark", remark);
        jsonObject1.put("vercode", vercode);
        request(createService(FriendService.class).applyAddFriend(jsonObject1), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 通过好友请求
     *
     * @param token           通过token
     * @param iCommonListener 返回
     */
    public void agreeFriendApply(String token, final ICommonListener iCommonListener) {

        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("token", token);
        request(createService(FriendService.class).agreeFriendApply(jsonObject1), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 同步好友
     */
    public void syncFriends(final ICommonListener iCommonListener) {
        String key = String.format("%s_friend_sync_version", WKConfig.getInstance().getUid());
        long version = WKSharedPreferencesUtil.getInstance().getLong(key);
        request(createService(FriendService.class).syncFriends(version, 5000, 1), new IRequestResultListener<List<UserInfo>>() {
            @Override
            public void onSuccess(List<UserInfo> list) {
                if (WKReader.isNotEmpty(list)) {
                    long tempVersion = 0;
                    List<WKChannel> channels = new ArrayList<>();
                    for (int i = 0, size = list.size(); i < size; i++) {
                        WKChannel channel = new WKChannel();
                        channel.channelID = list.get(i).uid;
                        channel.channelType = WKChannelType.PERSONAL;//指定为单聊信息
                        channel.channelRemark = list.get(i).remark;
                        channel.channelName = list.get(i).name;
                        channel.mute = list.get(i).mute;
                        channel.top = list.get(i).top;
                        channel.version = list.get(i).version;
                        channel.status = list.get(i).status;
                        channel.isDeleted = list.get(i).is_deleted;
                        channel.updatedAt = list.get(i).updated_at;
                        channel.createdAt = list.get(i).created_at;
                        channel.receipt = list.get(i).receipt;
                        channel.robot = list.get(i).robot;
                        channel.category = list.get(i).category;
                        channel.follow = 1;//指定为好友
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put(WKChannelExtras.revokeRemind, list.get(i).revoke_remind);
                        hashMap.put(WKChannelExtras.screenshot, list.get(i).screenshot);
                        hashMap.put(WKChannelExtras.sourceDesc, list.get(i).source_desc);
                        hashMap.put(WKChannelExtras.chatPwdOn, list.get(i).chat_pwd_on);
                        hashMap.put(WKChannelExtras.vercode, list.get(i).vercode);
                        channel.remoteExtraMap = hashMap;
                        channels.add(channel);
                        if (list.get(i).version > tempVersion) {
                            tempVersion = list.get(i).version;
                        }
                    }
                    WKSharedPreferencesUtil.getInstance().putLong(key, tempVersion);
                    //将好友信息设置到sdk
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannels(channels);
                    EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
                }
                if (iCommonListener != null) {
                    iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) {
                    iCommonListener.onResult(code, msg);
                }
            }
        });
    }

    /**
     * 保存新朋友申请消息
     *
     * @param contentJson 消息json
     */
    public void saveNewFriendsMsg(String contentJson) {
        if (TextUtils.isEmpty(contentJson)) return;

        NewFriendEntity newFriendEntity = JSONObject.parseObject(contentJson, NewFriendEntity.class);
        NewFriendEntity newFriendEntity1 = ApplyDB.getInstance().query(newFriendEntity.apply_uid);
        if (newFriendEntity1 != null && !TextUtils.isEmpty(newFriendEntity1.apply_uid)) {
            newFriendEntity1.status = 0;
            newFriendEntity1.token = newFriendEntity.token;
            newFriendEntity1.remark = newFriendEntity.remark;
            newFriendEntity1.created_at = WKTimeUtils.getInstance().getNowDate1();
            ApplyDB.getInstance().update(newFriendEntity1);
        } else {
            newFriendEntity.created_at = WKTimeUtils.getInstance().getNowDate1();
            ApplyDB.getInstance().insert(newFriendEntity);
        }
        int new_friend_count = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
        new_friend_count++;
        WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_new_friend_count", new_friend_count);
        EndpointManager.getInstance().invokes(EndpointCategory.wkRefreshMailList, null);
    }

    /**
     * 修改用户设置
     *
     * @param uid             用户ID
     * @param key             修改字段
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateUserSetting(String uid, String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(FriendService.class).updateUserSetting(uid, jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });

    }
}
