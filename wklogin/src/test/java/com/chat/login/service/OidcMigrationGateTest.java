package com.chat.login.service;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OidcMigrationGateTest {
    private static class Pending implements OidcMigrationGate.PendingLogin {
        String uid = "original-user";
        int consumed;
        public String consumeUid() {
            consumed++;
            String result = uid;
            uid = "";
            return result;
        }
    }

    @Test public void missingOrStaleCacheWaitsForUsableNetworkConfig() {
        Pending pending = new Pending();
        OidcMigrationGate gate = new OidcMigrationGate(pending);
        List<String> attempts = new ArrayList<>();
        gate.tryStart(false, false, false, attempts::add); // Missing cached provider.
        gate.tryStart(false, false, false, attempts::add); // Unusable/stale provider.
        assertEquals("original-user", pending.uid);
        assertEquals(0, pending.consumed);
        assertTrue(attempts.isEmpty());
        gate.tryStart(true, false, false, attempts::add); // Fresh SSO config.
        assertEquals(1, attempts.size());
        assertEquals("original-user", attempts.get(0));
        assertEquals("", pending.uid);
    }

    @Test public void repeatedConfigAndFailureDoNotRetryTheAttempt() {
        Pending pending = new Pending();
        OidcMigrationGate gate = new OidcMigrationGate(pending);
        List<String> attempts = new ArrayList<>();
        gate.tryStart(true, false, false, attempts::add);
        gate.tryStart(true, true, false, attempts::add);
        // A failed/timed-out attempt is complete; a later configuration callback is not a retry.
        gate.tryStart(true, false, false, attempts::add);
        new OidcMigrationGate(pending).tryStart(true, false, false, attempts::add);
        assertEquals(1, attempts.size());
    }

    @Test public void busyRequestDoesNotConsumePendingMigration() {
        Pending pending = new Pending();
        OidcMigrationGate gate = new OidcMigrationGate(pending);
        gate.tryStart(true, true, false, uid -> fail("Must wait"));
        assertEquals(0, pending.consumed);
        assertEquals("original-user", pending.uid);
    }

    @Test public void manualLoginAndExistingSessionSuppressLateConfigCallbacks() {
        Pending pending = new Pending();
        OidcMigrationGate gate = new OidcMigrationGate(pending);
        gate.cancel();
        gate.tryStart(true, false, false, uid -> fail("Manual login takes precedence"));
        pending.uid = "original-user";
        gate.tryStart(true, false, true, uid -> fail("Already logged in"));
        assertEquals("", pending.uid);
    }
}
