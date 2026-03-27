package com.chat.base.glide;

import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;

public class MyGlideUrlWithId extends GlideUrl {

    private final String id;

    // 每次冷启动生成一个新值，确保头像至少每次启动时刷新
    private static final long APP_LAUNCH_ID = System.currentTimeMillis();

    public MyGlideUrlWithId(String url, String id) {
        super(TextUtils.isEmpty(id) ? url
                : url + (url.contains("?") ? "&" : "?") + "v=" + id + "&s=" + APP_LAUNCH_ID);
        this.id = id;
    }

    @Override
    public String getCacheKey() {
        // 同一次启动内走 Glide 缓存，冷启动后重新拉取
        if (!TextUtils.isEmpty(id)) {
            return id + "_" + APP_LAUNCH_ID;
        }
        return super.getCacheKey();
    }

}
