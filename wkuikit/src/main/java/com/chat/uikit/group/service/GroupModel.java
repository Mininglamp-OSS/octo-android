package com.chat.uikit.group.service;

import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.group.GroupEntity;
import com.chat.uikit.group.service.entity.GroupMdEntity;
import com.chat.uikit.group.service.entity.GroupMember;
import com.chat.uikit.group.service.entity.GroupQr;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.interfaces.IChannelMemberListResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 2019-11-30 10:25
 * 群相关处理
 */
public class GroupModel extends WKBaseModel {

    private GroupModel() {
    }

    private static class GroupModelBinder {
        private final static GroupModel groupModel = new GroupModel();
    }

    public static GroupModel getInstance() {
        return GroupModelBinder.groupModel;
    }

    /**
     * 创建群组
     *
     * @param name 群名
     * @param ids  成员
     */
    public void createGroup(String name, List<String> ids, List<String> names, final IGroupInfo iGroupInfo) {
        createGroup(name, ids, names, null, iGroupInfo);
    }

    public void createGroup(String name, List<String> ids, List<String> names, String categoryId, final IGroupInfo iGroupInfo) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", name);
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject.put("members", jsonArray);
        JSONArray jsonArray1 = new JSONArray();
        jsonArray1.addAll(names);
        jsonObject.put("member_names", jsonArray1);
        jsonObject.put("msg_auto_delete", WKConfig.getInstance().getUserInfo().msg_expire_second);
        // Space 模式下传递 space_id，让服务端允许非好友成员建群
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId)) {
            jsonObject.put("space_id", spaceId);
        }
        if (!TextUtils.isEmpty(categoryId)) {
            jsonObject.put("category_id", categoryId);
        }
        request(createService(GroupService.class).createGroup(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(GroupEntity groupEntity) {
                WKChannel channel = new WKChannel();
                channel.channelID = groupEntity.group_no;
                channel.channelType = WKChannelType.GROUP;
                channel.channelName = groupEntity.name;
                // 保存 space_id 到 remoteExtraMap，供 Space 过滤使用
                if (!TextUtils.isEmpty(groupEntity.space_id)) {
                    channel.remoteExtraMap = new HashMap<>();
                    channel.remoteExtraMap.put("space_id", groupEntity.space_id);
                }
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                iGroupInfo.onResult(HttpResponseCode.success, "", groupEntity);
            }

            @Override
            public void onFail(int code, String msg) {
                iGroupInfo.onResult(code, msg, null);
            }
        });
    }

    public interface IGroupInfo {
        void onResult(int code, String msg, GroupEntity groupEntity);
    }

    /**
     * 添加群成员
     *
     * @param groupNo 群号
     * @param ids     成员
     */
    public void addGroupMembers(String groupNo, List<String> ids, List<String> names, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject.put("members", jsonArray);
        JSONArray nameArr = new JSONArray();
        nameArr.addAll(names);
        jsonObject.put("names", nameArr);
        // Space 模式下传递 space_id，让服务端允许非好友成员加群
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId)) {
            jsonObject.put("space_id", spaceId);
        }
        request(createService(GroupService.class).addGroupMembers(groupNo, jsonObject), new IRequestResultListener<>() {
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
     * 邀请加入群聊
     *
     * @param groupNo         群编号
     * @param ids             用户id
     * @param iCommonListener 返回
     */
    public void inviteGroupMembers(String groupNo, List<String> ids, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject1.put("uids", jsonArray);
        jsonObject1.put("remark", "");
        // Space 模式下传递 space_id，让服务端允许非好友成员邀请入群
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId)) {
            jsonObject1.put("space_id", spaceId);
        }
        request(createService(GroupService.class).inviteGroupMembers(groupNo, jsonObject1), new IRequestResultListener<>() {
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
     * 获取群详情
     *
     * @param groupNo     群编号
     * @param iGetChannel 返回
     */
    public void getGroupInfo(String groupNo, final WKCommonModel.IGetChannel iGetChannel) {
        WKCommonModel.getInstance().getChannel(groupNo, WKChannelType.GROUP, (code, msg, entity) -> {
            if (iGetChannel != null) {
                iGetChannel.onResult(code, msg, entity);
            }
        });
    }


    public void getChannelMembers(String groupNO, String keyword, int page, int limit, IChannelMemberListResult iChannelMemberListResult) {
        request(createService(GroupService.class).groupMembers(groupNO, keyword, page, limit), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupMember> result) {
                List<WKChannelMember> list = serialize(result);
                iChannelMemberListResult.onResult(list);
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    /**
     * 同步群成员
     *
     * @param groupNo 群编号
     */
    public synchronized void groupMembersSync(String groupNo, final ICommonListener iCommonListener) {
        long version = WKIM.getInstance().getChannelMembersManager().getMaxVersion(groupNo, WKChannelType.GROUP);
        request(createService(GroupService.class).syncGroupMembers(groupNo, 1000, version), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupMember> list) {
                if (WKReader.isNotEmpty(list)) {
                    List<WKChannelMember> members = serialize(list);
                    WKIM.getInstance().getChannelMembersManager().save(members);
                    AndroidUtilities.runOnUIThread(() -> groupMembersSync(groupNo, iCommonListener), 500);
                } else {
                    if (iCommonListener != null)
                        iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });

    }

    // 注：package-private + static，便于单元测试直接调用。
    // 历史上此方法是 `private`，YUJ-86 EP1 的"强制单元测试覆盖解析层字段非空透传"
    // 需要在不实例化 GroupModel（会触发 HTTP 客户端初始化）的情况下验证外部群
    // 字段透传。逻辑本身是纯数据转换，没有 this 依赖，提升为 static 不改变行为。
    // @VisibleForTesting 显式声明"本意应是 private"，保留 IDE/Lint 提示。
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static List<WKChannelMember> serialize(List<GroupMember> list) {
        List<WKChannelMember> members = new ArrayList<>();
        if (WKReader.isEmpty(list)) {
            return members;
        }
        for (int i = 0, size = list.size(); i < size; i++) {
            WKChannelMember member = new WKChannelMember();
            member.memberUID = list.get(i).uid;
            member.memberRemark = list.get(i).remark;
            member.memberName = list.get(i).name;
            member.channelID = list.get(i).group_no;
            member.channelType = WKChannelType.GROUP;
            member.isDeleted = list.get(i).is_deleted;
            member.version = list.get(i).version;
            member.role = list.get(i).role;
            member.status = list.get(i).status;
            member.memberInviteUID = list.get(i).invite_uid;
            member.robot = list.get(i).robot;
            member.forbiddenExpirationTime = list.get(i).forbidden_expir_time;
            if (member.robot == 1 && !TextUtils.isEmpty(list.get(i).username)) {
                member.memberName = list.get(i).username;
            }
            member.updatedAt = list.get(i).updated_at;
            member.createdAt = list.get(i).created_at;
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put(WKChannelMemberExtras.WKCode, list.get(i).vercode);
            // 外部成员标识：来自其他 Space 的成员（后端 PR #1167-#1169 提供的字段）。
            // 注：这里用纯 Java null+empty 判空，而不是 TextUtils.isEmpty。
            // 原因：serialize 是解析层核心，需在 JVM 单测里直接跑，而 host JVM
            // 的 android.jar stub 对 TextUtils.isEmpty 返回默认 false，会把
            // null/"" 当成非空，反而把空字段塞进 extraMap。参见 YUJ-86 EP1
            // codex review P1。
            hashMap.put(WKChannelMemberExtras.isExternal, list.get(i).is_external);
            String sourceSpaceId = list.get(i).source_space_id;
            if (sourceSpaceId != null && !sourceSpaceId.isEmpty()) {
                hashMap.put(WKChannelMemberExtras.sourceSpaceID, sourceSpaceId);
            }
            String sourceSpaceName = list.get(i).source_space_name;
            if (sourceSpaceName != null && !sourceSpaceName.isEmpty()) {
                hashMap.put(WKChannelMemberExtras.sourceSpaceName, sourceSpaceName);
            }
            // Home Space（YUJ-63 / web #997）— viewer-relative 外部判定字段。
            // 与 source_space_* 不同：home_space_* 是成员归属的 Space，客户端用它
            // 跟当前 viewer 的 Space 比较来判断是否外部，不完全信任后端 is_external。
            String homeSpaceId = list.get(i).home_space_id;
            if (homeSpaceId != null && !homeSpaceId.isEmpty()) {
                hashMap.put(WKChannelMemberExtras.homeSpaceID, homeSpaceId);
            }
            String homeSpaceName = list.get(i).home_space_name;
            if (homeSpaceName != null && !homeSpaceName.isEmpty()) {
                hashMap.put(WKChannelMemberExtras.homeSpaceName, homeSpaceName);
            }
            member.extraMap = hashMap;
            members.add(member);
        }
        return members;
    }

    /**
     * 修改群设置
     *
     * @param groupNo         群编号
     * @param key             修改字段
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupSetting(String groupNo, String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupSetting(groupNo, jsonObject), new IRequestResultListener<>() {
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

    public void updateGroupSetting(String groupNo, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupSetting(groupNo, jsonObject), new IRequestResultListener<>() {
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
     * 修改群信息
     *
     * @param groupNo         群编号
     * @param key             修改字段
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupInfo(String groupNo, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupInfo(groupNo, jsonObject), new IRequestResultListener<>() {
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

    public void uploadGroupAvatar(String groupNo, String filePath, final ICommonListener listener) {
        String url = WKApiConfig.baseUrl + "groups/" + groupNo + "/avatar?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                listener.onResult(HttpResponseCode.success, "");
            }

            @Override
            public void onError() {
                listener.onResult(HttpResponseCode.error, "upload failed");
            }
        });
    }

    /**
     * 删除群成员
     *
     * @param groupNo         群编号
     * @param uidList         用户ID
     * @param iCommonListener 返回
     */
    public void deleteGroupMembers(String groupNo, List<String> uidList, List<String> names, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        jsonObject.put("members", jsonArray);
        JSONArray nameArr = new JSONArray();
        nameArr.addAll(names);
        jsonObject.put("names", nameArr);
        request(createService(GroupService.class).deleteGroupMembers(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                List<WKChannelMember> list = new ArrayList<>();
                for (int i = 0, size = uidList.size(); i < size; i++) {
                    WKChannelMember member = new WKChannelMember();
                    member.isDeleted = 1;
                    member.channelID = groupNo;
                    member.channelType = WKChannelType.GROUP;
                    member.memberUID = uidList.get(i);
                    list.add(member);
                }
                WKIM.getInstance().getChannelMembersManager().delete(list);
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 修改群成员信息
     *
     * @param groupNo         群号
     * @param uid             用户ID
     * @param key             主键
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupMemberInfo(String groupNo, String uid, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put(key, value);
        request(createService(GroupService.class).updateGroupMemberInfo(groupNo, uid, jsonObject1), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (key.equalsIgnoreCase("remark")) {
                    //sdk层数据库修改
                    WKIM.getInstance().getChannelMembersManager().updateRemarkName(groupNo, WKChannelType.GROUP, uid, value);
                }
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 群二维码
     *
     * @param groupID  群号
     * @param iGroupQr 返回
     */
    void getGroupQr(String groupID, final IGroupQr iGroupQr) {
        request(createService(GroupService.class).getGroupQr(groupID), new IRequestResultListener<>() {
            @Override
            public void onSuccess(GroupQr result) {
                iGroupQr.onResult(HttpResponseCode.success, "", result.day, result.qrcode, result.expire);
            }

            @Override
            public void onFail(int code, String msg) {
                iGroupQr.onResult(code, msg, 0, "", "");
            }
        });

    }

    public interface IGroupQr {
        void onResult(int code, String msg, int day, String qrCode, String expire);
    }

    /**
     * 我保存的群聊
     *
     * @param iGetMyGroups 返回
     */
    void getMyGroups(final IGetMyGroups iGetMyGroups) {
        request(createService(GroupService.class).getMyGroups(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupEntity> result) {
                iGetMyGroups.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetMyGroups.onResult(code, msg, null);
            }
        });
    }

    public interface IGetMyGroups {
        void onResult(int code, String msg, List<GroupEntity> list);
    }

    public void exitGroup(String groupNo, final ICommonListener iCommonListener) {
        request(createService(GroupService.class).exitGroup(groupNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(HttpResponseCode.success, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public interface IGroupMdListener {
        void onResult(int code, String msg, GroupMdEntity entity);
    }

    public void getGroupMd(String groupNo, IGroupMdListener listener) {
        request(createService(GroupService.class).getGroupMd(groupNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(GroupMdEntity result) {
                listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, null);
            }
        });
    }

    public void updateGroupMd(String groupNo, String content, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("content", content);
        request(createService(GroupService.class).updateGroupMd(groupNo, json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                listener.onResult(HttpResponseCode.success, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }
}
