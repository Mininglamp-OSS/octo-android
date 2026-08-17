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
package com.chat.base.act;

import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/** Plain-JVM URL policy for native-to-Web Docs viewer context handoff. */
final class DocsViewerUrlPolicy {
    private static final String DOCUMENT_ID = "[A-Za-z0-9_-]{1,128}";
    private static final Pattern VIEWER_PATH = Pattern.compile(
            "^/(?:d/" + DOCUMENT_ID + "|ppt/d/" + DOCUMENT_ID
                    + "|docs/" + DOCUMENT_ID + "/present)/?$");

    private DocsViewerUrlPolicy() {
    }

    /**
     * Selects the configured Web base explicitly. The API base is accepted only to keep the
     * independently configured inputs visible and testable at the Activity wiring boundary.
     */
    static String originOfConfiguredBases(String apiBaseUrl, String webBaseUrl) {
        return originOf(webBaseUrl);
    }

    /** Returns a canonical scheme/host/port origin, or null for an invalid web base URL. */
    static String originOf(String webBaseUrl) {
        URI uri = parseAbsoluteHttpUri(webBaseUrl);
        if (uri == null) return null;
        try {
            return new URI(
                    uri.getScheme().toLowerCase(java.util.Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(java.util.Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null).toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    static boolean isTrustedOriginUrl(String url, String origin) {
        URI candidate = parseAbsoluteHttpUri(url);
        URI trustedOrigin = parseAbsoluteHttpUri(origin);
        return candidate != null && trustedOrigin != null && isSameOrigin(candidate, trustedOrigin);
    }

    static boolean isTrustedViewerUrl(String url, String origin) {
        URI candidate = parseAbsoluteHttpUri(url);
        URI trustedOrigin = parseAbsoluteHttpUri(origin);
        if (candidate == null || trustedOrigin == null || !isSameOrigin(candidate, trustedOrigin)) {
            return false;
        }
        String rawPath = candidate.getRawPath();
        return rawPath != null && VIEWER_PATH.matcher(rawPath).matches();
    }

    static boolean isViewerPath(String path) {
        return path != null && VIEWER_PATH.matcher(path).matches();
    }

    static String buildIdentityBootstrapHtml(String realUrl, String sid, String token, String uid,
                                             String name, String currentSpaceId) {
        String identity = "try{" +
                "var s=" + quoteForInlineScript(sid) + ";" +
                "localStorage.setItem('token'+s, " + quoteForInlineScript(token) + ");" +
                "localStorage.setItem('uid'+s, " + quoteForInlineScript(uid) + ");" +
                "localStorage.setItem('name'+s, " + quoteForInlineScript(name) + ");" +
                "}catch(e){}";
        return buildBootstrapHtml(realUrl, currentSpaceId, identity);
    }

    static String buildSpaceBootstrapHtml(String realUrl, String currentSpaceId) {
        return buildBootstrapHtml(realUrl, currentSpaceId, "");
    }

    private static String buildBootstrapHtml(String realUrl, String currentSpaceId, String identity) {
        String space = buildSpaceStorageScript(currentSpaceId);
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title></title></head><body>" +
                "<script>(function(){" + space + identity +
                "window.location.replace(" + quoteForInlineScript(realUrl) + ");" +
                "})();</script></body></html>";
    }

    /** null leaves pooled storage untouched; empty clears it; non-empty replaces it. */
    private static String buildSpaceStorageScript(String currentSpaceId) {
        if (currentSpaceId == null) return "";
        return "try{" +
                "var spaceId=" + quoteForInlineScript(currentSpaceId) + ";" +
                "if(spaceId){localStorage.setItem('currentSpaceId', spaceId);}" +
                "else{localStorage.removeItem('currentSpaceId');}" +
                "}catch(e){}";
    }

    /** Quotes a value without allowing it to terminate the inline script element. */
    private static String quoteForInlineScript(String value) {
        return JSONObject.quote(value)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static boolean isSameOrigin(URI candidate, URI origin) {
        return candidate.getScheme().equalsIgnoreCase(origin.getScheme())
                && candidate.getHost().equalsIgnoreCase(origin.getHost())
                && effectivePort(candidate) == effectivePort(origin);
    }

    private static URI parseAbsoluteHttpUri(String value) {
        if (value == null || value.length() == 0) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.length() == 0) return null;
            if (uri.getRawUserInfo() != null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return uri;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
