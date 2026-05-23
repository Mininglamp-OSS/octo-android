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

package com.chat.uikit.thread.service;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.group.service.entity.GroupMdEntity;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.WKReader;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.chat.uikit.thread.service.entity.ThreadMember;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class ThreadModel extends WKBaseModel {

    private static final String CHANNEL_ID_SEPARATOR = "____";

    private ThreadModel() {
    }

    private static class ThreadModelBinder {
        private static final ThreadModel instance = new ThreadModel();
    }

    public static ThreadModel getInstance() {
        return ThreadModelBinder.instance;
    }

    /**
     * 解析子区 channelId，返回 {groupNo, shortId}
     */
    public String[] parseChannelId(String channelId) {
        if (TextUtils.isEmpty(channelId) || !channelId.contains(CHANNEL_ID_SEPARATOR)) {
            return null;
        }
        int idx = channelId.indexOf(CHANNEL_ID_SEPARATOR);
        String groupNo = channelId.substring(0, idx);
        String shortId = channelId.substring(idx + CHANNEL_ID_SEPARATOR.length());
        return new String[]{groupNo, shortId};
    }

    /**
     * 构建子区 channelId
     */
    public String buildChannelId(String groupNo, String shortId) {
        return groupNo + CHANNEL_ID_SEPARATOR + shortId;
    }

    /**
     * 获取子区列表（全量，兼容旧调用）
     */
    public void listThreads(String groupNo, IThreadListListener listener) {
        listThreads(groupNo, 1, 10000, listener);
    }

    /**
     * 获取子区列表（单页，用于列表分页加载）
     */
    public void listThreads(String groupNo, int page, int limit, IThreadListListener listener) {
        request(createService(ThreadService.class).listThreads(groupNo, page, limit), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<ThreadEntity> result) {
                listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, null);
            }
        });
    }

    /**
     * 创建子区
     */
    public void createThread(String groupNo, String name, String sourceMessageId, JSONObject sourceMessagePayload, IThreadDetailListener listener) {
        JSONObject body = new JSONObject();
        body.put("name", name);
        if (!TextUtils.isEmpty(sourceMessageId)) {
            try {
                body.put("source_message_id", Long.parseLong(sourceMessageId));
            } catch (NumberFormatException ignored) {
            }
        }
        if (sourceMessagePayload != null) {
            body.put("source_message_payload", sourceMessagePayload);
        }
        request(createService(ThreadService.class).createThread(groupNo, body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(ThreadEntity result) {
                listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, null);
            }
        });
    }

    /**
     * 获取子区详情
     */
    public void getThreadDetail(String groupNo, String shortId, IThreadDetailListener listener) {
        request(createService(ThreadService.class).getThreadDetail(groupNo, shortId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(ThreadEntity result) {
                listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, null);
            }
        });
    }

    /**
     * 修改子区名称
     */
    public void updateThreadName(String groupNo, String shortId, String name, ICommonListener listener) {
        JSONObject body = new JSONObject();
        body.put("name", name);
        request(createService(ThreadService.class).updateThread(groupNo, shortId, body), new IRequestResultListener<>() {
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

    /**
     * 加入子区
     */
    public void joinThread(String groupNo, String shortId, ICommonListener listener) {
        request(createService(ThreadService.class).joinThread(groupNo, shortId), new IRequestResultListener<>() {
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

    /**
     * 离开子区
     */
    public void leaveThread(String groupNo, String shortId, ICommonListener listener) {
        request(createService(ThreadService.class).leaveThread(groupNo, shortId), new IRequestResultListener<>() {
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

    /**
     * 归档子区
     */
    public void archiveThread(String groupNo, String shortId, ICommonListener listener) {
        request(createService(ThreadService.class).archiveThread(groupNo, shortId), new IRequestResultListener<>() {
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

    /**
     * 取消归档子区
     */
    public void unarchiveThread(String groupNo, String shortId, ICommonListener listener) {
        request(createService(ThreadService.class).unarchiveThread(groupNo, shortId), new IRequestResultListener<>() {
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

    /**
     * 删除子区
     */
    public void deleteThread(String groupNo, String shortId, ICommonListener listener) {
        request(createService(ThreadService.class).deleteThread(groupNo, shortId), new IRequestResultListener<>() {
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

    /**
     * 获取子区成员列表
     */
    public void getThreadMembers(String groupNo, String shortId, String keyword, int page, int limit, IThreadMemberListListener listener) {
        request(createService(ThreadService.class).getThreadMembers(groupNo, shortId, keyword, page, limit), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<ThreadMember> result) {
                listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, null);
            }
        });
    }

    /**
     * 增量同步子区成员
     */
    public void syncThreadMembers(String channelID, ICommonListener listener) {
        long version = WKIM.getInstance().getChannelMembersManager().getMaxVersion(channelID, WKChannelType.COMMUNITY_TOPIC);
        request(createService(ThreadService.class).syncThreadMembers(channelID, version, 1000), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<ThreadMember> result) {
                if (WKReader.isNotEmpty(result)) {
                    List<WKChannelMember> members = serializeMembers(channelID, result);
                    WKIM.getInstance().getChannelMembersManager().save(members);
                }
                if (listener != null) {
                    listener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) {
                    listener.onResult(code, msg);
                }
            }
        });
    }

    private List<WKChannelMember> serializeMembers(String channelID, List<ThreadMember> list) {
        List<WKChannelMember> members = new ArrayList<>();
        if (WKReader.isEmpty(list)) {
            return members;
        }
        for (Object item : list) {
            if (!(item instanceof ThreadMember)) continue;
            ThreadMember tm = (ThreadMember) item;
            WKChannelMember member = new WKChannelMember();
            member.memberUID = tm.uid;
            member.memberName = tm.name;
            member.memberRemark = tm.remark;
            member.channelID = channelID;
            member.channelType = WKChannelType.COMMUNITY_TOPIC;
            member.isDeleted = tm.is_deleted;
            member.version = tm.version;
            member.role = tm.role;
            member.status = tm.status;
            member.updatedAt = tm.updated_at;
            member.createdAt = tm.created_at;
            members.add(member);
        }
        return members;
    }

    public interface IThreadListListener {
        void onResult(int code, String msg, List<ThreadEntity> list);
    }

    public void getThreadMd(String groupNo, String shortId, GroupModel.IGroupMdListener listener) {
        request(createService(ThreadService.class).getThreadMd(groupNo, shortId), new IRequestResultListener<>() {
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

    public void updateThreadMd(String groupNo, String shortId, String content, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("content", content);
        request(createService(ThreadService.class).updateThreadMd(groupNo, shortId, json), new IRequestResultListener<>() {
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

    public interface IThreadDetailListener {
        void onResult(int code, String msg, ThreadEntity entity);
    }

    public interface IThreadMemberListListener {
        void onResult(int code, String msg, List<ThreadMember> list);
    }
}
