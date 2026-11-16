package com.chat.login.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class OidcUrlPolicyTest {
    @Test public void httpApiCannotCarryRelativeAuthorizationOrAuthCodeRequests() {
        assertFalse(OidcUrlPolicy.isHttpsUrl("http://api.example/v1/"));
        assertThrows(IllegalArgumentException.class, () ->
                OidcUrlPolicy.resolveAuthorizeUrl("http://api.example/v1/", "/v1/auth/oidc/authorize"));
        assertThrows(IllegalArgumentException.class, () ->
                OidcUrlPolicy.resolveAuthorizeUrl("http://api.example/v1/", "auth/oidc/authorize"));
        // A secure authorize endpoint must not conceal an insecure authcode/status API.
        assertThrows(IllegalArgumentException.class, () ->
                OidcUrlPolicy.resolveAuthorizeUrl("http://api.example/v1/", "https://sso.example/authorize"));
    }

    @Test public void httpsBaseResolvesBothSupportedRelativeForms() {
        assertEquals("https://api.example:8443/auth/oidc/authorize",
                OidcUrlPolicy.resolveAuthorizeUrl("https://api.example:8443/v1/", "/auth/oidc/authorize"));
        assertEquals("https://api.example/v1/auth/oidc/authorize",
                OidcUrlPolicy.resolveAuthorizeUrl("https://api.example/v1/", "auth/oidc/authorize"));
        assertEquals("https://sso.example/authorize?tenant=one",
                OidcUrlPolicy.resolveAuthorizeUrl("https://api.example/v1/", "https://sso.example/authorize?tenant=one"));
    }

    @Test public void finalAndInitialLoadChecksRejectUnsafeUrls() {
        String[] invalid = {null, "", "http://api.example/?authcode=secret", "https:///missing-host",
                "https://user:password@api.example/authorize", "https://api.example/#authcode=secret",
                "https://api.example:0/", "https://api.example:65536/", "javascript:alert(1)",
                "https://api.example\\@evil.example/", "https://api.example/\n"};
        for (String value : invalid) assertFalse(String.valueOf(value), OidcUrlPolicy.isHttpsUrl(value));
        assertTrue(OidcUrlPolicy.isHttpsUrl("HTTPS://api.example/authorize?authcode=a%26b%3Dc"));
    }

    @Test public void authorizingCannotDowngradeOrUseProtocolRelativePaths() {
        for (String path : new String[]{"http://sso.example/", "HTTP://sso.example/", "//sso.example/", "", "https:///broken"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    OidcUrlPolicy.resolveAuthorizeUrl("https://api.example/v1/", path));
        }
    }
}
