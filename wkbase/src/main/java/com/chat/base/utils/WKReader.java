package com.chat.base.utils;

import org.json.JSONObject;

import java.util.List;

public class WKReader {
    public static String stringValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key))
            return "";
        return jsonObject.optString(key);
    }

    public static <T> boolean isNotEmpty(List<T> list) {
        return list != null && !list.isEmpty();
    }

    public static <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }
}
