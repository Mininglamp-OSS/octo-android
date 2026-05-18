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
 * 2020-09-02 10:59
 * 个人中心菜单配置
 */
public class PersonalInfoMenu extends BaseEndpoint {
    public IPersonalInfoMenuClick iPersonalInfoMenuClick;
    public boolean isNewVersionIv = false;

    public PersonalInfoMenu(String sid, int imgResourceID, String text, IPersonalInfoMenuClick iPersonalInfoMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.sid = sid;
        this.iPersonalInfoMenuClick = iPersonalInfoMenuClick;
    }

    public PersonalInfoMenu(int imgResourceID, String text, IPersonalInfoMenuClick iPersonalInfoMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.iPersonalInfoMenuClick = iPersonalInfoMenuClick;
    }

    public void setIsNewVersionIv(boolean is) {
        isNewVersionIv = is;
    }

    public interface IPersonalInfoMenuClick {
        void onClick();
    }
}
