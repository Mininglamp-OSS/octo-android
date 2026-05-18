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

package com.chat.base.endpoint.entity;

import android.app.Activity;
import android.view.View;

/**
 * 2020-11-27 14:03
 * 播放视频
 */
public class PlayVideoMenu {
    public String playUrl;
    public String coverUrl;
    public String videoTitle;
    public Activity activity;
    public View view;

    public PlayVideoMenu(Activity activity, View view, String videoTitle, String playUrl, String coverUrl) {
        this.playUrl = playUrl;
        this.coverUrl = coverUrl;
        this.videoTitle = videoTitle;
        this.activity = activity;
        this.view = view;
    }
}
