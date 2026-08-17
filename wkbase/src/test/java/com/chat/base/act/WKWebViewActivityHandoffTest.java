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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WKWebViewActivityHandoffTest {
    private static final String REAL_URL = "https://web.octo.test/d/42?sid=android#section";
    private static final String TRUSTED_ORIGIN = "https://web.octo.test";

    @Test public void bootstrapSetsCurrentSpaceBeforeNavigation() {
        String html = bootstrap("space-42");
        String write = "localStorage.setItem('currentSpaceId', spaceId);";
        String navigate = "window.location.replace(\"" + REAL_URL + "\");";
        assertTrue(html.contains("var spaceId=\"space-42\";"));
        assertOrdered(html, write, navigate);
    }

    @Test public void bootstrapRemovesStaleCurrentSpaceWhenSelectionIsEmpty() {
        String html = bootstrap("");
        String remove = "localStorage.removeItem('currentSpaceId');";
        assertTrue(html.contains("var spaceId=\"\";"));
        assertOrdered(html, remove, "window.location.replace");
    }

    @Test public void bootstrapEscapesInlineScriptValues() {
        String lineSeparator = String.valueOf((char) 0x2028);
        String attack = "x'</script><script>injected()</script>" + lineSeparator + "&";
        String html = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                REAL_URL, attack, attack, attack, attack, attack);
        assertFalse(html.contains("</script><script>injected()"));
        assertFalse(html.contains(lineSeparator));
        assertTrue(html.contains("\\u003c\\/script\\u003e"));
        assertTrue(html.contains("\\u2028"));
        assertTrue(html.contains("\\u0026"));
    }

    @Test public void bootstrapNavigatesToUnmodifiedRealUrlWithoutSpaceQueryParameters() {
        String html = bootstrap("space with / and ?");
        assertTrue(html.contains("localStorage.setItem('token'+s, \"token\");"));
        assertTrue(html.contains("localStorage.setItem('uid'+s, \"uid\");"));
        assertTrue(html.contains("localStorage.setItem('name'+s, \"name\");"));
        assertOrdered(html, "currentSpaceId", "localStorage.setItem('token'+s");
        assertOrdered(html, "localStorage.setItem('token'+s", "window.location.replace");
        assertTrue(html.contains("}catch(e){}try{"));
        assertTrue(html.contains("window.location.replace(\"" + REAL_URL + "\");"));
        assertFalse(html.contains("?sp="));
        assertFalse(html.contains("&sp="));
        assertFalse(html.contains("viewer="));
    }

    @Test public void onlyStandaloneDocsRoutesReceiveSpaceHandoff() {
        assertTrue(DocsViewerUrlPolicy.isViewerPath("/d/doc-1"));
        assertTrue(DocsViewerUrlPolicy.isViewerPath("/ppt/d/deck-1/"));
        assertTrue(DocsViewerUrlPolicy.isViewerPath("/docs/deck-1/present"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath(null));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/d/"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/d/doc-1/edit"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/docs/deck-1"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/d/.."));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/d/id.with.dot"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/d/a:b"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/ppt/d/a:b"));
        assertFalse(DocsViewerUrlPolicy.isViewerPath("/docs/a:b/present"));
        assertTrue(DocsViewerUrlPolicy.isViewerPath("/d/Az_09-"));
    }

    @Test public void trustedViewerGateRequiresExactOriginAndPath() {
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/d/doc-1?next=report.html", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test.evil/d/doc-1", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "http://web.octo.test/d/doc-1", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test:8443/d/doc-1", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/d/doc%2Fchild", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/d/%2e%2e", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://user@web.octo.test/d/doc-1", TRUSTED_ORIGIN));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test:443/d/doc-1#section", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/d/%64oc-1", TRUSTED_ORIGIN));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/d/../doc-1", TRUSTED_ORIGIN));
    }

    @Test public void webOriginComesFromAndNormalizesConfiguredWebBase() {
        assertEquals("https://web.octo.test",
                DocsViewerUrlPolicy.originOf("https://WEB.octo.test/docs/"));
        assertEquals("https://web.octo.test:8443",
                DocsViewerUrlPolicy.originOf("https://web.octo.test:8443/docs/"));
        assertNull(DocsViewerUrlPolicy.originOf("not a URL"));
    }

    @Test public void configuredTrustUsesWebBaseInsteadOfApiBase() {
        assertEquals("https://web.octo.test", DocsViewerUrlPolicy.originOfConfiguredBases(
                "https://api.octo.test/v1/", "https://web.octo.test/app/"));
    }

    @Test public void anonymousDocsBootstrapClearsStaleSpaceBeforeUnmodifiedNavigation() {
        String html = DocsViewerUrlPolicy.buildSpaceBootstrapHtml(REAL_URL, "");
        assertOrdered(html, "localStorage.removeItem('currentSpaceId')",
                "window.location.replace");
        assertTrue(html.contains("window.location.replace(\"" + REAL_URL + "\");"));
        assertFalse(html.contains("tokenandroid"));
        assertFalse(html.contains("?sp="));
    }

    @Test public void nonDocsBootstrapDoesNotTouchCurrentSpace() {
        String html = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                REAL_URL, "android", "token", "uid", "name", null);
        assertFalse(html.contains("currentSpaceId"));
        assertTrue(html.contains("window.location.replace(\"" + REAL_URL + "\");"));
    }

    private static String bootstrap(String currentSpaceId) {
        return DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                REAL_URL, "android", "token", "uid", "name", currentSpaceId);
    }

    private static void assertOrdered(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue("missing first fragment: " + first, firstIndex >= 0);
        assertTrue("missing second fragment: " + second, secondIndex >= 0);
        assertTrue("wrong fragment order", firstIndex < secondIndex);
    }
}
