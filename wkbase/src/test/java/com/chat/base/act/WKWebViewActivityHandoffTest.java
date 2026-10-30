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

    @Test public void trustedViewerGateRequiresExactDocumentOriginAndPath() {
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/doc-1?next=report.html", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test.evil/d/doc-1", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "http://docs.octo.test/d/doc-1", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test:8443/d/doc-1", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/doc%2Fchild", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/%2e%2e", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://user@docs.octo.test/d/doc-1", "https://docs.octo.test/"));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test:443/d/doc-1#section", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/%64oc-1", "https://docs.octo.test/"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/../doc-1", "https://docs.octo.test/"));
    }

    @Test public void documentDeploymentPrefixAllowsOnlyDocumentRelativeViewerRoutes() {
        String baseWithSlash = " https://docs.octo.test/gateway/api/v1/// ";
        String baseWithoutSlash = "https://docs.octo.test/gateway/api/v1";
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/doc-1?sid=android#section", baseWithSlash));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/ppt/d/deck-1/", baseWithoutSlash));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/docs/deck-1/present", baseWithoutSlash));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/doc-1", "https://docs.octo.test/"));

        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/d/doc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1-extra/d/doc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/%2e%2e", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/a%2Fb", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/%64oc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/doc-1", "https://docs.octo.test/gateway/api/v1?x=1"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/api/v1/d/doc-1", "https://docs.octo.test/gateway/api/v1#fragment"));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test:8443/gateway/api/v1/d/doc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "http://docs.octo.test/gateway/api/v1/d/doc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://other.octo.test/gateway/api/v1/d/doc-1", baseWithSlash));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://user@docs.octo.test/gateway/api/v1/d/doc-1", baseWithSlash));
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test:443/gateway/api/v1/d/doc-1", baseWithSlash));
    }

    @Test public void configuredRawEncodedPrefixRetainsItsRawPathSemantics() {
        String configuredBase = "https://docs.octo.test/gateway%2Fweb/api/v1/";
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway%2Fweb/api/v1/d/doc-1", configuredBase));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://docs.octo.test/gateway/web/api/v1/d/doc-1", configuredBase));
    }

    @Test public void absentDocumentBaseNeverTrustsApiHostButWebTrustRemainsIndependent() {
        String webBase = "https://web.octo.test/app/web/";
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://api.octo.test/gateway/d/doc-1", null));
        assertEquals("https://web.octo.test", DocsViewerUrlPolicy.handoffOriginForUrl(
                "https://web.octo.test/app/web/settings", null, webBase));
        assertEquals("https://web.octo.test", DocsViewerUrlPolicy.handoffOriginForUrl(
                "https://web.octo.test/app/web/d/doc-1", null, webBase));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/app/web/d/doc-1", null));
        assertNull(DocsViewerUrlPolicy.selectDocsViewerSpaceId(false, false, null, "space-global"));
        assertNull(DocsViewerUrlPolicy.handoffOriginForUrl(
                "https://api.octo.test/gateway/d/doc-1", null, webBase));
    }

    @Test public void documentViewerAndWebPageSelectTheirOwnTrustedBootstrapOrigins() {
        String apiBase = "https://api.octo.test:8443/gateway/api/v1/";
        String documentBase = "https://docs.octo.test:9443/gateway/api/v1/";
        String webBase = "https://web.octo.test/app/web/";
        String target = "https://docs.octo.test:9443/gateway/api/v1/d/doc-1?sid=custom&x=1#page";
        assertTrue(DocsViewerUrlPolicy.isTrustedViewerUrl(target, documentBase));
        assertEquals("https://docs.octo.test:9443", DocsViewerUrlPolicy.handoffOriginForUrl(target, documentBase, webBase));
        assertNull(DocsViewerUrlPolicy.handoffOriginForUrl(
                "https://docs.octo.test:9443/gateway/api/v1/settings", documentBase, webBase));
        assertEquals("https://web.octo.test", DocsViewerUrlPolicy.handoffOriginForUrl(
                "https://web.octo.test/app/web/settings", documentBase, webBase));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl(
                "https://web.octo.test/app/web/d/doc-1", documentBase));
        assertFalse(DocsViewerUrlPolicy.isTrustedViewerUrl("https://api.octo.test:8443/gateway/api/v1/d/doc-1", documentBase));

        String identityHtml = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                target, "custom", "token", "uid", "name", "space-7");
        assertOrdered(identityHtml, "localStorage.setItem('currentSpaceId', spaceId);",
                "localStorage.setItem('token'+s");
        assertOrdered(identityHtml, "localStorage.setItem('token'+s", "window.location.replace");
        String escapedTarget = target.replace("&", "\\u0026");
        assertTrue(identityHtml.contains("window.location.replace(\"" + escapedTarget + "\");"));

        String anonymousHtml = DocsViewerUrlPolicy.buildSpaceBootstrapHtml(target, "");
        assertOrdered(anonymousHtml, "localStorage.removeItem('currentSpaceId');",
                "window.location.replace");
        String anonymousWithSpaceHtml = DocsViewerUrlPolicy.buildSpaceBootstrapHtml(target, "space-7");
        assertOrdered(anonymousWithSpaceHtml, "localStorage.setItem('currentSpaceId', spaceId);",
                "window.location.replace");
        assertTrue(anonymousHtml.contains("window.location.replace(\"" + escapedTarget + "\");"));
    }

    @Test public void docsViewerSpaceUsesExplicitMessageValueIncludingEmpty() {
        String current = "space-global";
        String missingExtra = DocsViewerUrlPolicy.selectDocsViewerSpaceId(true, false, null, current);
        String explicitEmpty = DocsViewerUrlPolicy.selectDocsViewerSpaceId(true, true, "", current);
        String explicitSpace = DocsViewerUrlPolicy.selectDocsViewerSpaceId(true, true, "space-message", current);
        assertEquals(current, missingExtra);
        assertEquals("", explicitEmpty);
        assertEquals("space-message", explicitSpace);
        assertNull(DocsViewerUrlPolicy.selectDocsViewerSpaceId(false, true, "space-message", current));
        assertTrue(DocsViewerUrlPolicy.isValidDocsViewerSpaceId(explicitEmpty));
        assertFalse(DocsViewerUrlPolicy.isValidDocsViewerSpaceId("bad/id"));

        String target = "https://api.octo.test/gateway/d/doc-1?sid=android&x=1#page";
        String identityHtml = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                target, "android", "token", "uid", "name", explicitSpace);
        assertOrdered(identityHtml, "localStorage.setItem('currentSpaceId', spaceId);",
                "localStorage.setItem('token'+s");
        String fallbackIdentityHtml = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                target, "android", "token", "uid", "name", missingExtra);
        assertTrue(fallbackIdentityHtml.contains("var spaceId=\"space-global\";"));
        assertOrdered(fallbackIdentityHtml, "localStorage.setItem('currentSpaceId', spaceId);",
                "localStorage.setItem('token'+s");
        String identityWithEmptySpaceHtml = DocsViewerUrlPolicy.buildIdentityBootstrapHtml(
                target, "android", "token", "uid", "name", explicitEmpty);
        assertOrdered(identityWithEmptySpaceHtml, "localStorage.removeItem('currentSpaceId');",
                "localStorage.setItem('token'+s");
        String anonymousHtml = DocsViewerUrlPolicy.buildSpaceBootstrapHtml(target, explicitEmpty);
        assertOrdered(anonymousHtml, "localStorage.removeItem('currentSpaceId');",
                "window.location.replace");
        String anonymousWithSpaceHtml = DocsViewerUrlPolicy.buildSpaceBootstrapHtml(target, explicitSpace);
        assertOrdered(anonymousWithSpaceHtml, "localStorage.setItem('currentSpaceId', spaceId);",
                "window.location.replace");
        String escapedTarget = target.replace("&", "\\u0026");
        assertTrue(identityHtml.contains("window.location.replace(\"" + escapedTarget + "\");"));
        assertTrue(anonymousHtml.contains("window.location.replace(\"" + escapedTarget + "\");"));
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
