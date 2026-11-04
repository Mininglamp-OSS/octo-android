package com.chat.base.utils;

import okhttp3.HttpUrl;

/** Builds the report page URL from the active API base URL. */
public final class ReportUrlBuilder {
    private ReportUrlBuilder() {
    }

    public static String getEndpoint(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.trim().isEmpty()) return "";
        HttpUrl base = HttpUrl.parse(apiBaseUrl.trim());
        if (base == null || !"https".equals(base.scheme()) || base.host().isEmpty()
                || base.username().length() > 0 || base.password().length() > 0) return "";
        String path = base.encodedPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            // Ignore query and fragment from API base configuration; endpoint identity is path-based.
            return base.newBuilder().encodedPath(path + "/report/html").query(null).fragment(null).build().toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public static String build(String apiBaseUrl, String lang, String uid, String token,
                               String mode, String channelId, byte channelType) {
        String endpoint = getEndpoint(apiBaseUrl);
        if (endpoint.isEmpty()) return "";
        HttpUrl parsed = HttpUrl.parse(endpoint);
        if (parsed == null) return "";
        try {
            return parsed.newBuilder()
                    .setQueryParameter("lang", lang)
                    .setQueryParameter("uid", uid)
                    .setQueryParameter("token", token)
                    .setQueryParameter("mode", mode)
                    .setQueryParameter("channel_id", channelId)
                    .setQueryParameter("channel_type", Byte.toString(channelType))
                    .build().toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public static boolean isConfiguredReportUrl(String url, String apiBaseUrl) {
        HttpUrl candidate = HttpUrl.parse(url);
        HttpUrl configured = HttpUrl.parse(getEndpoint(apiBaseUrl));
        return candidate != null && configured != null
                && "https".equals(candidate.scheme())
                && candidate.scheme().equals(configured.scheme())
                && candidate.host().equals(configured.host())
                && candidate.port() == configured.port()
                && candidate.encodedPath().equals(configured.encodedPath());
    }
}
