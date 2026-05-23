package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import java.util.List;

public class FollowModel extends WKBaseModel {

    private FollowModel() {
    }

    private static class Binder {
        static final FollowModel INSTANCE = new FollowModel();
    }

    public static FollowModel getInstance() {
        return Binder.INSTANCE;
    }

    public void followDM(String peerUid, String categoryId, ICommonListener listener) {
        JSONObject body = new JSONObject();
        body.put("peer_uid", peerUid);
        if (categoryId != null && !categoryId.isEmpty()) {
            body.put("category_id", categoryId);
        }
        request(createService(FollowService.class).followDM(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void unfollowDM(String peerUid, ICommonListener listener) {
        request(createService(FollowService.class).unfollowDM(peerUid), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void refollowChannel(String groupNo, ICommonListener listener) {
        JSONObject body = new JSONObject();
        body.put("group_no", groupNo);
        request(createService(FollowService.class).refollowChannel(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void unfollowChannel(String groupNo, ICommonListener listener) {
        JSONObject body = new JSONObject();
        body.put("group_no", groupNo);
        request(createService(FollowService.class).unfollowChannel(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void followThread(String threadChannelId, ICommonListener listener) {
        JSONObject body = new JSONObject();
        body.put("thread_channel_id", threadChannelId);
        request(createService(FollowService.class).followThread(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void unfollowThread(String threadChannelId, ICommonListener listener) {
        request(createService(FollowService.class).unfollowThread(threadChannelId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void sort(List<FollowSortItem> items, int version, ICommonListener listener) {
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (FollowSortItem item : items) {
            arr.add(item.toJson());
        }
        body.put("items", arr);
        body.put("version", version);
        request(createService(FollowService.class).sort(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                FollowedKeysStore.getInstance().bumpVersion();
                FollowedKeysStore.getInstance().reload();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public static boolean isVersionConflictError(String msg) {
        return msg != null && msg.toLowerCase().contains("version conflict");
    }

    public static class FollowSortItem {
        public int target_type;
        public String target_id;
        public int sort;

        public FollowSortItem(int targetType, String targetId, int sort) {
            this.target_type = targetType;
            this.target_id = targetId;
            this.sort = sort;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("target_type", target_type);
            json.put("target_id", target_id != null ? target_id : "");
            json.put("sort", sort);
            return json;
        }
    }
}
