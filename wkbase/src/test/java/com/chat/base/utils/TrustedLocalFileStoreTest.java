package com.chat.base.utils;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class TrustedLocalFileStoreTest {
    @Test
    public void identityIncludesAccountServerChannelAndMessage() {
        String identity = TrustedLocalFileStore.identityKey("alice", "https://one.example/v1/", (byte) 2, "room", "message-1");

        assertNotEquals(identity, TrustedLocalFileStore.identityKey("bob", "https://one.example/v1/", (byte) 2, "room", "message-1"));
        assertNotEquals(identity, TrustedLocalFileStore.identityKey("alice", "https://two.example/v1/", (byte) 2, "room", "message-1"));
        assertNotEquals(identity, TrustedLocalFileStore.identityKey("alice", "https://one.example/v1/", (byte) 1, "room", "message-1"));
        assertNotEquals(identity, TrustedLocalFileStore.identityKey("alice", "https://one.example/v1/", (byte) 2, "other", "message-1"));
        assertNotEquals(identity, TrustedLocalFileStore.identityKey("alice", "https://one.example/v1/", (byte) 2, "room", "message-2"));
    }
}
