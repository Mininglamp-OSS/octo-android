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

package com.chat.base.config;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.UserInfoSetting;

/**
 * 2019-11-13 10:27
 * 配置文件
 */
public class WKConfig {
    private WKConfig() {
    }

    private static class ConfigBinder {
        private static final WKConfig WK_CONFIG = new WKConfig();
    }

    public static WKConfig getInstance() {
        return ConfigBinder.WK_CONFIG;
    }

    public void setUid(String uid) {
        WKSharedPreferencesUtil.getInstance().putSP("wk_uid", uid);
    }

    public String getUid() {
        return WKSharedPreferencesUtil.getInstance().getSP("wk_uid");
    }

    public void setToken(String token) {
        WKSharedPreferencesUtil.getInstance().putSP("wk_token", token);
    }

    public String getToken() {
        return WKSharedPreferencesUtil.getInstance().getSP("wk_token");
    }

    public void setImToken(String imToken) {
        WKSharedPreferencesUtil.getInstance().putSP("wk_im_token", imToken);
    }

    public String getImToken() {
        return WKSharedPreferencesUtil.getInstance().getSP("wk_im_token");
    }

    public void setUserName(String name) {
        WKSharedPreferencesUtil.getInstance().putSP("wk_name", name);
    }

    public String getUserName() {
        return WKSharedPreferencesUtil.getInstance().getSP("wk_name");
    }

    public void clearInfo() {
        setUid("");
        setToken("");
        setImToken("");
        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
        userInfoEntity.token = "";
        userInfoEntity.im_token = "";
        WKConfig.getInstance().saveUserInfo(userInfoEntity);
    }

    public void saveAppConfig(WKAPPConfig WKAPPConfig) {
        String json = new Gson().toJson(WKAPPConfig);
        WKSharedPreferencesUtil.getInstance().putSP("app_config", json);
        //  · A3：appconfig 更新后清掉系统 Bot 白名单缓存，下次读取走最新值
        com.chat.base.space.SystemBotsFallback.invalidateCache();
    }

    public WKAPPConfig getAppConfig() {
        String json = WKSharedPreferencesUtil.getInstance().getSP("app_config");
        WKAPPConfig WKAPPConfig = null;
        if (!TextUtils.isEmpty(json)) {
            WKAPPConfig = new Gson().fromJson(json, WKAPPConfig.class);
        }
        if (WKAPPConfig == null) {
            WKAPPConfig = new WKAPPConfig();
        }
        return WKAPPConfig;
    }

    public void saveUserInfo(UserInfoEntity userInfoEntity) {
        String json = new Gson().toJson(userInfoEntity);
        WKSharedPreferencesUtil.getInstance().putSP("user_info", json);
    }

    public UserInfoEntity getUserInfo() {
        String json = WKSharedPreferencesUtil.getInstance().getSP("user_info");
        UserInfoEntity userInfoEntity = null;
        if (!TextUtils.isEmpty(json)) {
            userInfoEntity = new Gson().fromJson(json, UserInfoEntity.class);
        }
        if (userInfoEntity == null) {
            userInfoEntity = new UserInfoEntity();
        }
        if (userInfoEntity.setting == null)
            userInfoEntity.setting = new UserInfoSetting();
        return userInfoEntity;
    }
}
