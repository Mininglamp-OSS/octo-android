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
import com.xinbida.wukongim.entity.WKConversationMsg;
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
     * <p>YUJ-183 · Fix B Step 1：对缺失外部群 extra 字段的本地 row 强制全量重拉。
     *
     * <p>根因（Coda 定位）：后端 membersync 是 {@code WHERE version > $clientVersion}
     * 的增量接口。老用户本地 DB 里该群 {@code maxVersion} 早已超过"外部群字段首次下发"
     * 时的 version bump 点，之后若该群成员 row 没再被 bump → 客户端永远拉不到
     * {@code is_external / source_space_* / home_space_*} 字段，
     * {@code WKChannelMember.extraMap} 永远残缺，4 个下游 bug 全部触发。
     *
     * <p>修法：进入该方法时先看我自己这一行的 extraMap 有没有外部群字段痕迹
     * （{@code home_space_id} / {@code is_external}）。缺就用 {@code version=0}
     * 强制全量重拉一次，让后端把所有 row 重新下发一遍，{@code serialize} 就能
     * 正确写全 extraMap。单次强制后 {@code save} 走 CONFLICT_REPLACE，
     * 本地 DB 整行刷新，下次再进群自然走正常增量路径（因为 extra 已经完整了）。
     */
    public synchronized void groupMembersSync(String groupNo, final ICommonListener iCommonListener) {
        long version = resolveSyncVersion(groupNo);
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

    /**
     * 计算当前请求应该用的 sync version。
     * <p>正常：读 SDK 本地 DB 的 max version（增量）。
     * <p>YUJ-183 Fix B Step 1：如果本地我这一行的 extraMap 缺外部群字段
     * （{@code home_space_id} / {@code is_external} 都没有），返回 0 强制全量重拉。
     * <p>{@code groupMembersSync} 是 synchronized 的，且全量后 {@code save} 走
     * CONFLICT_REPLACE 立即补齐 extra，第二次调用这个判定就不会再命中 0 分支 →
     * 不会死循环。<br>
     * {@code @VisibleForTesting} 以便 JVM 单测验证判定矩阵。
     */
    @VisibleForTesting
    long resolveSyncVersion(String groupNo) {
        long version = WKIM.getInstance().getChannelMembersManager().getMaxVersion(groupNo, WKChannelType.GROUP);
        if (version <= 0) {
            return 0; // first sync 就是全量，原路径
        }
        boolean needForceFull = isMyExtraMissingExternalFields(groupNo);
        if (needForceFull) {
            return 0;
        }
        return version;
    }

    /**
     * 判断本地我自己这一行 WKChannelMember 的 extraMap 是否缺外部群标记。
     * <p>返回 true 表示缺，需要触发 Fix B Step 1 的强制全量重拉。
     * <p>判定条件：my row 存在 + 两个关键 key（{@code home_space_id}、{@code is_external}）都不在 extraMap。
     * <ul>
     *     <li>my row 不存在：交给正常首次同步（version=0 已经是 0）。</li>
     *     <li>只要任一 key 在 → 假定 extraMap 已被后端正确写过，不重拉（避免每次都强制全量导致流量爆炸）。</li>
     * </ul>
     */
    private static boolean isMyExtraMissingExternalFields(String groupNo) {
        try {
            String myUid = WKConfig.getInstance().getUid();
            if (TextUtils.isEmpty(myUid)) return false;
            WKChannelMember me = WKIM.getInstance().getChannelMembersManager()
                    .getMember(groupNo, WKChannelType.GROUP, myUid);
            if (me == null) return false;
            return isExtraMissingExternalFields(me.extraMap);
        } catch (Throwable ignored) {
            return false; // 异常环境 fail-safe：不强制重拉，不制造额外流量风险
        }
    }

    /**
     * 纯函数：判断给定的 extraMap 是否缺少外部群标记字段。
     *
     * <p>拆成 package-private + static 是为了在 JVM 单元测试里直接校验判定矩阵
     * （同 {@code serialize} 的 YUJ-86 EP1 pattern）。
     *
     * @param extras member 行的 extraMap（可能为 null）
     * @return true = 缺字段，需要强制 full sync；false = 已有外部群标记，可走增量
     */
    @VisibleForTesting
    static boolean isExtraMissingExternalFields(java.util.HashMap<String, Object> extras) {
        if (extras == null || extras.isEmpty()) return true;
        boolean hasHome = extras.containsKey(WKChannelMemberExtras.homeSpaceID);
        boolean hasIsExternal = extras.containsKey(WKChannelMemberExtras.isExternal);
        return !hasHome && !hasIsExternal;
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
                iGroupQr.onResult(HttpResponseCode.success, "", result.day, result.qrcode, result.expire, result.invite_url);
            }

            @Override
            public void onFail(int code, String msg) {
                iGroupQr.onResult(code, msg, 0, "", "", "");
            }
        });

    }

    public interface IGroupQr {
        void onResult(int code, String msg, int day, String qrCode, String expire, String inviteUrl);
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

    // ------------------------------------------------------------------
    // YUJ-183 · Fix B Step 2 · one-time 老用户外部群 extra 字段回填迁移
    // ------------------------------------------------------------------

    /**
     * 迁移标记 SharedPreferences key（per-uid）。true 表示此 uid 已做过本次迁移。
     * 版本号带在 key 里方便将来再加一次迁移时独立跟踪（不影响已完成的 v1）。
     */
    private static final String MIGRATION_KEY_V1 = "yuj183_external_fields_migration_v1_done";

    /**
     * 迁移调度间隔（ms）—— sequential dispatch 避免一次打出几十个 HTTP 请求。
     * {@code groupMembersSync} 本身是 synchronized 的，但若一下投递 100 个 callback 链，
     * 后端 / 客户端线程池都会有短时压力。
     */
    private static final long MIGRATION_DISPATCH_INTERVAL_MS = 300L;

    /**
     * Fix B Step 2：一次性批量迁移 —— 老用户首次启动带修复代码的 APK 时，
     * 把本地所有 GROUP 会话依次触发 {@link #groupMembersSync(String, ICommonListener)}。
     *
     * <p>Step 1 的 {@link #resolveSyncVersion} 会判定该群我的 row 是否缺字段，缺就用
     * version=0 强制全量重拉；所以这里只是"帮用户主动触发一遍"，不主动决定每个群
     * 用不用全量。好处：
     * <ul>
     *     <li>不用遍历每个群逐一判断 "是否外部群"（客户端无可靠 signal）。</li>
     *     <li>内部群 + extra 已完整的群：resolveSyncVersion 返回本地 maxVersion，
     *         后端返回空列表，零数据 round-trip，代价接近 0。</li>
     *     <li>外部群 + extra 残缺的群：resolveSyncVersion 返回 0 全量重拉，extraMap 被写全。</li>
     * </ul>
     *
     * <p>幂等：用 {@link WKSharedPreferencesUtil#getBooleanWithUID} 标记完成，再次调用
     * 立即返回。调用方通常在 {@code WKIMUtils.initIMListener()} 末尾挂一次即可。
     *
     * <p>失败语义：单个群 sync 失败不阻塞整体迁移（下一个继续），整体完成（不管子任务成败）
     * 后 mark done。下一次 APK 启动就不再重跑——失败群的 extra 会在用户进入群时由
     * Step 1 兜底触发。
     */
    public void runExternalFieldsMigrationIfNeeded() {
        try {
            // 未登录 / uid 为空：跳过，等登录后 WKIMUtils.initIMListener 再调一次。
            if (TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
                return;
            }
            boolean done = com.chat.base.config.WKSharedPreferencesUtil.getInstance()
                    .getBoolean(WKConfig.getInstance().getUid() + "_" + MIGRATION_KEY_V1, false);
            if (done) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        // 后台线程收集 group 列表：DB 查询不要阻塞主线程。
        new Thread(() -> {
            try {
                List<String> groupNos = collectLocalGroupNos();
                AndroidUtilities.runOnUIThread(() -> dispatchMigrationNext(groupNos, 0));
            } catch (Throwable ignored) {
            }
        }, "yuj183-migration-collect").start();
    }

    /**
     * 从 SDK 本地 conversation DB 收集所有群会话号（含当前用户所在但不含子区 COMMUNITY_TOPIC）。
     * <p>{@code @VisibleForTesting} 是为了在后续扩展单测时能直接注入 stub 会话列表。
     */
    @VisibleForTesting
    List<String> collectLocalGroupNos() {
        List<String> groupNos = new ArrayList<>();
        try {
            List<WKConversationMsg> allConvs = WKIM.getInstance().getConversationManager()
                    .getWithChannelType(WKChannelType.GROUP);
            if (WKReader.isNotEmpty(allConvs)) {
                for (WKConversationMsg conv : allConvs) {
                    if (conv == null || TextUtils.isEmpty(conv.channelID)) continue;
                    if (conv.channelType != WKChannelType.GROUP) continue;
                    groupNos.add(conv.channelID);
                }
            }
        } catch (Throwable ignored) {
        }
        return groupNos;
    }

    /**
     * Sequential dispatch：一个接一个 sync，间隔 {@link #MIGRATION_DISPATCH_INTERVAL_MS}。
     * 末尾（index 越界）把 MIGRATION_KEY_V1 写成 true。
     */
    private void dispatchMigrationNext(List<String> groupNos, int index) {
        if (groupNos == null || index >= groupNos.size()) {
            com.chat.base.config.WKSharedPreferencesUtil.getInstance().putBooleanWithUID(
                    MIGRATION_KEY_V1, true);
            return;
        }
        final String groupNo = groupNos.get(index);
        groupMembersSync(groupNo, (code, msg) -> AndroidUtilities.runOnUIThread(
                () -> dispatchMigrationNext(groupNos, index + 1),
                MIGRATION_DISPATCH_INTERVAL_MS));
    }
}
