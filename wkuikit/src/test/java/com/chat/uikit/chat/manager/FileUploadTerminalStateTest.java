package com.chat.uikit.chat.manager;

import com.chat.base.net.ud.WKProgressManager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileUploadTerminalStateTest {
    @Test public void terminalClaimIsOnceOnlyAcrossRepeatedCallbacks() {
        WKSendMsgUtils.FileUploadTerminal terminal = new WKSendMsgUtils.FileUploadTerminal();
        int[] effects = {0};
        assertTrue(terminal.finish(() -> effects[0]++));
        assertTrue(terminal.isTerminal());
        assertFalse(terminal.finish(() -> effects[0]++));
        assertEquals(1, effects[0]);
    }

    @Test public void cancellationPreventsLateSuccessMutationAndPutInitiation() {
        WKSendMsgUtils.FileUploadTerminal terminal = new WKSendMsgUtils.FileUploadTerminal();
        int[] putStarts = {0};
        int[] urlWrites = {0};
        assertTrue(terminal.finish(() -> { }));
        assertFalse(terminal.runIfActive(() -> putStarts[0]++));
        assertFalse(terminal.finish(() -> urlWrites[0]++));
        assertEquals(0, putStarts[0]);
        assertEquals(0, urlWrites[0]);
    }

    @Test public void staleFailureForSameSequenceDoesNotRemoveNewAccountProgress() {
        WKProgressManager manager = WKProgressManager.Companion.getInstance();
        WKProgressManager.FileUploadProgressKey oldKey = new WKProgressManager.FileUploadProgressKey("account-a", 4L, "msg-a", 73L);
        WKProgressManager.FileUploadProgressKey newKey = new WKProgressManager.FileUploadProgressKey("account-b", 5L, "msg-b", 73L);
        int[] oldFailures = {0};
        int[] newFailures = {0};
        int[] newSuccesses = {0};
        manager.registerProgress(oldKey, new TestProgress(oldFailures, new int[1]));
        manager.registerProgress(newKey, new TestProgress(newFailures, newSuccesses));
        try {
            manager.completeUploadFailure(oldKey, "stale");
            manager.completeUploadSuccess(newKey, null);
            assertEquals(1, oldFailures[0]);
            assertEquals(0, newFailures[0]);
            assertEquals(1, newSuccesses[0]);
        } finally {
            manager.unregisterProgress(oldKey);
            manager.unregisterProgress(newKey);
        }
    }

    @Test public void fileUploadTaskSurvivesListenerRemovalAndIsVisibleToReboundRows() {
        WKProgressManager manager = WKProgressManager.Companion.getInstance();
        WKProgressManager.FileUploadProgressKey key = new WKProgressManager.FileUploadProgressKey("task-account", 91L, "task-msg", 17L);
        int[] observedStarts = {0};
        WKProgressManager.IProgress first = new TestProgress(new int[1], new int[1]) {
            @Override public void onProgress(Object tag, int progress) { observedStarts[0]++; }
        };
        try {
            manager.registerProgress(key, first);
            assertTrue(manager.beginFileUpload(key));
            assertEquals(1, observedStarts[0]);
            manager.unregisterProgress(key, first);
            assertEquals(0, manager.getFileUploadTask(key).getProgress());

            int[] reboundProgress = {-1};
            manager.registerProgress(key, new WKProgressManager.IProgress() {
                @Override public void onProgress(Object tag, int progress) { reboundProgress[0] = progress; }
                @Override public void onSuccess(Object tag, String path) { }
                @Override public void onFail(Object tag, String message) { }
            });
            assertEquals(0, reboundProgress[0]);
            manager.completeUploadFailure(key, "done");
            assertEquals(null, manager.getFileUploadTask(key));
        } finally {
            manager.completeUploadFailure(key, "cleanup");
            manager.unregisterProgress(key);
        }
    }

    private static class TestProgress implements WKProgressManager.IProgress {
        private final int[] failures;
        private final int[] successes;
        TestProgress(int[] failures, int[] successes) { this.failures = failures; this.successes = successes; }
        @Override public void onProgress(Object tag, int progress) { }
        @Override public void onSuccess(Object tag, String path) { successes[0]++; }
        @Override public void onFail(Object tag, String message) { failures[0]++; }
    }
}
