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
package com.chat.base.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/** Builds and validates document viewer routes from the configured HTTPS API deployment base. */
public final class DocumentViewerUrlPolicy {
    private static final String DOCUMENT_ID = "[A-Za-z0-9_-]{1,128}";
    private static final Pattern VIEWER_PATH = Pattern.compile(
            "^/(?:d/" + DOCUMENT_ID + "|ppt/d/" + DOCUMENT_ID
                    + "|docs/" + DOCUMENT_ID + "/present)/?$");

    private DocumentViewerUrlPolicy() {
    }

    /**
     * The API suffixes /api/v1 and /v1 identify endpoints, while document routes share the
     * deployment root. For example, /gateway/api/v1 maps to /gateway/d/{id}; other path prefixes
     * remain intact because they may identify a tenant or deployment. The configured /web path
     * belongs to general Web pages; appending it to a document URL would route into the main Web app.
     */
    public static String viewerBaseUrl(String apiBaseUrl) {
        URI uri = parseApiBase(apiBaseUrl);
        if (uri == null) return null;
        String path = normalizedRawPath(uri.getRawPath());
        if (path.endsWith("/api/v1")) {
            path = path.substring(0, path.length() - "/api/v1".length());
        } else if (path.endsWith("/v1")) {
            path = path.substring(0, path.length() - "/v1".length());
        }
        path = normalizedRawPath(path);
        return "https://" + uri.getRawAuthority() + path;
    }

    public static String documentUrl(String apiBaseUrl, String documentId) {
        if (!isValidDocumentId(documentId)) return null;
        String viewerBase = viewerBaseUrl(apiBaseUrl);
        return viewerBase == null ? null : viewerBase + "/d/" + documentId;
    }

    /**
     * Accepts only viewer routes at the configured deployment root. Paths are compared raw so
     * encoded separators and encoded document IDs cannot become trusted after decoding.
     */
    public static boolean isTrustedViewerUrl(String url, String apiBaseUrl) {
        URI candidate = parseAbsoluteHttpsUrl(url);
        URI viewerBase = parseAbsoluteHttpsUrl(viewerBaseUrl(apiBaseUrl));
        if (candidate == null || viewerBase == null || !sameOrigin(candidate, viewerBase)) return false;
        String basePath = normalizedRawPath(viewerBase.getRawPath());
        String rawPath = candidate.getRawPath();
        if (rawPath == null) return false;
        if (basePath.length() == 0) return isViewerPath(rawPath);
        if (!rawPath.startsWith(basePath + "/")) return false;
        return isViewerPath(rawPath.substring(basePath.length()));
    }

    public static boolean isValidDocumentId(String value) {
        return value != null && value.matches(DOCUMENT_ID);
    }

    public static boolean isViewerPath(String rawPath) {
        return rawPath != null && VIEWER_PATH.matcher(rawPath).matches();
    }

    public static String originOfViewerBase(String apiBaseUrl) {
        URI base = parseAbsoluteHttpsUrl(viewerBaseUrl(apiBaseUrl));
        if (base == null) return null;
        try {
            return new URI("https", null, base.getHost(), base.getPort(), null, null, null).toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static URI parseApiBase(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() == 0) return null;
        URI uri = parseAbsoluteHttpsUrl(normalized);
        if (uri == null || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
        return uri;
    }

    private static URI parseAbsoluteHttpsUrl(String value) {
        if (value == null || value.length() == 0) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().length() == 0 || uri.getRawUserInfo() != null) return null;
            return uri;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String normalizedRawPath(String rawPath) {
        if (rawPath == null || rawPath.length() == 0) return "";
        int end = rawPath.length();
        while (end > 0 && rawPath.charAt(end - 1) == '/') end--;
        return rawPath.substring(0, end);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return 443;
    }
}
