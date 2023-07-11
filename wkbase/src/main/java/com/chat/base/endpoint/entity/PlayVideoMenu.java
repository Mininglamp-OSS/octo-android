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
