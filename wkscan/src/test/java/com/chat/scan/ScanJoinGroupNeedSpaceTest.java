package com.chat.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.chat.base.space.PendingGroupInvite;

import org.junit.Before;
import org.junit.Test;

/**
 * YUJ-372 Phase 2 · Android 端 {@code need_space} 处理（dmworkim#1319）· host-side 单测。
 *
 * <p>覆盖后端契约 <code>{"status":"need_space","msg":"..."}</code> 的识别、
 * pending 入群上下文的落盘 + JSON 序列化 + 一次性消费（重试）语义。
 *
 * <p>Activity 层本身依赖 Android framework 无法在 host 上实例化；本测试通过
 * {@link PendingGroupInvite} 的公共 API + 注入 {@link PendingGroupInvite.Store}
 * 锁定契约所有分支，覆盖 Activity 调用路径上的关键决策点（onSuccess →
 * isNeedSpaceResponse → save → consume → 重启自己）。
 */
public class ScanJoinGroupNeedSpaceTest {

    private InMemoryStore store;

    private static final class InMemoryStore implements PendingGroupInvite.Store {
        String data;
        @Override public String read() { return data; }
        @Override public void write(String json) { this.data = json; }
    }

    @Before
    public void setUp() {
        store = new InMemoryStore();
    }

    // ------------------------------------------------------------------
    // isNeedSpaceResponse — 后端契约识别
    // ------------------------------------------------------------------

    @Test
    public void isNeedSpaceResponse_matchesStatusField() {
        String body = "{\"status\":\"need_space\",\"msg\":\"请先加入一个 Space 后再入群\"}";
        assertTrue(PendingGroupInvite.isNeedSpaceResponse(body));
    }

    @Test
    public void isNeedSpaceResponse_rejectsNormalScanJoinPayload() {
        // YUJ-200 Path B 正常响应（无 status 或 status != need_space）— 不拦截
        String body = "{\"space_id\":\"s_A\",\"space_name\":\"SA\",\"group_name\":\"G\"}";
        assertFalse(PendingGroupInvite.isNeedSpaceResponse(body));
    }

    @Test
    public void isNeedSpaceResponse_rejectsOtherStatus() {
        String body = "{\"status\":\"ok\"}";
        assertFalse(PendingGroupInvite.isNeedSpaceResponse(body));
    }

    @Test
    public void isNeedSpaceResponse_emptyOrNull_failOpen() {
        // 空 body / null body 视为正常响应 — 不拦截，走既有分支
        assertFalse(PendingGroupInvite.isNeedSpaceResponse(null));
        assertFalse(PendingGroupInvite.isNeedSpaceResponse(""));
    }

    @Test
    public void isNeedSpaceResponse_malformedJson_failOpen() {
        // 解析失败视为普通响应 — fail-open，不误拦截
        assertFalse(PendingGroupInvite.isNeedSpaceResponse("this is not json"));
        assertFalse(PendingGroupInvite.isNeedSpaceResponse("{broken"));
    }

    @Test
    public void isNeedSpaceResponse_caseSensitive_onStatusValue() {
        // status 值大小写敏感，防止后端意外大写触发（契约是小写 need_space）
        assertFalse(PendingGroupInvite.isNeedSpaceResponse("{\"status\":\"NEED_SPACE\"}"));
    }

    // ------------------------------------------------------------------
    // save + peek + consume — pending 落盘 / 重试 语义
    // ------------------------------------------------------------------

    @Test
    public void save_thenPeek_returnsSameFields() {
        PendingGroupInvite.Pending p = new PendingGroupInvite.Pending(
                "g_123", "auth_xyz", "Demo Group", "avatar.png",
                42, false, "s_external", "External Space");
        PendingGroupInvite.save(p, store);

        PendingGroupInvite.Pending got = PendingGroupInvite.peek(store);
        assertNotNull(got);
        assertEquals("g_123", got.groupNo);
        assertEquals("auth_xyz", got.authCode);
        assertEquals("Demo Group", got.groupName);
        assertEquals("avatar.png", got.avatar);
        assertEquals(42, got.memberCount);
        assertFalse(got.isMember);
        assertEquals("s_external", got.spaceId);
        assertEquals("External Space", got.spaceName);
    }

