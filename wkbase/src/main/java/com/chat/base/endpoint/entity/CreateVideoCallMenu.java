package com.chat.base.endpoint.entity;

import android.app.Activity;

import com.xinbida.wukongim.entity.WKChannel;

import java.util.List;

/**
 * 5/7/21 6:39 PM
 */
public class CreateVideoCallMenu {
    public String channelID;
    public byte channelType;
    public List<WKChannel> WKChannels;
    public Activity activity;

    public CreateVideoCallMenu(Activity activity, String channelID, byte channelType, List<WKChannel> WKChannels) {
        this.WKChannels = WKChannels;
        this.activity = activity;
        this.channelID = channelID;
        this.channelType = channelType;
    }
}
