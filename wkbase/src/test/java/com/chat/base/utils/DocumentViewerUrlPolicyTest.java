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
    @Test public void explicitDocumentRootPreservesCompletePathAndNormalizesTrailingSlash() {
        assertEquals("https://docs.example.test/gateway/api/v1",
                DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://api.example.test/api/v1", " https://docs.example.test/gateway/api/v1/// "));
        assertEquals("https://docs.example.test/gateway/api/v1/d/doc_A-9",
                DocumentViewerUrlPolicy.documentUrl("https://docs.example.test/gateway/api/v1///", "doc_A-9"));
        assertNull(DocumentViewerUrlPolicy.documentUrl(null, "doc_A-9"));
    }

    @Test public void blankOverrideUsesLegacyApiFallbackAndPreservesDeploymentPrefix() {
        assertEquals("https://docs.example.test", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/api/v1/", " "));
        assertEquals("https://docs.example.test:8443/gateway", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test:8443/gateway/api/v1/", null));
        assertEquals("https://docs.example.test/tenant/v1", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/tenant/v1/api/v1/", null));
        assertEquals("https://docs.example.test/gateway", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/gateway/v1/", null));
        assertEquals("https://docs.example.test/gateway/custom", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/gateway/custom/api/v1/", null));
        assertEquals("https://docs.example.test/gateway%2Fdocs", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/gateway%2Fdocs/v1///", null));
        assertEquals("https://docs.example.test/gateway/custom/api/v2", DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://docs.example.test/gateway/custom/api/v2/", null));
        String doubleSlashBase = DocumentViewerUrlPolicy.legacyDocumentBaseUrlFromApi("https://docs.example.test/gateway//api/v1");
        assertEquals("https://docs.example.test/gateway", doubleSlashBase);
        String doubleSlashViewer = DocumentViewerUrlPolicy.documentUrl(doubleSlashBase, "doc-1");
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl(doubleSlashViewer, doubleSlashBase));
        assertEquals("https://docs.example.test/path", DocumentViewerUrlPolicy.legacyDocumentBaseUrlFromApi("https://docs.example.test/path///"));
    }

    @Test public void explicitInvalidDocumentRootDoesNotFallBack() {
        for (String value : new String[] {"http://docs.example.test", "https://user@docs.example.test", "https://docs.example.test/path?x=1", "https://docs.example.test/path#frag"}) {
            assertNull(value, DocumentViewerUrlPolicy.resolveDocumentBaseUrl("https://api.example.test/api/v1", value));
        }
    }

    @Test public void trustsOnlyExactViewerRoutesAtConfiguredDocumentRoot() {
        String docs = "https://docs.example.test/gateway/api/v1/";
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://docs.example.test/gateway/api/v1/d/doc-1?sp=space#section", docs));
        assertTrue(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://docs.example.test/gateway/api/v1/ppt/d/deck-1/", docs));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://docs.example.test/gateway/api/v1/settings", docs));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://api.example.test/api/v1/d/doc-1", docs));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://docs.example.test/web/d/doc-1", "https://docs.example.test/gateway/api/v1"));
        assertFalse(DocumentViewerUrlPolicy.isTrustedViewerUrl("https://docs.example.test/gateway/api/v1/d/a%2Fb", docs));
        assertEquals("https://docs.example.test", DocumentViewerUrlPolicy.originOfViewerBase(docs));
        assertNull(DocumentViewerUrlPolicy.documentUrl(docs, "bad/id"));
        assertNull(DocumentViewerUrlPolicy.documentUrl(docs, ""));
    }

    @Test public void rejectsUnsafeApiFallbackBases() {
        for (String value : new String[] {"", "http://docs.example.test/api/v1", "https://user@docs.example.test/api/v1", "https://docs.example.test/api/v1?tenant=x", "https://docs.example.test/api/v1#section", "https://"}) {
            assertNull(value, DocumentViewerUrlPolicy.resolveDocumentBaseUrl(value, null));
        }
    }
}
