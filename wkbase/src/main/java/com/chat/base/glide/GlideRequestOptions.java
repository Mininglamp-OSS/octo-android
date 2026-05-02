package com.chat.base.glide;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.R;

/**
 * 2019-05-06 10:52
 * glide
 *
 * YUJ-236 phase2 perf (A8): 将 DiskCacheStrategy 从 ALL 切换为 AUTOMATIC，
 * 减少对远程原图的额外 IO（AUTOMATIC 对 remote 资源默认仅缓存 DATA，对 local 仅缓存 RESOURCE）。
 * 原 ALL 会把原图 + 变换后位图都写盘，聊天列表里会放大解码+IO 开销。
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
     * YUJ-236 phase2 perf (A8): override 后再加 thumbnail(0.1f)，
     * 先用 1/10 尺寸 bitmap 绘制占位，再在完整 bitmap 就绪后替换，
     * 滑动期间肉眼几乎无感，但解码峰值显著下降。
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
     * YUJ-236 phase2 perf (A8): 头像请求，带明确的像素尺寸 override。
     * 聊天会话列表头像 40dp、消息气泡内头像 36dp，都远小于服务端原图。
     * 没有 override 时 Glide 会退回到 ImageView 测量尺寸（layout 阶段才可知），
     * 快速滑动里经常命中未测量的 ViewHolder 触发多次解码。主动 override 消除这个路径。
     */
    public RequestOptions headRequestOption(int width, int height) {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .override(width, height)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }


}
