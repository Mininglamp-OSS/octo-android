package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.List;

public class SidebarSyncResponse {

    public List<SidebarItemEntity> items;
    public long version;
    public int follow_version;

    public static SidebarSyncResponse fromJson(JSONObject json) {
        SidebarSyncResponse r = new SidebarSyncResponse();
        if (json == null) {
            r.items = Collections.emptyList();
            r.version = 0;
            r.follow_version = 0;
            return r;
        }
        JSONArray arr = json.getJSONArray("items");
        r.items = (arr != null) ? SidebarItemEntity.fromJsonArray(arr) : Collections.emptyList();
        r.version = json.getLongValue("version");
        r.follow_version = json.getIntValue("follow_version");
        return r;
    }
}
