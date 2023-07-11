package com.chat.base.glide;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.R;

/**
 * 2019-05-06 10:52
 * glide
 */
public class GlideRequestOptions {

    private static GlideRequestOptions instance;

    public static GlideRequestOptions getInstance() {
        if (instance == null) {
            synchronized (GlideRequestOptions.class) {
                if (instance == null) {
                    instance = new GlideRequestOptions();
                }
            }
        }
        return instance;
    }

    /**
     * 默认
     */
    public RequestOptions normalRequestOption(int width, int height) {
        return new RequestOptions()
                .error(R.drawable.default_view_bg).override(width, height)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.ALL);
    }

    public RequestOptions normalRequestOption() {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.ALL);
    }

    public RequestOptions normalRequestOption(int defImgResource) {
        return new RequestOptions()
                .error(defImgResource)
                .placeholder(defImgResource)
                .diskCacheStrategy(DiskCacheStrategy.ALL);
    }


    /**
     * 默认头像
     */
    public RequestOptions headRequestOption() {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.ALL);

    }


}
