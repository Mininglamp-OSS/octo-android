package com.chat.base.glide;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.R;

/**
 * 2019-05-06 10:52
 * glide
 *
 * YUJ-236 phase2 perf (A8): DiskCacheStrategy 从 ALL → AUTOMATIC，减少对 remote 原图的额外 IO。
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
     * 默认 — 带明确目标尺寸
     */
    public RequestOptions normalRequestOption(int width, int height) {
        return new RequestOptions()
                .error(R.drawable.default_view_bg).override(width, height)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }

    public RequestOptions normalRequestOption() {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }

    public RequestOptions normalRequestOption(int defImgResource) {
        return new RequestOptions()
                .error(defImgResource)
                .placeholder(defImgResource)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }


    /**
     * 默认头像
     */
    public RequestOptions headRequestOption() {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);

    }

    /**
     * YUJ-236 phase2 perf (A8): 头像带 override，提前降采样。
     */
    public RequestOptions headRequestOption(int width, int height) {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .override(width, height)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }


}
