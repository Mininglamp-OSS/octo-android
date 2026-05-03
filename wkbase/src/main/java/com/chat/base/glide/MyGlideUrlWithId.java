package com.chat.base.glide;

import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;

/**
 * Avatar / image 的 Glide URL 包装器，cache key 只依赖服务端下发的
 * {@code avatarCacheKey}（{@code WKChannel.avatarCacheKey}）。
 *
 * <p>YUJ-283-P-03: 之前这里会把进程启动时间 {@code APP_LAUNCH_ID} 拼进 URL
 * 和 cacheKey，导致每次冷启动所有头像磁盘缓存都作废，冷启进入 TabActivity
 * 后头像必须重新下载。现在去除该字段，cache key 只随 avatarCacheKey 变化，
 * 服务端在头像变更时会翻版本号，长期命中磁盘缓存。</p>
 */
public class MyGlideUrlWithId extends GlideUrl {

    private final String id;

    public MyGlideUrlWithId(String url, String id) {
        super(TextUtils.isEmpty(id) ? url
                : url + (url.contains("?") ? "&" : "?") + "v=" + id);
        this.id = id;
    }

    @Override
    public String getCacheKey() {
        // 只用服务端 avatarCacheKey 作为 cache key；头像变更由后端翻版本号触发失效。
        if (!TextUtils.isEmpty(id)) {
            return id;
        }
        return super.getCacheKey();
    }

}
