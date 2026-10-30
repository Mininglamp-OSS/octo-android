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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DocumentViewerUrlPolicyTest {
    @Test public void derivesViewerRootByRemovingOnlyTheKnownApiSuffix() {
        assertEquals("https://docs.example.test",
                DocumentViewerUrlPolicy.viewerBaseUrl(" https://docs.example.test/api/v1/// "));
        assertEquals("https://docs.example.test:8443/gateway",
                DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test:8443/gateway/api/v1/"));
        assertEquals("https://docs.example.test/tenant/v1",
                DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/tenant/v1/api/v1/"));
        assertEquals("https://docs.example.test/gateway%2Fdocs",
                DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/gateway%2Fdocs/v1///"));
        assertEquals("https://docs.example.test/gateway/custom",
                DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/gateway/custom///"));
        assertEquals("https://docs.example.test",
                DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/"));
    }

    @Test public void createsAsciiDocumentUrlThatMatchesTheSameDeploymentPolicy() {
        String apiBase = "https://docs.example.test:8443/gateway/api/v1/";
        String url = DocumentViewerUrlPolicy.documentUrl(apiBase, "doc_A-9");
        assertEquals("https://docs.example.test:8443/gateway/d/doc_A-9", url);
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(url, apiBase));
        assertEquals("https://docs.example.test:8443",
                DocumentViewerUrlPolicy.originOfViewerBase(apiBase));
        assertNull(DocumentViewerUrlPolicy.documentUrl(apiBase, "bad/id"));
        assertNull(DocumentViewerUrlPolicy.documentUrl(apiBase, ""));

        String repeatedSlashApiBase = "https://docs.example.test/gateway//api/v1";
        String normalizedUrl = DocumentViewerUrlPolicy.documentUrl(repeatedSlashApiBase, "doc-2");
        assertEquals("https://docs.example.test/gateway/d/doc-2", normalizedUrl);
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(normalizedUrl, repeatedSlashApiBase));
    }

    @Test public void acceptsOnlyExactDeploymentRelativeViewerRoutes() {
        String apiBase = "https://docs.example.test/gateway/api/v1/";
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/d/doc-1?sp=space#section", apiBase));
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/ppt/d/deck-1/", apiBase));
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/docs/deck-1/present", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/d/doc-1", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway-other/d/doc-1", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/d/%2e%2e", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/d/a%2Fb", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/gateway/d/%64oc-1", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://other.example.test/gateway/d/doc-1", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test:8444/gateway/d/doc-1", apiBase));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.example.test/web/d/doc-1", "https://docs.example.test/api/v1/"));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl(
                "http://docs.example.test/gateway/d/doc-1", apiBase));
    }

    @Test public void rejectsUnsafeApiBases() {
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl(""));
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl("http://docs.example.test/api/v1"));
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl("https://user@docs.example.test/api/v1"));
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/api/v1?tenant=x"));
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl("https://docs.example.test/api/v1#section"));
        assertNull(DocumentViewerUrlPolicy.viewerBaseUrl("https://"));
    }
}
