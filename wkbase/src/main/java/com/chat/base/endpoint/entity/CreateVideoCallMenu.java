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
