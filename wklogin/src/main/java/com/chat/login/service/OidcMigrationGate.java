package com.chat.login.service;

/** Called on the UI thread for both cached and newly fetched provider configuration. */
public final class OidcMigrationGate {
    public interface PendingLogin {
        String consumeUid();
    }

    public interface Starter {
        void start(String uid);
    }

    private final PendingLogin pending;

    public OidcMigrationGate(PendingLogin pending) {
        this.pending = pending;
    }

    public void tryStart(boolean providerReady, boolean requestPending, boolean alreadyLoggedIn,
                         Starter starter) {
        if (alreadyLoggedIn) {
            pending.consumeUid();
            return;
        }
        // Missing/stale configuration has not used up the one automatic attempt.
        if (!providerReady || requestPending) return;
        String uid = pending.consumeUid();
        if (uid != null && !uid.isEmpty()) starter.start(uid);
    }

    public void cancel() {
        pending.consumeUid();
    }
}
