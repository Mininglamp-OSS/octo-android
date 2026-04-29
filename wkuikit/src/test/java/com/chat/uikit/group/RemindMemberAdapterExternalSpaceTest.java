package com.chat.uikit.group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.junit.Test;

import java.util.HashMap;

/**
 * YUJ-134 · Android @Mention 候选菜单外部成员 @SpaceName 标识单测。
 *
 * <p>覆盖 {@link RemindMemberAdapter#resolveExternalSpaceName(WKChannelMember, String)}
 * 四个关键场景（对齐 Web `createMentionSuggestion` + resolveExternalForViewer 语义）：
 * <ol>
 *   <li>跨 Space：home_space_id != viewerSpaceId → 返回 home_space_name，
 *       调用方会在 @候选菜单昵称后拼 " @SpaceName" 灰紫色后缀，提醒跨 Space 避免数据泄漏。</li>
 *   <li>同 Space：home_space_id == viewerSpaceId → 返回 null，不渲染后缀。</li>
 *   <li>空 sourceSpaceName：is_external=1 但 source_space_name 空 → 返回 null，
 *       避免渲染 "@" 空占位污染昵称。</li>
 *   <li>Legacy 降级：无 home_space_id，is_external=1 + source_space_name 有值
 *       → 回落到 legacy 字段，仍需显示后缀（旧后端兼容）。</li>
 * </ol>
 *
 * <p>同时守护 Web YUJ-53 式 key 漂移：所有测试用 {@link WKChannelMemberExtras}
 * 的常量往 extraMap 里放值，避免 model / resolver 之间字符串不一致导致 UI 静默失效。
 */
public class RemindMemberAdapterExternalSpaceTest {

    private WKChannelMember memberWithExtras(HashMap<String, Object> extras) {
        WKChannelMember m = new WKChannelMember();
        m.memberUID = "u1";
        m.memberName = "Alice";
        m.extraMap = extras;
        return m;
    }

    /** 场景 1：跨 Space @候选菜单必须显示外部 Space 名。 */
    @Test
    public void crossSpace_newField_returnsHomeSpaceName() {
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.homeSpaceID, "space_b");
        extras.put(WKChannelMemberExtras.homeSpaceName, "Team B");

        String suffix = RemindMemberAdapter.resolveExternalSpaceName(
                memberWithExtras(extras),
                "space_a"
        );

        assertEquals("Team B", suffix);
    }

    /** 场景 2：同 Space 不渲染后缀，避免同群同 Space 成员被错误高亮。 */
    @Test
    public void sameSpace_returnsNull() {
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.homeSpaceID, "space_a");
        extras.put(WKChannelMemberExtras.homeSpaceName, "Team A");

        String suffix = RemindMemberAdapter.resolveExternalSpaceName(
                memberWithExtras(extras),
                "space_a"
        );

        assertNull(suffix);
    }

    /** 场景 3：is_external=1 但 source_space_name 为空 → 不渲染 "@"。 */
    @Test
    public void externalButEmptySpaceName_returnsNull() {
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        extras.put(WKChannelMemberExtras.sourceSpaceName, "");

        String suffix = RemindMemberAdapter.resolveExternalSpaceName(
                memberWithExtras(extras),
                "space_a"
        );

        assertNull(suffix);
    }

    /** 场景 4：Legacy 降级路径（旧后端无 home_space_id）。 */
    @Test
    public void legacyFallback_returnsSourceSpaceName() {
        HashMap<String, Object> extras = new HashMap<>();
        extras.put(WKChannelMemberExtras.isExternal, 1);
        extras.put(WKChannelMemberExtras.sourceSpaceName, "LegacySpace");

        String suffix = RemindMemberAdapter.resolveExternalSpaceName(
                memberWithExtras(extras),
                "space_a"
        );

        assertEquals("LegacySpace", suffix);
    }

    /** 边界：member / extraMap 为空时必须稳定返回 null，防 NPE。 */
    @Test
    public void nullOrEmptyExtras_returnsNull() {
        assertNull(RemindMemberAdapter.resolveExternalSpaceName(null, "space_a"));

        WKChannelMember memberNullMap = new WKChannelMember();
        memberNullMap.memberUID = "u2";
        assertNull(RemindMemberAdapter.resolveExternalSpaceName(memberNullMap, "space_a"));

        WKChannelMember memberEmptyMap = memberWithExtras(new HashMap<>());
        assertNull(RemindMemberAdapter.resolveExternalSpaceName(memberEmptyMap, "space_a"));
    }
}
