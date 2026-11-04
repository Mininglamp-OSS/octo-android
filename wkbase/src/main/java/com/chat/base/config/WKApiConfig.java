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
import android.content.Context;

import com.chat.base.ui.Theme;
import com.chat.base.utils.ReportUrlBuilder;
import com.chat.base.utils.language.WKLanguageType;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2019-11-20 10:11
 * api地址
 */
public class WKApiConfig {
    public static String baseUrl = "";
    public static String baseWebUrl = "";
    public static String termsUrl = "";
    public static String privacyUrl = "";
    public static String reportUrl = "";

    public static String getReportUrl(Context context, String channelId, byte channelType) {
        int languageType = WKMultiLanguageUtil.getInstance().getLanguageType();
        String lang;
        if (languageType == WKLanguageType.LANGUAGE_EN) {
            lang = "en";
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL) {
            lang = "zh-Hant";
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED) {
            lang = "zh-Hans";
        } else {
            java.util.Locale locale = WKMultiLanguageUtil.getInstance().getSysLocale();
            if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
                lang = "en";
            } else if (locale != null && ("Hant".equalsIgnoreCase(locale.getScript())
                    || "TW".equalsIgnoreCase(locale.getCountry())
                    || "HK".equalsIgnoreCase(locale.getCountry())
                    || "MO".equalsIgnoreCase(locale.getCountry()))) {
                lang = "zh-Hant";
            } else {
                lang = "zh-Hans";
            }
        }
        String mode = Theme.getDarkModeStatus(context) ? "dark" : "light";
        return ReportUrlBuilder.build(reportUrl, lang, WKConfig.getInstance().getUid(),
                WKConfig.getInstance().getToken(), mode, channelId, channelType);
    }

    public static void initBaseURL(String apiURL) {
        baseUrl = apiURL + "/v1/";
        baseWebUrl = apiURL + "/web/";
    }

    public static void initBaseURLIncludeIP(String apiURL) {
        baseUrl = apiURL + "/v1/";
        baseWebUrl = apiURL + "/web/";
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
