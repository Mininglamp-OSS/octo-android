package com.chat.login.service;

import java.net.URI;
import java.net.URISyntaxException;

/** Transport checks shared by OIDC setup, requests and the initial WebView load. */
public final class OidcUrlPolicy {
    private OidcUrlPolicy() {}

    public static boolean isHttpsUrl(String value) {
        try {
            requireHttps(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static URI requireHttps(String value) {
        try {
            URI uri = value == null ? null : new URI(value);
            if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("OIDC requires a valid HTTPS URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            // Do not put the URL in an exception: it may carry an authcode.
            throw new IllegalArgumentException("Invalid OIDC URL");
        }
    }

    public static String resolveAuthorizeUrl(String apiBase, String authorizePath) {
        URI api = requireHttps(apiBase);
        if (authorizePath == null || authorizePath.isEmpty() || authorizePath.startsWith("//")) {
            throw new IllegalArgumentException("Invalid OIDC authorize path");
        }
        try {
            URI target = api.resolve(new URI(authorizePath));
            return requireHttps(target.toString()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid OIDC authorize path");
        }
    }
}
