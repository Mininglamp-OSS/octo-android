package com.chat.base.utils;

import okhttp3.HttpUrl;

/** Builds the report page URL without coupling it to API or web base URLs. */
public final class ReportUrlBuilder {
    private ReportUrlBuilder() {
    }

    public static String build(String reportUrl, String lang, String uid, String token,
                               String mode, String channelId, byte channelType) {
        HttpUrl parsed = HttpUrl.parse(reportUrl);
        if (parsed == null) throw new IllegalArgumentException("Invalid report URL");
        return parsed.newBuilder()
                .setQueryParameter("lang", lang)
                .setQueryParameter("uid", uid)
                .setQueryParameter("token", token)
                .setQueryParameter("mode", mode)
                .setQueryParameter("channel_id", channelId)
                .setQueryParameter("channel_type", Byte.toString(channelType))
                .build().toString();
    }

    public static boolean isConfiguredReportUrl(String url, String reportUrl) {
        HttpUrl candidate = HttpUrl.parse(url);
        HttpUrl configured = HttpUrl.parse(reportUrl);
        return candidate != null && configured != null
                && candidate.scheme().equals(configured.scheme())
                && candidate.host().equals(configured.host())
                && candidate.port() == configured.port()
                && candidate.encodedPath().equals(configured.encodedPath());
    }
}
