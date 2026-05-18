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

/**
 * 2020-09-02 12:14
 * 联系人模块
 */
public class ContactsMenu extends BaseEndpoint {
    public int badgeNum;
    //是否显示红点提示
    public boolean showRedDot;
    public String uid;
    public String sid;
    public String countValue;
    public Class<?> targetActivity;

    public ContactsMenu(String sid, int imgResourceID, String text, IMenuClick iMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.sid = sid;
        this.iMenuClick = iMenuClick;
    }

    public ContactsMenu(String sid, int imgResourceID, String text, Class<?> targetActivity) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.sid = sid;
        this.targetActivity = targetActivity;
    }
}