    @Test
    public void peek_doesNotClearStore() {
        PendingGroupInvite.save(newSimplePending(), store);
        PendingGroupInvite.peek(store);
        assertNotNull("peek must not clear the pending entry",
                PendingGroupInvite.peek(store));
    }

    @Test
    public void consume_returnsPayloadAndClears() {
        PendingGroupInvite.save(newSimplePending(), store);
        PendingGroupInvite.Pending first = PendingGroupInvite.consume(store);
        assertNotNull(first);
        assertEquals("g_1", first.groupNo);

        // 第二次 consume 必须返回 null（一次性消费语义，重试不重复触发）
        assertNull(PendingGroupInvite.consume(store));
    }

    @Test
    public void consume_emptyStore_returnsNull() {
        assertNull(PendingGroupInvite.consume(store));
    }

    @Test
    public void consume_clearsEvenWhenPayloadMalformed() {
        // 脏数据：不是合法 JSON 对象。consume 应吃掉并返回 null，
        // 避免反复触发 need_space 路径。
        store.write("this is not json");
        assertNull(PendingGroupInvite.consume(store));
        // 读完即清
        assertNull(PendingGroupInvite.peek(store));
    }

    @Test
    public void fromJson_missingGroupNo_returnsNull() {
        // groupNo 是重试主键；缺失时认为 payload 无效，不返回半残 Pending
        store.write("{\"auth_code\":\"x\"}");
        assertNull(PendingGroupInvite.peek(store));
    }

    @Test
    public void save_overwritesPreviousPending() {
        // 同一用户连续扫两个需要 need_space 的群 — 后者覆盖前者
        PendingGroupInvite.save(newSimplePending("g_first"), store);
        PendingGroupInvite.save(newSimplePending("g_second"), store);
        PendingGroupInvite.Pending got = PendingGroupInvite.consume(store);
        assertNotNull(got);
        assertEquals("g_second", got.groupNo);
    }

    @Test
    public void pendingConstructor_nullSafe() {
        // Activity 侧 getIntent().getStringExtra 对缺字段返回 null — Pending 必须兜底成 ""
        PendingGroupInvite.Pending p = new PendingGroupInvite.Pending(
                "g_1", null, null, null, 0, false, null, null);
        assertEquals("", p.authCode);
        assertEquals("", p.groupName);
        assertEquals("", p.avatar);
        assertEquals("", p.spaceId);
        assertEquals("", p.spaceName);
    }

    // ------------------------------------------------------------------
    // end-to-end：onSuccess → identify → save → goToSpaceGuide →
    //             (user joins space) → consume → restart ScanJoin
    // ------------------------------------------------------------------

    @Test
    public void endToEnd_needSpaceFlow_preservesAllIntentExtras() {
        // 1) onSuccess 阶段：识别 need_space
        String body = "{\"status\":\"need_space\",\"msg\":\"请先加入一个 Space 后再入群\"}";
        assertTrue(PendingGroupInvite.isNeedSpaceResponse(body));

        // 2) Activity 收集当前上下文并落盘
        PendingGroupInvite.Pending original = new PendingGroupInvite.Pending(
                "g_ext", "code_abc", "External Group", "http://cdn/a.png",
                128, false, "s_B", "Space B");
        PendingGroupInvite.save(original, store);

        // 3) SpaceGuideActivity onSpaceJoined 成功 → consume
        PendingGroupInvite.Pending resumed = PendingGroupInvite.consume(store);

        // 4) 重启 ScanJoinGroupActivity 需要完整 extras
        assertNotNull(resumed);
        assertEquals(original.groupNo, resumed.groupNo);
        assertEquals(original.authCode, resumed.authCode);
        assertEquals(original.groupName, resumed.groupName);
        assertEquals(original.avatar, resumed.avatar);
        assertEquals(original.memberCount, resumed.memberCount);
        assertEquals(original.isMember, resumed.isMember);
        assertEquals(original.spaceId, resumed.spaceId);
        assertEquals(original.spaceName, resumed.spaceName);

        // 5) 第二次进 SpaceGuide 不应再命中（pending 已消费）
        assertNull(PendingGroupInvite.consume(store));
    }

    private PendingGroupInvite.Pending newSimplePending() {
        return newSimplePending("g_1");
    }

    private PendingGroupInvite.Pending newSimplePending(String groupNo) {
        return new PendingGroupInvite.Pending(
                groupNo, "auth", "name", "", 0, false, "", "");
    }
}
