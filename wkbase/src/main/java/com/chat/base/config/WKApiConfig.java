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

import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2019-11-20 10:11
 * api地址
 */
public class WKApiConfig {
    public static String baseUrl = "";
    public static String baseWebUrl = "";
    private static String documentBaseUrl = "";
    public static String termsUrl = "";
    public static String privacyUrl = "";

    public static void initBaseURL(String apiURL) {
        updateBaseUrls(apiURL, null, null, false);
    }

    public static void initBaseURLIncludeIP(String apiURL) {
        updateBaseUrls(apiURL, null, null, false);
    }

    public static void initBaseURL(String apiURL, String webBaseURL, String documentBaseURL) {
        updateBaseUrls(apiURL, webBaseURL, documentBaseURL, true);
    }

    public static String getDocumentBaseUrl() {
        return documentBaseUrl;
    }

    private static void updateBaseUrls(String apiURL, String webBaseURL, String documentBaseURL,
                                       boolean includeOverrides) {
        String nextBase = apiURL + "/v1/";
        String nextWeb = includeOverrides ? webBaseURL : apiURL + "/web/";
        String nextDocument = includeOverrides
                ? com.chat.base.utils.DocumentViewerUrlPolicy.resolveDocumentBaseUrl(nextBase, documentBaseURL)
                : com.chat.base.utils.DocumentViewerUrlPolicy.resolveDocumentBaseUrl(nextBase, null);
        boolean changed = !java.util.Objects.equals(baseUrl, nextBase)
                || !java.util.Objects.equals(baseWebUrl, nextWeb)
                || !java.util.Objects.equals(documentBaseUrl, nextDocument);
        baseUrl = nextBase;
        baseWebUrl = nextWeb;
        documentBaseUrl = nextDocument;
        if (changed) WKConfig.getInstance().invalidateSessionContext();
    }

    public static String getAvatarUrl(String uid) {
        return baseUrl + "users/" + uid + "/avatar";
    }

    public static String getGroupUrl(String groupId) {
        return baseUrl + "groups/" + groupId + "/avatar";
    }

    public static String getShowAvatar(String channelID, byte channelType) {
        return channelType == WKChannelType.PERSONAL ? getAvatarUrl(channelID) : getGroupUrl(channelID);
    }

    public static String getShowUrl(String url) {
        if (TextUtils.isEmpty(url) || url.startsWith("http") || url.startsWith("HTTP")) {
            return url;
        } else {
            return baseUrl + url;
        }
    }

}
