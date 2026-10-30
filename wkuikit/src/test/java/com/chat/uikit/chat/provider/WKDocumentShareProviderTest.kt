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

package com.chat.uikit.chat.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse

/**
 * 直接测试 [WKDocumentShareProvider] companion object 中的 isValidDocUrl。
 * 纯 JVM：不实例化 Provider，不触碰 Android 框架方法。
 */
class WKDocumentShareProviderTest {

    @Test
    fun providerAttachFocusAndReattachUseTheExpectedLoadModes() {
        val forceRequests = mutableListOf<Boolean>()
        val lifecycle = DocumentShareLoadLifecycle { forceRequests += it }
        lifecycle.onAttach(true)
        lifecycle.onFocusChanged(true)
        lifecycle.onDetach()
        lifecycle.onAttach(true)
        assertEquals(listOf(false, false), forceRequests)

        lifecycle.onFocusChanged(false)
        lifecycle.onFocusChanged(true)
        assertEquals(listOf(false, false, true), forceRequests)

        lifecycle.onDetach()
        lifecycle.onFocusChanged(true)
        assertEquals(listOf(false, false, true), forceRequests)
    }

    @Test
    fun attachWithoutInitialFocusOnlyRefreshesAfterObservedFocusIsLostAndRestored() {
        val forceRequests = mutableListOf<Boolean>()
        DocumentShareLoadLifecycle { forceRequests += it }.run {
            onAttach(false)
            onFocusChanged(false)
            onFocusChanged(true)
            onFocusChanged(false)
            onFocusChanged(true)
        }
        assertEquals(listOf(false, true), forceRequests)
    }

    @Test
    fun navigationUrlUsesExplicitCompleteDocumentBaseWithoutStrippingPath() {
        assertEquals("https://docs.example.test/d/abc_7", DocumentSharePreviewRepository.navigationUrl("abc_7", "https://docs.example.test/"))
        assertEquals("https://docs.example.test:8443/gateway/api/v1/d/abc", DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test:8443/gateway/api/v1/"))
        assertEquals("https://docs.example.test/tenant/v1/api/v1/d/abc", DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/tenant/v1/api/v1/"))
        assertEquals("https://docs.example.test/gateway%2Fdocs/v1/d/abc", DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/gateway%2Fdocs/v1///"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "http://localhost:8080/api/v1/"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://user@docs.example.test/api/v1/"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/api/v1?tenant=x"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/api/v1#section"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("bad/id", "https://docs.example.test/"))
    }

    @Test
    fun apiBaseUrlValidationPreservesHttpsPathPrefixAndRejectsUnsafeComponents() {
        assertEquals("https://api.example.test/gateway/v1", DocumentSharePreviewRepository.apiBaseUrl("https://api.example.test/gateway/v1/"))
        assertNull(DocumentSharePreviewRepository.apiBaseUrl("http://api.example.test/v1"))
        assertNull(DocumentSharePreviewRepository.apiBaseUrl("https://user@api.example.test/v1"))
        assertNull(DocumentSharePreviewRepository.apiBaseUrl("https://api.example.test/v1?tenant=x"))
        assertNull(DocumentSharePreviewRepository.apiBaseUrl("https://api.example.test/v1#section"))
    }

    @Test
    fun rejectsInvalidUrls() {
        assertNull(DocumentSharePreviewRepository.navigationUrl("", "https://docs.example.test/api/v1/"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("bad/id", "https://docs.example.test/api/v1/"))
        assertTrue(DocumentSharePreviewRepository.navigationUrl("javascript", "https://docs.example.test/api/v1/")!!.endsWith("/d/javascript"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", ""))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://user@docs.example.test/api/v1"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/api/v1?tenant=x"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://docs.example.test/api/v1#section"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "file://docs.example.test/api/v1"))
        assertNull(DocumentSharePreviewRepository.navigationUrl("abc", "https://"))
    }
}
