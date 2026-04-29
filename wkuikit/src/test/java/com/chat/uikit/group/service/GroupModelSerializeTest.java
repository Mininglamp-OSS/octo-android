package com.chat.uikit.group.service;

import com.chat.uikit.group.service.entity.GroupMember;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 外部群 Phase 1 — YUJ-86 EP1 解析层字段透传单元测试（强制）。
 *
 * 验证 {@link GroupModel#serialize(List)} 将 /group/members 响应的外部字段
 * 完整写进 {@link WKChannelMember#extraMap}：
 *   is_external / source_space_id / source_space_name /
 *   home_space_id / home_space_name
 *
 * web YUJ-53 的静默失败教训：model 层漏了字段 → UI 节点数归零却没有编译报错。
 * 该测试就是 EP1 的止血点，只要 serialize 少写任何一个 key 就会红灯。
 */
public class GroupModelSerializeTest {

    private static GroupMember newExternalMember() {
        GroupMember m = new GroupMember();
        m.uid = "uid_bob";
        m.name = "Bob";
        m.group_no = "grp_123";
        m.vercode = "vc_xyz";
        m.is_external = 1;
        m.source_space_id = "space_beta";
        m.source_space_name = "Beta";
        m.home_space_id = "space_alpha";
        m.home_space_name = "Alpha";
        return m;
    }

    @Test
    public void serialize_propagatesAllExternalFieldsIntoExtraMap() {
        List<WKChannelMember> out = GroupModel.serialize(Arrays.asList(newExternalMember()));
        assertEquals(1, out.size());
        WKChannelMember member = out.get(0);
        assertNotNull(member.extraMap);

        assertEquals(1, member.extraMap.get(WKChannelMemberExtras.isExternal));
        assertEquals("space_beta", member.extraMap.get(WKChannelMemberExtras.sourceSpaceID));
        assertEquals("Beta", member.extraMap.get(WKChannelMemberExtras.sourceSpaceName));
        assertEquals("space_alpha", member.extraMap.get(WKChannelMemberExtras.homeSpaceID));
        assertEquals("Alpha", member.extraMap.get(WKChannelMemberExtras.homeSpaceName));

        // 核心字段继续保持
        assertEquals("uid_bob", member.memberUID);
        assertEquals("Bob", member.memberName);
        assertEquals("grp_123", member.channelID);
        assertEquals("vc_xyz", member.extraMap.get(WKChannelMemberExtras.WKCode));
    }

    @Test
    public void serialize_omitsEmptySpaceFields_butAlwaysWritesIsExternal() {
        GroupMember m = new GroupMember();
        m.uid = "uid_alice";
        m.name = "Alice";
        m.group_no = "grp_123";
        m.is_external = 0;
        // source/home 字段全空
        m.source_space_id = null;
        m.source_space_name = "";
        m.home_space_id = null;
        m.home_space_name = null;

        List<WKChannelMember> out = GroupModel.serialize(Arrays.asList(m));
        WKChannelMember member = out.get(0);

        assertNotNull(member.extraMap);
        // is_external 即便是 0 也要写，下游可直接判等
        assertEquals(0, member.extraMap.get(WKChannelMemberExtras.isExternal));
        // 其它空字段不落库，避免 UI 显示空串
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceName));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceName));
    }

    @Test
    public void serialize_returnsEmptyList_forEmptyInput() {
        assertTrue(GroupModel.serialize(new ArrayList<GroupMember>()).isEmpty());
        assertTrue(GroupModel.serialize(null).isEmpty());
    }

    /** 回归保护：GroupMember DTO 必须声明 5 个外部字段（YUJ-86 EP1 验收项）。 */
    @Test
    public void groupMemberDto_declaresAllExternalFields() throws NoSuchFieldException {
        Class<GroupMember> cls = GroupMember.class;
        assertNotNull(cls.getField("is_external"));
        assertNotNull(cls.getField("source_space_id"));
        assertNotNull(cls.getField("source_space_name"));
        assertNotNull(cls.getField("home_space_id"));
        assertNotNull(cls.getField("home_space_name"));
    }
}
