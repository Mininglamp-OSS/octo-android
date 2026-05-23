package com.chat.uikit.sidebar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SidebarItemEntity {

    public static final int TARGET_TYPE_DM = 1;
    public static final int TARGET_TYPE_CHANNEL = 2;
    public static final int TARGET_TYPE_THREAD = 5;

    public int target_type;
    public String target_id;
    public int channel_type;
    public String channel_id;
    public long timestamp;
    public int unread;
    public boolean is_pinned;
    public boolean is_followed;
    public String category_id;
    public int category_sort;
    public long follow_sort;
    public String parent_channel_id;

    public String followKey() {
        return target_type + "::" + (target_id != null ? target_id : "");
    }

    public static SidebarItemEntity fromJson(JSONObject json) {
        SidebarItemEntity e = new SidebarItemEntity();
        e.target_type = json.getIntValue("target_type");
        e.target_id = json.getString("target_id") != null ? json.getString("target_id") : "";
        e.channel_type = json.getIntValue("channel_type");
        e.channel_id = json.getString("channel_id") != null ? json.getString("channel_id") : "";
        e.timestamp = json.getLongValue("timestamp");
        e.unread = json.getIntValue("unread");
        e.is_pinned = json.getBooleanValue("is_pinned");
        e.is_followed = json.getBooleanValue("is_followed");
        e.category_id = json.getString("category_id");
        e.category_sort = json.getIntValue("category_sort");
        Long rawFollowSort = json.getLong("follow_sort");
        e.follow_sort = (rawFollowSort != null) ? rawFollowSort : Long.MAX_VALUE;
        e.parent_channel_id = json.getString("parent_channel_id");
        return e;
    }

    public static List<SidebarItemEntity> fromJsonArray(JSONArray array) {
        List<SidebarItemEntity> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj != null) {
                result.add(fromJson(obj));
            }
        }
        return result;
    }
}
