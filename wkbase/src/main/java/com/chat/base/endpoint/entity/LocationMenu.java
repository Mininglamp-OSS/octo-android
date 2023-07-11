package com.chat.base.endpoint.entity;

import android.content.Context;

/**
 * 2020-12-22 14:01
 * 显示地址
 */
public class LocationMenu {
    public String address;
    public double longitude;
    public double latitude;
    public Context context;
    public LocationMenu(Context context, String address, double longitude, double latitude) {
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.context = context;
    }
}
