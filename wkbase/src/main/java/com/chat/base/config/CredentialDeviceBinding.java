package com.chat.base.config;

import com.chat.base.utils.WKCacheKeys;

/** Bind a device category to the credentials that were actually issued for it. */
public final class CredentialDeviceBinding {
    private CredentialDeviceBinding() {}

    public static String fingerprint(String uid, String token, String imToken) {
        return WKCacheKeys.sha256Hex(part(uid) + part(token) + part(imToken));
    }

    private static String part(String value) {
        if (value == null) value = "";
        return value.length() + ":" + value;
    }

    public static boolean matches(String savedFlag, String savedBinding, int currentFlag,
                                  String uid, String token, String imToken) {
        // Production before category migration did not write either field and used 0.
        if (savedFlag == null || savedFlag.isEmpty()) return currentFlag == 0;
        return String.valueOf(currentFlag).equals(savedFlag)
                && fingerprint(uid, token, imToken).equals(savedBinding);
    }
}
