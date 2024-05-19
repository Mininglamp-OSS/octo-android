package com.chat.uikit.user.service;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.enity.OnlineUserAndDevice;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.enity.UserQr;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-06-30 12:37
 * 用户
 */
public class UserModel extends WKBaseModel {
    private UserModel() {
    }

    private static class UserModelBinder {
        static final UserModel userModel = new UserModel();
    }

    public static UserModel getInstance() {
        return UserModelBinder.userModel;
    }

    public void updateUserInfo(String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(UserService.class).updateUserInfo(jsonObject), new IRequestResultListener<CommonResponse>() {
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

    public void updateUserSetting(String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(UserService.class).setting(jsonObject), new IRequestResultListener<CommonResponse>() {
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

    public void updateUserRemark(String uid, String remark, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uid", uid);
        jsonObject.put("remark", remark);
        request(createService(UserService.class).updateFriendRemark(jsonObject), new IRequestResultListener<CommonResponse>() {
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

    public void deleteUser(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).deleteFriend(uid), new IRequestResultListener<CommonResponse>() {
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

    public void addBlackList(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).addBlackList(uid), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKCommonModel.getInstance().getChannel(uid, WKChannelType.PERSONAL, null);
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void removeBlackList(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).removeBlackList(uid), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKCommonModel.getInstance().getChannel(uid, WKChannelType.PERSONAL, null);
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }


    public void uploadAvatar(String filePath, final IUploadBack iUploadBack) {
        String url = WKApiConfig.baseUrl + "users/" + WKConfig.getInstance().getUid() + "/avatar?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                iUploadBack.onResult(HttpResponseCode.success);
            }

            @Override
            public void onError() {
                iUploadBack.onResult(HttpResponseCode.error);
            }
        });
    }

    public interface IUploadBack {
        void onResult(int code);
    }

    public void getOnlineUsers(List<String> uids, @NonNull final IOnlineUser iOnlineUser) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uids);
        request(createService(UserService.class).getOnlineUsers(jsonArray), new IRequestResultListener<List<OnlineUser>>() {
            @Override
            public void onSuccess(List<OnlineUser> result) {
                iOnlineUser.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iOnlineUser.onResult(code, msg, null);
            }
        });
    }

    public interface IOnlineUser {
        void onResult(int code, String msg, List<OnlineUser> list);
    }

    public void getOnlineUsers() {
        request(createService(UserService.class).onlineUsers(), new IRequestResultListener<OnlineUserAndDevice>() {
            @Override
            public void onSuccess(OnlineUserAndDevice result) {
                int online = 0;
                int muteOfAPP = 0;
                if (result.pc != null) {
                    online = result.pc.online;
                    muteOfAPP = result.pc.mute_of_app;
                }
                WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_pc_online", online);
                WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_mute_of_app", muteOfAPP);
                List<WKChannel> tempList = WKIM.getInstance().getChannelManager().getWithFollowAndStatus(WKChannelType.PERSONAL, 1, 1);
                List<WKChannel> list = new ArrayList<>();
                if (WKReader.isNotEmpty(result.friends)) {
                    if (WKReader.isNotEmpty(tempList)) {
                        for (int i = 0, size = tempList.size(); i < size; i++) {
                            boolean isReset = true;
                            for (int j = 0, len = result.friends.size(); j < len; j++) {
                                if (result.friends.get(j).uid.equals(tempList.get(i).channelID)) {
                                    isReset = false;
                                    tempList.get(i).online = result.friends.get(j).online;
                                    tempList.get(i).lastOffline = result.friends.get(j).last_offline;
                                    break;
                                }
                            }
                            if (isReset) {
                                tempList.get(i).online = 0;
                                // tempList.get(i).lastOffline = 0;
                            }
                            list.add(tempList.get(i));
                        }

                        for (int i = 0, size = result.friends.size(); i < size; i++) {
                            boolean isAdd = true;
                            for (int j = 0, len = tempList.size(); j < len; j++) {
                                if (result.friends.get(i).uid.equals(tempList.get(j).channelID)) {
                                    isAdd = false;
                                    break;
                                }
                            }
                            if (isAdd) {
                                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(result.friends.get(i).uid, WKChannelType.PERSONAL);
                                if (channel != null) {
                                    channel.lastOffline = result.friends.get(i).last_offline;
                                    channel.online = result.friends.get(i).online;
                                    list.add(channel);
                                }
                            }
                        }
                    } else {
                        for (int i = 0, size = result.friends.size(); i < size; i++) {
                            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(result.friends.get(i).uid, WKChannelType.PERSONAL);
                            if (channel != null) {
                                channel.lastOffline = result.friends.get(i).last_offline;
                                channel.online = result.friends.get(i).online;
                                list.add(channel);
                            }
                        }
                    }
                } else {
                    for (int i = 0, size = tempList.size(); i < size; i++) {
                        if (tempList.get(i).online == 1 || tempList.get(i).lastOffline > 0) {
                            tempList.get(i).online = 0;
                            // tempList.get(i).lastOffline = 0;
                            list.add(tempList.get(i));
                        }
                    }
                }

                if (WKReader.isNotEmpty(result.friends)) {
                    if (WKReader.isNotEmpty(tempList)) {
                        for (int i = 0, size = tempList.size(); i < size; i++) {
                            for (int j = 0, len = result.friends.size(); j < len; j++) {
                                if (result.friends.get(j).uid.equals(tempList.get(i).channelID)) {
                                    tempList.get(i).online = result.friends.get(j).online;
                                    tempList.get(i).lastOffline = result.friends.get(j).last_offline;
                                    list.add(tempList.get(i));
                                    break;
                                }
                            }
                        }
                    }
                }
                WKIM.getInstance().getChannelManager().saveOrUpdateChannels(list);
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }


    public void userQr(final IUserQr iUserQr) {
        request(createService(UserService.class).userQr(), new IRequestResultListener<UserQr>() {
            @Override
            public void onSuccess(UserQr result) {
                iUserQr.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iUserQr.onResult(code, msg, null);
            }
        });
    }

    public interface IUserQr {
        void onResult(int code, String msg, UserQr userQr);
    }

    public void uploadContacts(List<MailListEntity> list, final ICommonListener iCommonListener) {
        JSONArray jsonArray = new JSONArray();
        for (MailListEntity entity : list) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", entity.name);
            jsonObject.put("zone", entity.zone);
            jsonObject.put("phone", entity.phone);
            jsonArray.add(jsonObject);
        }
        request(createService(UserService.class).uploadContacts(jsonArray), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(HttpResponseCode.success, "");
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void getContacts(final IGetContacts iGetContacts) {
        request(createService(UserService.class).getContacts(), new IRequestResultListener<List<MailListEntity>>() {
            @Override
            public void onSuccess(List<MailListEntity> result) {
                iGetContacts.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetContacts.onResult(code, msg, null);
            }
        });
    }

    public interface IGetContacts {
        void onResult(int code, String msg, List<MailListEntity> list);
    }

    public interface IUserInfo {
        void onResult(int code, String msg, UserInfo userInfo);
    }

    public void getUserInfo(String uid,String groupNo, IUserInfo iUserInfo) {
        request(createService(UserService.class).getUserInfo(uid,groupNo), new IRequestResultListener<UserInfo>() {
            @Override
            public void onSuccess(UserInfo result) {
                iUserInfo.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iUserInfo.onResult(code, msg, null);
            }
        });
    }
}
