package com.xinbida.wukongim.entity;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 外部群 Phase 1 —  EP1 数据层透传单元测试。
 *
 * 验证 {@link WKMsg} 上新增的 msg-level 便捷 getter（对齐 web #982 / #997
 * 的 MessageWrap 语义）在 memberOfFrom.extraMap 含/不含外部字段时的返回值。
 *
 * web  曾因 model 层未透传字段导致 UI 静默失败，这里强制保证 getter
 * 能稳定读出 is_external / source_space_name / home_space_id / home_space_name。
 */
public class WKMsgExternalGroupFieldsTest {

    private static WKMsg msgWithMember(WKChannelMember member) {
        WKMsg msg = new WKMsg();
        msg.setMemberOfFrom(member);
        return msg;
    }

    @Test
    public void fromIsExternal_returnsValue_whenMemberHasExtra() {
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        member.extraMap = extras;

        assertEquals(1, msgWithMember(member).getFromIsExternal());
    }

    @Test
    public void fromIsExternal_returnsZero_whenMemberExtraMapMissing() {
        // 注：我们不测试 msg.memberOfFrom == null 的路径，因为 getMemberOfFrom()
        // 会惰性地调 ChannelMembersManager.getInstance().getMember(...) → 触达
        // ChannelMembersDbManager（SQLite），在 host JVM 单测里无法跑通。
        // 该 lazy-load 分支由 instrumented test 覆盖（超出 EP1 JVM 单测范围）。
        //
        // 这里只验证"显式设置了 member 但没填 extraMap / 填了空 extraMap"的分支：
        // getter 必须安全地返回 0 / null，不能 NPE。
        WKChannelMember member = new WKChannelMember();
        assertEquals(0, msgWithMember(member).getFromIsExternal());

        member.extraMap = new HashMap<String, Object>();
        assertEquals(0, msgWithMember(member).getFromIsExternal());
    }

    @Test
    public void fromIsExternal_acceptsStringAndLongValues() {
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, "1");
        member.extraMap = extras;
        assertEquals(1, msgWithMember(member).getFromIsExternal());

        extras.put(WKChannelMemberExtras.isExternal, Long.valueOf(1L));
        assertEquals(1, msgWithMember(member).getFromIsExternal());

        extras.put(WKChannelMemberExtras.isExternal, "not-a-number");
        assertEquals(0, msgWithMember(member).getFromIsExternal());
    }

    @Test
    public void fromSourceSpaceName_returnsValue() {
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.sourceSpaceName, "Octo Labs");
        member.extraMap = extras;

        assertEquals("Octo Labs", msgWithMember(member).getFromSourceSpaceName());
    }

    @Test
    public void fromSourceSpaceID_returnsValue() {
        // claude review round-2 P2：source_space_id 也暴露 getter，避免 UI 绕过
        // 封装直接取裸 key，对齐  防静默失败原则。
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.sourceSpaceID, "space_beta");
        member.extraMap = extras;

        assertEquals("space_beta", msgWithMember(member).getFromSourceSpaceID());
    }

    @Test
    public void fromSourceSpaceName_returnsNull_whenMissing() {
        WKChannelMember member = new WKChannelMember();
        member.extraMap = new HashMap<String, Object>();
        assertNull(msgWithMember(member).getFromSourceSpaceName());
    }

    @Test
    public void fromHomeSpaceID_andName_returnValues() {
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.homeSpaceID, "space_alpha");
        extras.put(WKChannelMemberExtras.homeSpaceName, "Alpha");
        member.extraMap = extras;

        assertEquals("space_alpha", msgWithMember(member).getFromHomeSpaceID());
        assertEquals("Alpha", msgWithMember(member).getFromHomeSpaceName());
    }

    @Test
    public void fromHomeSpace_returnsNull_whenMissing() {
        WKChannelMember member = new WKChannelMember();
        member.extraMap = new HashMap<String, Object>();
        assertNull(msgWithMember(member).getFromHomeSpaceID());
        assertNull(msgWithMember(member).getFromHomeSpaceName());
    }

    /**  回归保护：确保 4 个新 extras key 都是预期的后端字段名。 */
    @Test
    public void memberExtrasKeys_matchBackendContract() {
        assertEquals("is_external", WKChannelMemberExtras.isExternal);
        assertEquals("source_space_id", WKChannelMemberExtras.sourceSpaceID);
        assertEquals("source_space_name", WKChannelMemberExtras.sourceSpaceName);
        assertEquals("home_space_id", WKChannelMemberExtras.homeSpaceID);
        assertEquals("home_space_name", WKChannelMemberExtras.homeSpaceName);
    }
}
