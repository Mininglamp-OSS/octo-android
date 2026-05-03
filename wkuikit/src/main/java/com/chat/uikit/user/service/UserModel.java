package com.chat.uikit.user.service;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.enity.Device;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.enity.OnlineUserAndDevice;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.enity.UserQr;
import com.chat.uikit.group.service.entity.GroupMember;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.HashMap;
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
        uploadAvatar(WKConfig.getInstance().getUid(), filePath, iUploadBack);
    }

    /**
     * YUJ-238 (对齐 web PR#1092 BotDetailModal)：bot 创建者为自己管理的
     * bot 更新头像时需要显式指定 {@code targetUid}，而不是沿用老实现里
     * 写死的 {@code WKConfig.getUid()}。后端 {@code POST /users/:uid/avatar}
     * 内置 {@code creator_uid} 校验，非 owner 请求会 403 —— 这里只负责把
     * 目标 uid 拼进 URL，权限门槛由服务端兜底。
     *
     * @param uid      被上传头像的目标用户（登录者自己或本人管理的 bot）
     * @param filePath 本地图片路径
     */
    public void uploadAvatar(String uid, String filePath, final IUploadBack iUploadBack) {
        String url = WKApiConfig.baseUrl + "users/" + uid + "/avatar?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
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

    /**
     * YUJ-238 (对齐 web PR#1092 BotDetailModal handleSaveDescription)：
     * bot 创建者更新 bot 简介。后端 {@code PUT /robot/:uid/description}
     * 已内置 creator_uid 校验，非 owner 直接 403。
     */
    public void updateBotDescription(String uid, String description, final ICommonListener iCommonListener) {
        JSONObject body = new JSONObject();
        body.put("description", description == null ? "" : description);
        request(createService(UserService.class).updateBotDescription(uid, body), new IRequestResultListener<CommonResponse>() {
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

    public void getUserInfo(String uid, String groupNo, IUserInfo iUserInfo) {
        request(createService(UserService.class).getUserInfo(uid, groupNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(UserInfo result) {
                if (result.group_member != null) {
                    WKChannelMember member = buildMemberFromUserInfo(result.group_member);
                    WKIM.getInstance().getChannelMembersManager().save(member);
                }
                if (iUserInfo != null) {
                    iUserInfo.onResult(HttpResponseCode.success, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iUserInfo != null) {
                    iUserInfo.onResult(code, msg, null);
                }
            }
        });
    }

    /**
     * 从 {@code /users/{uid}?group_no=} 响应里的 {@link GroupMember} 构造一个
     * 可写入 WKIM 本地缓存的 {@link WKChannelMember}，<b>关键：把外部群相关字段
     * 完整透传到 {@code extraMap}</b>。
     *
     * <p>bug（YUJ-178 / dmwork-android#134 问题 2）：历史实现漏掉 extraMap 赋值，
     * 导致随后 {@code ChannelMembersManager.save(member)} 走 INSERT OR REPLACE 路径时，
     * 目标 row 的 {@code extra} 列被清空。再次打开成员列表读本地 DB 时就失去
     * {@code home_space_id / source_space_name} 等字段，{@code @SpaceName} 后缀丢失。
     *
     * <p>透传字段清单与 {@code GroupModel.serialize}（YUJ-86 EP1）保持一致，集中在这里
     * 有两个好处：
     * <ol>
     *     <li>避免 {@code GroupModel.serialize} / {@code UserModel.getUserInfo} 双路径漂移；</li>
     *     <li>纯 Java null/empty 判空以便 JVM 单测，不依赖 Android framework。</li>
     * </ol>
     *
     * @param gm 后端 {@code group_member} 节点，允许为 {@code null}（调用方应提前判空）
     * @return 新建的 {@link WKChannelMember}；外部群字段全部非空时会写入 {@code extraMap}
     */
    @VisibleForTesting
    @NonNull
    static WKChannelMember buildMemberFromUserInfo(@NonNull GroupMember gm) {
        WKChannelMember member = new WKChannelMember();
        member.memberUID = gm.uid;
        member.memberRemark = gm.remark;
        member.memberName = gm.name;
        member.channelID = gm.group_no;
        member.channelType = WKChannelType.GROUP;
        member.isDeleted = gm.is_deleted;
        member.version = gm.version;
        member.role = gm.role;
        member.status = gm.status;
        member.memberInviteUID = gm.invite_uid;
        member.robot = gm.robot;
        member.forbiddenExpirationTime = gm.forbidden_expir_time;
        if (member.robot == 1 && !isNullOrEmpty(gm.username)) {
            member.memberName = gm.username;
        }
        member.updatedAt = gm.updated_at;
        member.createdAt = gm.created_at;
        // 外部群字段写入 extraMap（YUJ-178 修复点，对齐 GroupModel.serialize 的清单）。
        // 注：这里用纯 Java null+empty 判空而不是 TextUtils.isEmpty — 解析层需在 JVM
        // 单测里跑，而 host JVM 的 android.jar stub 对 TextUtils.isEmpty 返回默认 false。
        HashMap<String, Object> extras = new HashMap<>();
        if (!isNullOrEmpty(gm.vercode)) {
            extras.put(WKChannelMemberExtras.WKCode, gm.vercode);
        }
        extras.put(WKChannelMemberExtras.isExternal, gm.is_external);
        if (!isNullOrEmpty(gm.source_space_id)) {
            extras.put(WKChannelMemberExtras.sourceSpaceID, gm.source_space_id);
        }
        if (!isNullOrEmpty(gm.source_space_name)) {
            extras.put(WKChannelMemberExtras.sourceSpaceName, gm.source_space_name);
        }
        if (!isNullOrEmpty(gm.home_space_id)) {
            extras.put(WKChannelMemberExtras.homeSpaceID, gm.home_space_id);
        }
        if (!isNullOrEmpty(gm.home_space_name)) {
            extras.put(WKChannelMemberExtras.homeSpaceName, gm.home_space_name);
        }
        member.extraMap = extras;
        return member;
    }

    private static boolean isNullOrEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    public void quit(ICommonListener iCommonListener) {
        // 不走 BaseObserver（避免全局 401 拦截弹出"认证失败"），直接订阅并忽略错误
        createService(UserService.class).quit()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            if (iCommonListener != null)
                                iCommonListener.onResult(HttpResponseCode.success, "");
                        },
                        throwable -> {
                            // 退出时任何错误（含 401）都视为正常，静默处理
                            if (iCommonListener != null)
                                iCommonListener.onResult(HttpResponseCode.success, "");
                        }
                );
    }

    public void device(){
        String deviceId = WKConstants.getDeviceID();
        request(createService(UserService.class).device(deviceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Device result) {
                if (result != null) {
                    WKIM.getInstance().setDeviceId(String.valueOf(result.id));
                }
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }

    public interface IDestroyStatusListener {
        void onResult(int status, int remainingDays, String expireAt);
        void onError(int code, String msg);
    }

    public void getDestroyStatus(IDestroyStatusListener listener) {
        request(createService(UserService.class).getDestroyStatus(), new IRequestResultListener<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                int status = result.getIntValue("destroy_status");
                int days = result.getIntValue("remaining_days");
                String expireAt = result.getString("expire_at");
                listener.onResult(status, days, expireAt != null ? expireAt : "");
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void applyDestroy(String password, IDestroyStatusListener listener) {
        JSONObject body = new JSONObject();
        body.put("password", password);
        request(createService(UserService.class).applyDestroy(body), new IRequestResultListener<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                int status = result.getIntValue("destroy_status");
                int days = result.getIntValue("remaining_days");
                String expireAt = result.getString("expire_at");
                listener.onResult(
                        status > 0 ? status : 1,
                        days > 0 ? days : 7,
                        expireAt != null ? expireAt : ""
                );
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void cancelDestroy(ICommonListener listener) {
        request(createService(UserService.class).cancelDestroy(), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }
}
