package com.chat.base.config;

import org.junit.Test;
import static org.junit.Assert.*;

public class CredentialDeviceBindingTest {
    @Test public void downgradeLoginCannotReuseStaleCategoryFour() {
        String binding = CredentialDeviceBinding.fingerprint("u", "four-http", "four-im");
        assertTrue(CredentialDeviceBinding.matches("4", binding, 4, "u", "four-http", "four-im"));
        assertFalse(CredentialDeviceBinding.matches("4", binding, 4, "u", "zero-http", "zero-im"));
    }

    @Test public void eachCredentialAndAccountMustMatch() {
        String binding = CredentialDeviceBinding.fingerprint("u", "http", "im");
        assertFalse(CredentialDeviceBinding.matches("4", binding, 4, "other", "http", "im"));
        assertFalse(CredentialDeviceBinding.matches("4", binding, 4, "u", "new", "im"));
        assertFalse(CredentialDeviceBinding.matches("4", binding, 4, "u", "http", "new"));
        assertFalse(CredentialDeviceBinding.matches("4", binding, 0, "u", "http", "im"));
    }

    @Test public void unboundExperimentalCategoryMustReauthenticate() {
        assertFalse(CredentialDeviceBinding.matches("4", "", 4, "u", "http", "im"));
        assertFalse(CredentialDeviceBinding.matches("", "", 4, "u", "http", "im"));
        assertTrue(CredentialDeviceBinding.matches("", "", 0, "u", "http", "im"));
    }
}
