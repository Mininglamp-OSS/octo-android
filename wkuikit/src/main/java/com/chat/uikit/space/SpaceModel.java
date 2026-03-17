package com.chat.uikit.space;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import java.util.ArrayList;
import java.util.List;

public class SpaceModel extends WKBaseModel {

    private List<SpaceEntity> cachedSpaces = null;
    private String cachedMembersSpaceId = null;
    private List<SpaceEntity.SpaceMember> cachedMembers = null;

    private SpaceModel() {
    }

    private static class Binder {
        static final SpaceModel INSTANCE = new SpaceModel();
    }

    public static SpaceModel getInstance() {
        return Binder.INSTANCE;
    }

    /** 清除缓存，下次会重新从网络拉取 */
    public void invalidateCache() {
        cachedSpaces = null;
        cachedMembersSpaceId = null;
        cachedMembers = null;
    }

    public interface ISpaceListListener {
        void onResult(List<SpaceEntity> list);

        void onError(int code, String msg);
    }

    public interface ISpaceListener {
        void onResult(SpaceEntity space);

        void onError(int code, String msg);
    }

    public interface IMembersListener {
        void onResult(List<SpaceEntity.SpaceMember> members);

        void onError(int code, String msg);
    }

    public interface IInviteListener {
        void onResult(String inviteCode);

        void onError(int code, String msg);
    }

    public void getMySpaces(ISpaceListListener listener) {
        // 有缓存先立即返回，再后台刷新
        if (cachedSpaces != null) {
            listener.onResult(new ArrayList<>(cachedSpaces));
        }
        request(createService(SpaceService.class).getMySpaces(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<SpaceEntity> result) {
                cachedSpaces = result;
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (cachedSpaces == null) {
                    listener.onError(code, msg);
                }
                // 有缓存时网络失败不报错，静默降级
            }
        });
    }

    public void createSpace(String name, String description, ISpaceListener listener) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("description", description);
        request(createService(SpaceService.class).createSpace(json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(SpaceEntity result) {
                // 直接追加进缓存，无需重新请求
                if (cachedSpaces != null) {
                    cachedSpaces.add(result);
                }
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void getSpaceDetail(String spaceId, ISpaceListener listener) {
        request(createService(SpaceService.class).getSpaceDetail(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(SpaceEntity result) {
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void getMembers(String spaceId, IMembersListener listener) {
        // 有缓存且是同一个 Space，直接返回
        if (cachedMembers != null && spaceId.equals(cachedMembersSpaceId)) {
            listener.onResult(cachedMembers);
            return;
        }
        request(createService(SpaceService.class).getMembers(spaceId, 1, 10000), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<SpaceEntity.SpaceMember> result) {
                cachedMembersSpaceId = spaceId;
                cachedMembers = result;
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    /** 清除成员缓存（新增/删除成员后调用） */
    public void invalidateMembersCache() {
        cachedMembersSpaceId = null;
        cachedMembers = null;
    }

    public void createInvite(String spaceId, IInviteListener listener) {
        request(createService(SpaceService.class).createInvite(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(SpaceEntity.InviteResult result) {
                listener.onResult(result.invite_code);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void joinSpace(String inviteCode, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("invite_code", inviteCode);
        request(createService(SpaceService.class).joinSpace(json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache(); // 加入新 Space 后清除缓存
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void leaveSpace(String spaceId, ICommonListener listener) {
        request(createService(SpaceService.class).leaveSpace(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache(); // 离开 Space 后清除缓存
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void disbandSpace(String spaceId, ICommonListener listener) {
        request(createService(SpaceService.class).disbandSpace(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache(); // 解散 Space 后清除缓存
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void removeMembers(String spaceId, List<String> uids, ICommonListener listener) {
        JSONObject json = new JSONObject();
        JSONArray uidArray = new JSONArray();
        uidArray.addAll(uids);
        json.put("uids", uidArray);
        request(createService(SpaceService.class).removeMembers(spaceId, json), new IRequestResultListener<>() {
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

    public void changeMemberRole(String spaceId, String uid, int role, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("role", role);
        request(createService(SpaceService.class).changeMemberRole(spaceId, uid, json), new IRequestResultListener<>() {
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
