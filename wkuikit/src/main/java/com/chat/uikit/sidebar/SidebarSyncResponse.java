package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xinbida.wukongim.entity.WKSpaceMembership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SidebarSyncResponse {

    public List<SidebarItemEntity> items;
    public long version;
    public int follow_version;
    public List<WKSpaceMembership> space_memberships;

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
        JSONArray smArr = json.getJSONArray("space_memberships");
        if (smArr != null) {
            r.space_memberships = new ArrayList<>(smArr.size());
            for (int i = 0; i < smArr.size(); i++) {
                JSONObject obj = smArr.getJSONObject(i);
                if (obj == null) continue;
                WKSpaceMembership m = new WKSpaceMembership();
                m.channel_id = obj.getString("channel_id");
                m.space_id = obj.getString("space_id");
                m.my_source_space_id = obj.getString("my_source_space_id");
                r.space_memberships.add(m);
            }
        }
        return r;
    }
}
