package com.xinbida.wukongim.utils;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class WKCommonUtils {
    private static final String TAG = "WKCommonUtils";

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

    public static HashMap<String, Object> str2HashMap(String extra) {
        HashMap<String, Object> hashMap = new HashMap<>();
        if (!TextUtils.isEmpty(extra)) {
            try {
                JSONObject jsonObject = new JSONObject(extra);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "str2HashMap error");
            }
        }
        return hashMap;
    }
}
