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

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.R;

/**
 * 2019-05-06 10:52
 * glide
 *
 *  phase2 perf (A8): DiskCacheStrategy 从 ALL → AUTOMATIC，减少对 remote 原图的额外 IO。
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
     *  phase2 perf (A8): 头像带 override，提前降采样。
     */
    public RequestOptions headRequestOption(int width, int height) {
        return new RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .override(width, height)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }


}
