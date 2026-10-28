package com.chat.uikit.chat.provider

import com.chat.base.config.WKConfig
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
}
