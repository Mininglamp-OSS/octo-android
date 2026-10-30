package com.chat.uikit.chat.provider

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WKApiConfigDocumentBaseTest {
    @Test fun documentConfigurationPriorityAndChangesInvalidateSessionContextOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baseApplication = WKBaseApplication.getInstance()
        val originalApplication = baseApplication.application
        baseApplication.application = context.applicationContext as Application
        val originalApi = WKApiConfig.baseUrl
        val originalWeb = WKApiConfig.baseWebUrl
        val originalDocument = WKApiConfig.getDocumentBaseUrl()
        val documentField = WKApiConfig::class.java.getDeclaredField("documentBaseUrl").apply { isAccessible = true }
        try {
            val config = WKConfig.getInstance()
            val startVersion = config.sessionSnapshot.version

            WKApiConfig.initBaseURL("https://api-default.test", "https://web-default.test/web/", "https://docs-default.test/root/")
            assertEquals("https://docs-default.test/root", WKApiConfig.getDocumentBaseUrl())
            assertEquals(startVersion + 1, config.sessionSnapshot.version)

            WKApiConfig.initBaseURL("https://api-default.test", "https://web-default.test/web/", "https://docs-default.test/root///")
            assertEquals(startVersion + 1, config.sessionSnapshot.version)

            WKApiConfig.initBaseURL("https://api-default.test", "https://web-default.test/web/", "https://docs-default.test/other/")
            assertEquals("https://docs-default.test/other", WKApiConfig.getDocumentBaseUrl())
            assertEquals(startVersion + 2, config.sessionSnapshot.version)

            WKApiConfig.initBaseURL("https://api-custom.test")
            assertEquals("https://api-custom.test", WKApiConfig.getDocumentBaseUrl())
            assertEquals(startVersion + 3, config.sessionSnapshot.version)
            WKApiConfig.initBaseURLIncludeIP("https://api-custom-2.test")
            assertEquals("https://api-custom-2.test", WKApiConfig.getDocumentBaseUrl())
            assertEquals(startVersion + 4, config.sessionSnapshot.version)

            WKApiConfig.initBaseURL("https://api-default.test", "https://web-default.test/web/", "https://docs-default.test/root/")
            assertEquals("https://docs-default.test/root", WKApiConfig.getDocumentBaseUrl())
        } finally {
            WKApiConfig.baseUrl = originalApi
            WKApiConfig.baseWebUrl = originalWeb
            documentField.set(null, originalDocument)
            WKConfig.getInstance().invalidateSessionContext()
            baseApplication.application = originalApplication
        }
    }
}
