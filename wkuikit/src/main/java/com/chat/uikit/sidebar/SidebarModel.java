package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;

public class SidebarModel extends WKBaseModel {

    private SidebarModel() {
    }

    private static class Binder {
        static final SidebarModel INSTANCE = new SidebarModel();
    }

    public static SidebarModel getInstance() {
        return Binder.INSTANCE;
    }

    public interface ISidebarSyncListener {
        void onResult(SidebarSyncResponse response);

        void onError(int code, String msg);
    }

    public void sync(String tab, long version, String lastMsgSeqs, String deviceUUID, ISidebarSyncListener listener) {
        JSONObject body = new JSONObject();
        body.put("tab", tab);
        body.put("version", version);
        body.put("last_msg_seqs", lastMsgSeqs != null ? lastMsgSeqs : "");
        body.put("msg_count", 1);
        body.put("device_uuid", deviceUUID != null ? deviceUUID : "");
        request(createService(SidebarService.class).sync(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(JSONObject result) {
                SidebarSyncResponse response = SidebarSyncResponse.fromJson(result);
                listener.onResult(response);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }
}
