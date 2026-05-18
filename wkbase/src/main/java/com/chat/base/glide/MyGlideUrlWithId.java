/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.glide;

import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;

/**
 * Avatar / image 的 Glide URL 包装器，cache key 只依赖服务端下发的
 * {@code avatarCacheKey}（{@code WKChannel.avatarCacheKey}）。
 *
 * <p>-P-03: 之前这里会把进程启动时间 {@code APP_LAUNCH_ID} 拼进 URL
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
