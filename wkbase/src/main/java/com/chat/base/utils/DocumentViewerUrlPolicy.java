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

/** Builds and validates document viewer routes from an explicit or legacy document root. */
public final class DocumentViewerUrlPolicy {
    private static final String DOCUMENT_ID = "[A-Za-z0-9_-]{1,128}";
    private static final Pattern VIEWER_PATH = Pattern.compile(
            "^/(?:d/" + DOCUMENT_ID + "|ppt/d/" + DOCUMENT_ID
                    + "|docs/" + DOCUMENT_ID + "/present)/?$");

    private DocumentViewerUrlPolicy() {
    }

    /** Resolve an explicit complete docs root, or use the legacy API suffix derivation when blank. */
    public static String resolveDocumentBaseUrl(String apiBaseUrl, String documentOverride) {
        if (documentOverride != null && !documentOverride.trim().isEmpty()) {
            return normalizeDocumentBaseUrl(documentOverride);
        }
        return legacyDocumentBaseUrlFromApi(apiBaseUrl);
    }

    /** Validate an explicit complete docs root without removing API-looking path components. */
    public static String normalizeDocumentBaseUrl(String documentBase) {
        URI uri = parseHttpsBase(documentBase);
        if (uri == null) return null;
        return "https://" + uri.getRawAuthority() + normalizedRawPath(uri.getRawPath());
    }

    /**
     * Legacy fallback for same-origin deployments: on 2026-10-29 /web/d opened a generic page,
     * while the user verified /d and iOS uses the same API-derived route.
     */
    public static String legacyDocumentBaseUrlFromApi(String apiBaseUrl) {
        URI uri = parseHttpsBase(apiBaseUrl);
        if (uri == null) return null;
        String path = normalizedRawPath(uri.getRawPath());
        if (path.endsWith("/api/v1")) path = path.substring(0, path.length() - "/api/v1".length());
        else if (path.endsWith("/v1")) path = path.substring(0, path.length() - "/v1".length());
        return "https://" + uri.getRawAuthority() + normalizedRawPath(path);
    }

    public static String documentUrl(String documentBaseUrl, String documentId) {
        if (!isValidDocumentId(documentId)) return null;
        String normalized = normalizeDocumentBaseUrl(documentBaseUrl);
        return normalized == null ? null : normalized + "/d/" + documentId;
    }

    /** Accept only exact viewer routes relative to a valid document-service root. */
    public static boolean isTrustedViewerUrl(String url, String documentBaseUrl) {
        URI candidate = parseAbsoluteHttpsUrl(url);
        URI viewerBase = parseAbsoluteHttpsUrl(normalizeDocumentBaseUrl(documentBaseUrl));
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

    public static String originOfViewerBase(String documentBaseUrl) {
        URI base = parseAbsoluteHttpsUrl(normalizeDocumentBaseUrl(documentBaseUrl));
        if (base == null) return null;
        try {
            return new URI("https", null, base.getHost(), base.getPort(), null, null, null).toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static URI parseHttpsBase(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        URI uri = parseAbsoluteHttpsUrl(value.trim());
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
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }
}
