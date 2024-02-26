package com.chat.base.glide;

import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;

public class MyGlideUrlWithId extends GlideUrl {

    private final String id;

    public MyGlideUrlWithId(String url, String id) {
        super(url);
        this.id = id;
    }

    @Override
    public String getCacheKey() {
        return !TextUtils.isEmpty(id) ? id : super.getCacheKey();
    }

}
