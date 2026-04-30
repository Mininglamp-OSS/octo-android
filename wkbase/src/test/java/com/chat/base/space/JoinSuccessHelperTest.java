package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * YUJ-140 · 跨 Space 加群 Toast — host-side 单元测试。
 *
 * <p>覆盖 {@link JoinSuccessHelper} 的 crossSpace 判定、序列化、消费清空、以及
 * 畸形 JSON 的 fail-open 行为。所有测试通过 {@link InMemoryStore} 注入，纯 JVM。
 */
public class JoinSuccessHelperTest {

    /** In-memory 存根，模拟 SharedPreferences 读写行为。*/
    private static final class InMemoryStore implements JoinSuccessHelper.NoticeStore {
        String buffer;
        int reads;
        int writes;

        @Override
        public String read() {
            reads++;
            return buffer;
        }

        @Override
        public void write(String json) {
            writes++;
            this.buffer = json;
        }
    }

    // ------------------------------------------------------------------
    // crossSpace 判定
    // ------------------------------------------------------------------

    @Test
    public void computeAndSave_marksCrossSpace_whenTargetDiffersFromViewer() {
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "External Group", "space_B", "OctoWork",
                "space_A", store);

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(notice);
        assertTrue(notice.crossSpace);
        assertEquals("g1", notice.groupNo);
        assertEquals("External Group", notice.groupName);
        assertEquals("space_B", notice.targetSpaceId);
        assertEquals("OctoWork", notice.targetSpaceName);
        assertEquals("space_A", notice.viewerSpaceId);
    }

    @Test
    public void computeAndSave_notCrossSpace_whenTargetEqualsViewer() {
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "Group", "space_A", "测试空间",
                "space_A", store);

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(notice);
        assertFalse(notice.crossSpace);
    }

    @Test
    public void computeAndSave_notCrossSpace_whenTargetEmpty() {
        // 后端未返回 space_id（Space 模式缺字段）→ fail-open 成同 Space 行为
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "Group", "", "",
                "space_A", store);

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(notice);
        assertFalse(notice.crossSpace);
    }

    @Test
    public void computeAndSave_notCrossSpace_whenViewerEmpty() {
        // 非 Space 模式（用户未选择任何 Space）→ 不构成跨空间
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "Group", "space_B", "Other",
                "", store);

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(notice);
        assertFalse(notice.crossSpace);
    }

    @Test
    public void computeAndSave_notCrossSpace_whenViewerNull() {
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "Group", "space_B", "Other",
                null, store);

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(notice);
        assertFalse(notice.crossSpace);
    }

    // ------------------------------------------------------------------
    // consume 清空行为
    // ------------------------------------------------------------------

    @Test
    public void consumeNotice_readsOnceAndClears() {
        InMemoryStore store = new InMemoryStore();
        JoinSuccessHelper.computeAndSave("g1", "G", "space_B", "B",
                "space_A", store);

        JoinSuccessHelper.JoinNotice first = JoinSuccessHelper.consumeNotice(store);
        assertNotNull(first);

        // 第二次 consume 必须返回 null（已清空），避免跨 resume 反复弹 Dialog
        JoinSuccessHelper.JoinNotice second = JoinSuccessHelper.consumeNotice(store);
        assertNull(second);

        // 至少一次 write("") 清空
        assertTrue("consumeNotice should write empty string to clear", store.writes >= 2);
    }

    @Test
    public void consumeNotice_returnsNull_whenBufferEmpty() {
        InMemoryStore store = new InMemoryStore();
        assertNull(JoinSuccessHelper.consumeNotice(store));
    }

    @Test
    public void consumeNotice_returnsNull_andClears_whenBufferMalformed() {
        InMemoryStore store = new InMemoryStore();
        store.buffer = "{not-valid-json";

        JoinSuccessHelper.JoinNotice notice = JoinSuccessHelper.consumeNotice(store);
        assertNull(notice);
        // 畸形 JSON 也必须清空，避免死循环
        assertEquals("", store.buffer);
    }

    // ------------------------------------------------------------------
    // JSON round-trip
    // ------------------------------------------------------------------

    @Test
    public void noticeJson_roundTripPreservesFields() {
        JoinSuccessHelper.JoinNotice original = new JoinSuccessHelper.JoinNotice(
                "g1", "「外部群」with quotes", "space_B", "OctoWork Space",
                "space_A", true);
        String raw = original.toJson().toString();
        JoinSuccessHelper.JoinNotice parsed = JoinSuccessHelper.JoinNotice.fromJson(raw);

        assertNotNull(parsed);
        assertEquals(original.groupNo, parsed.groupNo);
        assertEquals(original.groupName, parsed.groupName);
        assertEquals(original.targetSpaceId, parsed.targetSpaceId);
        assertEquals(original.targetSpaceName, parsed.targetSpaceName);
        assertEquals(original.viewerSpaceId, parsed.viewerSpaceId);
        assertEquals(original.crossSpace, parsed.crossSpace);
    }

    @Test
    public void noticeFromJson_tolerateMissingFields() {
        JoinSuccessHelper.JoinNotice parsed =
                JoinSuccessHelper.JoinNotice.fromJson("{\"group_no\":\"g1\"}");
        assertNotNull(parsed);
        assertEquals("g1", parsed.groupNo);
        // 缺字段 → 空串而非 null，防止下游 NPE
        assertEquals("", parsed.groupName);
        assertEquals("", parsed.targetSpaceId);
        assertEquals("", parsed.targetSpaceName);
        assertFalse(parsed.crossSpace);
    }

    @Test
    public void noticeNullOrEmptyArgs_normalizedToEmptyString() {
        JoinSuccessHelper.JoinNotice n = new JoinSuccessHelper.JoinNotice(
                null, null, null, null, null, false);
        assertEquals("", n.groupNo);
        assertEquals("", n.groupName);
        assertEquals("", n.targetSpaceId);
        assertEquals("", n.targetSpaceName);
        assertEquals("", n.viewerSpaceId);
    }
}
