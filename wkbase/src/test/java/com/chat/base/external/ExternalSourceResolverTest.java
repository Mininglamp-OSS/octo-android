package com.chat.base.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import org.junit.Test;

import java.util.HashMap;

/**
 * Unit tests for {@link ExternalSourceResolver}. YUJ-53 was a silent
 * passthrough failure — these tests lock the priority chain and the
 * viewer-relative degradation rules so regressions surface immediately
 * rather than as "DOM count == 0" at runtime.
 */
public class ExternalSourceResolverTest {

    private WKMsg groupMsg() {
        WKMsg msg = new WKMsg();
        msg.channelType = WKChannelType.GROUP;
        msg.channelID = "g_123";
        msg.fromUID = "uid_sender";
        msg.localExtraMap = new HashMap();
        return msg;
    }

    @Test
    public void viewerRelativePriorityReturnsHomeSpaceName() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_B");
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Space B");
        // viewer is in Space A → sender is external → show Space B
        assertEquals("Space B", ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void viewerRelativeSameSpaceReturnsNull() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_A");
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Space A");
        // sender's home Space == viewer's → no suffix
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void legacyFromIsExternalFallbackStillWorks() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, 1);
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Old Space");
        assertEquals("Old Space", ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void legacyExternalButEmptyNameReturnsNull() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, 1);
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "");
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void privateChatReturnsNull() {
        WKMsg msg = groupMsg();
        msg.channelType = WKChannelType.PERSONAL;
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_B");
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Space B");
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void topicSubThreadReturnsNull() {
        WKMsg msg = groupMsg();
        msg.topicID = "topic_abc";
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_B");
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Space B");
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void emptyExtrasReturnsNull() {
        WKMsg msg = groupMsg();
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void nullMsgReturnsNull() {
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(null, "space_A"));
    }

    @Test
    public void homeSpaceIdWithoutNameFallsThroughToLegacy() {
        WKMsg msg = groupMsg();
        // home_space_id set, name missing → viewer is external, but we have no
        // label. Fall through to legacy fields if present.
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_B");
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, "1");
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Fallback Space");
        assertEquals("Fallback Space", ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    /**
     * Inverse of {@link #homeSpaceIdWithoutNameFallsThroughToLegacy}: when
     * {@code home_space_name} is present but {@code home_space_id} is missing,
     * Priority 1 MUST be ignored (we cannot run the viewer-relative check
     * without an id) and the resolver must keep falling through to Priority 2.
     * This locks the semantic so a future refactor that "helpfully" uses the
     * name alone cannot silently break the viewer-relative rule — and also
     * pins the fallback path (Jerry-Xin round 2, B3).
     */
    @Test
    public void homeSpaceNameWithoutIdIsIgnoredAndFallsThroughToLegacy() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Orphan Space");
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, 1);
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Legacy Space");
        // Priority 1 is skipped (no home_space_id) → Priority 2 wins.
        assertEquals("Legacy Space", ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    /**
     * Name-only Priority 1 with no Priority 2 payload must yield null rather
     * than leaking "Orphan Space" as a suffix.
     */
    @Test
    public void homeSpaceNameWithoutIdAndNoLegacyReturnsNull() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Orphan Space");
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void truthyFlagAcceptsStringOne() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, "1");
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Space C");
        assertEquals("Space C", ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void zeroFlagDoesNotTrigger() {
        WKMsg msg = groupMsg();
        msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, 0);
        msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Space C");
        assertNull(ExternalSourceResolver.resolveSourceSpaceName(msg, "space_A"));
    }

    @Test
    public void mergeForwardUserResolverHonorsViewerRelative() {
        com.xinbida.wukongim.entity.WKChannel user = new com.xinbida.wukongim.entity.WKChannel();
        user.channelID = "uid_1";
        user.remoteExtraMap = new HashMap();
        user.remoteExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, "space_B");
        user.remoteExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, "Space B");
        assertEquals("Space B",
                ExternalSourceResolver.resolveMergeForwardUserSpaceName(user, "space_A"));
        assertNull(ExternalSourceResolver.resolveMergeForwardUserSpaceName(user, "space_B"));
    }

    @Test
    public void mergeForwardUserResolverFallsBackToLegacy() {
        com.xinbida.wukongim.entity.WKChannel user = new com.xinbida.wukongim.entity.WKChannel();
        user.channelID = "uid_1";
        user.remoteExtraMap = new HashMap();
        user.remoteExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, 1);
        user.remoteExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Vendor Space");
        assertEquals("Vendor Space",
                ExternalSourceResolver.resolveMergeForwardUserSpaceName(user, "viewer_space"));
    }

    @Test
    public void mergeForwardUserResolverNullSafe() {
        assertNull(ExternalSourceResolver.resolveMergeForwardUserSpaceName(null, "viewer_space"));
        com.xinbida.wukongim.entity.WKChannel user = new com.xinbida.wukongim.entity.WKChannel();
        assertNull(ExternalSourceResolver.resolveMergeForwardUserSpaceName(user, "viewer_space"));
    }

    // ===== YUJ-132 · Reply 预览 overload（primitive 参数） =====

    @Test
    public void replyOverload_viewerRelativeHomeSpacePriority() {
        // viewer 在 space_A，被回复用户 home=space_B → 渲染 "Space B"
        assertEquals("Space B",
                ExternalSourceResolver.resolveSourceSpaceName(
                        "space_B", "Space B", 0, null, "space_A"));
    }

    @Test
    public void replyOverload_sameHomeSpaceReturnsNull() {
        // 被回复用户 home == viewer 当前 Space → 不加后缀，即便 source_space_name 非空
        assertNull(
                ExternalSourceResolver.resolveSourceSpaceName(
                        "space_A", "Space A", 1, "Space Alt", "space_A"));
    }

    @Test
    public void replyOverload_fallsBackToAbsoluteWhenHomeSpaceMissing() {
        // 老数据：只有 from_is_external + from_source_space_name
        assertEquals("Vendor",
                ExternalSourceResolver.resolveSourceSpaceName(
                        null, null, 1, "Vendor", "space_A"));
    }

    @Test
    public void replyOverload_allFieldsAbsentReturnsNull() {
        assertNull(
                ExternalSourceResolver.resolveSourceSpaceName(
                        null, null, 0, null, "space_A"));
        assertNull(
                ExternalSourceResolver.resolveSourceSpaceName(
                        "", "", 0, "", ""));
    }

    @Test
    public void replyOverload_isExternalZeroWithNamePresentReturnsNull() {
        // from_is_external=0 表示同 Space，即便 source name 非空也不应渲染
        assertNull(
                ExternalSourceResolver.resolveSourceSpaceName(
                        null, null, 0, "Some Space", "space_A"));
    }

    @Test
    public void replyOverload_viewerSpaceIdEmptyStillRenders() {
        // 尚未切 Space（viewerSpaceId 为空）→ 没有参照系，降级为 "有 home_space_name 就渲染"
        assertEquals("Space B",
                ExternalSourceResolver.resolveSourceSpaceName(
                        "space_B", "Space B", 0, null, ""));
        assertEquals("Space B",
                ExternalSourceResolver.resolveSourceSpaceName(
                        "space_B", "Space B", 0, null, null));
    }
}
