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

package com.chat.uikit.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 *  I02a：名片联系方式展示策略单测（对齐 iOS WKUserInfoContactTests 清单）。
 * 纯 JUnit，无 Robolectric —— ContactInfoPolicy 无 Android 依赖。
 */
public class ContactInfoPolicyTest {

    // ==================== sectionEligible ====================

    @Test
    public void switchOff_hidesForSelfAndOthers() {
        assertFalse(ContactInfoPolicy.sectionEligible(false, true, false, 0));
        assertFalse(ContactInfoPolicy.sectionEligible(false, false, false, 0));
    }

    @Test
    public void robotNeverShown() {
        assertFalse(ContactInfoPolicy.sectionEligible(true, false, true, 0));
        assertFalse(ContactInfoPolicy.sectionEligible(true, true, true, 0));
    }

    @Test
    public void otherUserDestroyStates() {
        assertFalse(ContactInfoPolicy.sectionEligible(true, false, false, 1));
        assertFalse(ContactInfoPolicy.sectionEligible(true, false, false, 2));
        assertTrue(ContactInfoPolicy.sectionEligible(true, false, false, 0));
    }

    @Test
    public void selfVisibleEvenWhenCoolingOffOrDeleted() {
        assertTrue(ContactInfoPolicy.sectionEligible(true, true, false, 1));
        assertTrue(ContactInfoPolicy.sectionEligible(true, true, false, 2));
    }

    // ==================== phoneRowMode ====================

    @Test
    public void phoneNull_hidden() {
        assertEquals(ContactInfoPolicy.RowMode.HIDDEN,
                ContactInfoPolicy.phoneRowMode(null, false, false));
        assertEquals(ContactInfoPolicy.RowMode.HIDDEN,
                ContactInfoPolicy.phoneRowMode(null, true, false));
    }

    @Test
    public void phoneEmpty_placeholder() {
        assertEquals(ContactInfoPolicy.RowMode.PLACEHOLDER,
                ContactInfoPolicy.phoneRowMode("", false, false));
    }

    @Test
    public void phoneOtherNotRevealed_tapToView() {
        assertEquals(ContactInfoPolicy.RowMode.TAP_TO_VIEW,
                ContactInfoPolicy.phoneRowMode("13800000000", false, false));
    }

    @Test
    public void phoneOtherRevealed_fullValue() {
        assertEquals(ContactInfoPolicy.RowMode.FULL_VALUE,
                ContactInfoPolicy.phoneRowMode("13800000000", false, true));
    }

    @Test
    public void phoneSelf_fullValueRegardlessOfRevealed() {
        assertEquals(ContactInfoPolicy.RowMode.FULL_VALUE,
                ContactInfoPolicy.phoneRowMode("13800000000", true, false));
        assertEquals(ContactInfoPolicy.RowMode.FULL_VALUE,
                ContactInfoPolicy.phoneRowMode("13800000000", true, true));
    }

    // ==================== emailRowMode ====================

    @Test
    public void emailNull_hidden() {
        assertEquals(ContactInfoPolicy.RowMode.HIDDEN, ContactInfoPolicy.emailRowMode(null));
    }

    @Test
    public void emailEmpty_placeholder() {
        assertEquals(ContactInfoPolicy.RowMode.PLACEHOLDER, ContactInfoPolicy.emailRowMode(""));
    }

    @Test
    public void emailNonEmpty_fullValue() {
        assertEquals(ContactInfoPolicy.RowMode.FULL_VALUE,
                ContactInfoPolicy.emailRowMode("a@b.com"));
    }

    // ==================== normalizeDialCode ====================

    @Test
    public void normalize_countryCodeVariants() {
        assertEquals("86", ContactInfoPolicy.normalizeDialCode("86", null));
        assertEquals("86", ContactInfoPolicy.normalizeDialCode("+86", null));
        assertEquals("86", ContactInfoPolicy.normalizeDialCode(null, "+86"));
        assertEquals("86", ContactInfoPolicy.normalizeDialCode(null, "0086"));
        assertEquals("86", ContactInfoPolicy.normalizeDialCode(null, "86"));
    }

    @Test
    public void normalize_missing_returnsEmpty() {
        assertEquals("", ContactInfoPolicy.normalizeDialCode(null, null));
        assertEquals("", ContactInfoPolicy.normalizeDialCode(null, ""));
    }

    @Test
    public void normalize_emptyCountryCode_fallsBackToZone() {
        assertEquals("86", ContactInfoPolicy.normalizeDialCode("", "0086"));
    }

    // ==================== selfCardPhoneDisplay ====================

    @Test
    public void selfCard_withDialCode() {
        assertEquals("+86 13800000000",
                ContactInfoPolicy.selfCardPhoneDisplay("13800000000", "86", null));
    }

    @Test
    public void selfCard_noDialCode_returnsBareNumber() {
        assertEquals("13800000000",
                ContactInfoPolicy.selfCardPhoneDisplay("13800000000", null, null));
        assertEquals("13800000000",
                ContactInfoPolicy.selfCardPhoneDisplay("13800000000", null, ""));
    }

    // ==================== meInfoPhoneDisplay ====================

    @Test
    public void meInfo_withZone() {
        assertEquals("(+86) 13800000000",
                ContactInfoPolicy.meInfoPhoneDisplay("13800000000", "0086"));
    }

    @Test
    public void meInfo_emptyZone_returnsBareNumber() {
        assertEquals("13800000000",
                ContactInfoPolicy.meInfoPhoneDisplay("13800000000", ""));
        assertEquals("13800000000",
                ContactInfoPolicy.meInfoPhoneDisplay("13800000000", null));
    }
}
