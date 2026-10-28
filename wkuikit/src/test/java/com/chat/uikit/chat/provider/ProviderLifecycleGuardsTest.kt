package com.chat.uikit.chat.provider

import com.chat.base.config.WKConfig
import com.chat.base.msgcontent.WKFileContent
import com.chat.base.net.ud.WKProgressManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLifecycleGuardsTest {
    @Test fun downloadPersistenceSpyRunsOnlyForCapturedAccountAndHelper() {
        val accountA = WKConfig.SessionSnapshot(3, "A", "token-a")
        val accountB = WKConfig.SessionSnapshot(4, "B", "token-b")
        var writes = 0
        // Detachment is deliberately absent from this persistence gate: a valid account-owned
        // download must still update its own row after the recycled view leaves the window.
        assertFalse(FileDownloadSessionGuard.persistIfCurrent(accountA, accountB, "B", false) { writes++ })
        assertFalse(FileDownloadSessionGuard.persistIfCurrent(accountA, accountA, "A", false) { writes++ })
        assertEquals(0, writes)
        assertTrue(FileDownloadSessionGuard.persistIfCurrent(accountA, accountA, "A", true) { writes++ })
        assertEquals(1, writes)
    }

    @Test fun uploadTerminalSuccessAndFailureCannotRegisterAgainAfterReattach() {
        val success = UploadProgressRegistration()
        val progressListener = object : WKProgressManager.IProgress {
            override fun onProgress(tag: Any?, progress: Int) = Unit
            override fun onSuccess(tag: Any?, path: String?) = Unit
            override fun onFail(tag: Any?, msg: String?) = Unit
        }
        success.listener = progressListener
        success.registered = true
        assertEquals(UploadProgressRegistration.TerminalResult.ignored, success.complete(8L, 7L, WKFileContent().apply { url = "https://files.test/a" }))
        assertTrue(success.registered)
        assertEquals(UploadProgressRegistration.TerminalResult.success, success.complete(7L, 7L, WKFileContent().apply { url = "https://files.test/a" }))
        assertFalse(success.canRegister())
        assertFalse(success.registered)
        assertEquals(null, success.listener)

        val failure = UploadProgressRegistration().apply { this.listener = progressListener; registered = true }
        assertEquals(UploadProgressRegistration.TerminalResult.failed, failure.complete(7L, 7L, WKFileContent()))
        assertFalse(failure.canRegister())
    }
}
