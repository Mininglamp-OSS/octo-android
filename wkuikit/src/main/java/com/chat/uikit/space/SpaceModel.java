package com.chat.uikit.space;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import java.util.List;

public class SpaceModel extends WKBaseModel {

    private SpaceModel() {
    }

    private static class Binder {
        static final SpaceModel INSTANCE = new SpaceModel();
    }

    public static SpaceModel getInstance() {
        return Binder.INSTANCE;
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
        request(createService(SpaceService.class).getMySpaces(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<SpaceEntity> result) {
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
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
        request(createService(SpaceService.class).getMembers(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<SpaceEntity.SpaceMember> result) {
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
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
