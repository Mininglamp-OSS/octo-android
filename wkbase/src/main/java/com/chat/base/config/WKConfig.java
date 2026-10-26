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
    public interface SessionListener {
        void onSessionChanged(SessionSnapshot snapshot);
    }

    public static final class SessionSnapshot {
        public final long version;
        public final String uid;
        public final String token;

        public SessionSnapshot(long version, String uid, String token) {
            this.version = version;
            this.uid = uid;
            this.token = token;
        }
    }

    private WKConfig() {
    }

    private static class ConfigBinder {
        private static final WKConfig WK_CONFIG = new WKConfig();
    }

    private final Object sessionLock = new Object();
    private long sessionVersion;
    private final java.util.concurrent.CopyOnWriteArrayList<SessionListener> sessionListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static WKConfig getInstance() {
        return ConfigBinder.WK_CONFIG;
    }

    public void setUid(String uid) {
        changeSession(uid, null, true, false);
    }

    public String getUid() {
        synchronized (sessionLock) {
            return WKSharedPreferencesUtil.getInstance().getSP("wk_uid");
        }
    }

    public void setToken(String token) {
        changeSession(null, token, false, true);
    }

    public String getToken() {
        synchronized (sessionLock) {
            return WKSharedPreferencesUtil.getInstance().getSP("wk_token");
        }
    }

    public SessionSnapshot getSessionSnapshot() {
        synchronized (sessionLock) {
            return new SessionSnapshot(sessionVersion,
                    WKSharedPreferencesUtil.getInstance().getSP("wk_uid"),
                    WKSharedPreferencesUtil.getInstance().getSP("wk_token"));
        }
    }

    public void addSessionListener(SessionListener listener) { if (listener != null) sessionListeners.addIfAbsent(listener); }
    public void removeSessionListener(SessionListener listener) { sessionListeners.remove(listener); }
    public void registerSessionListener(SessionListener listener) { addSessionListener(listener); }
    public void unregisterSessionListener(SessionListener listener) { removeSessionListener(listener); }

    public void invalidateSessionContext() {
        SessionSnapshot snapshot;
        synchronized (sessionLock) {
            snapshot = new SessionSnapshot(++sessionVersion,
                    WKSharedPreferencesUtil.getInstance().getSP("wk_uid"),
                    WKSharedPreferencesUtil.getInstance().getSP("wk_token"));
        }
        notifySessionChanged(snapshot);
    }

    private void changeSession(String uid, String token, boolean uidChanged, boolean tokenChanged) {
        SessionSnapshot snapshot = null;
        synchronized (sessionLock) {
            String oldUid = WKSharedPreferencesUtil.getInstance().getSP("wk_uid");
            String oldToken = WKSharedPreferencesUtil.getInstance().getSP("wk_token");
            if (uidChanged) WKSharedPreferencesUtil.getInstance().putSP("wk_uid", uid);
            if (tokenChanged) WKSharedPreferencesUtil.getInstance().putSP("wk_token", token);
            String newUid = uidChanged ? uid : oldUid;
            String newToken = tokenChanged ? token : oldToken;
            if (!java.util.Objects.equals(oldUid, newUid) || !java.util.Objects.equals(oldToken, newToken)) {
                snapshot = new SessionSnapshot(++sessionVersion, newUid, newToken);
            }
        }
        if (snapshot != null) notifySessionChanged(snapshot);
    }

    private void notifySessionChanged(SessionSnapshot snapshot) {
        for (SessionListener listener : sessionListeners) {
            try { listener.onSessionChanged(snapshot); } catch (RuntimeException ignored) { }
        }
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
        SessionSnapshot snapshot;
        synchronized (sessionLock) {
            WKSharedPreferencesUtil.getInstance().putSP("wk_uid", "");
            WKSharedPreferencesUtil.getInstance().putSP("wk_token", "");
            snapshot = new SessionSnapshot(++sessionVersion, "", "");
        }
        notifySessionChanged(snapshot);
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
