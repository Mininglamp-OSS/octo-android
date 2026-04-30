package com.chat.uikit.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.chat.uikit.enity.UserInfo;

import org.junit.Test;

/**
 * YUJ-155 · Android 搜索结果外部成员 @SpaceName 后缀单测。
 *
 * <p>覆盖 {@link SearchUserAdapter#resolveExternalSpaceName(UserInfo, String)}
 * 四个关键场景（对齐 YUJ-134 RemindMemberAdapterExternalSpaceTest 与 Web
 * YUJ-138 PR#1088 resolveExternalForViewer 语义）：
 * <ol>
 *   <li>跨 Space：home_space_id != viewerSpaceId → 返回 home_space_name，
 *       调用方会在搜索结果昵称后拼 " @SpaceName" 灰紫色后缀。</li>
 *   <li>同 Space：home_space_id == viewerSpaceId → 返回 null，不渲染后缀，
 *       避免同 Space 成员被错标为外部。</li>
 *   <li>空 sourceSpaceName：is_external=1 但 source_space_name 空 → 返回 null，
 *       避免渲染 "@" 空占位污染昵称。</li>
 *   <li>Legacy 降级：无 home_space_id，is_external=1 + source_space_name 有值
 *       → 回落到 legacy 字段，仍渲染后缀（旧后端兼容）。</li>
 * </ol>
 */
public class SearchUserAdapterExternalSpaceTest {

    private UserInfo user(String homeSpaceId, String homeSpaceName,
                          int isExternal, String sourceSpaceName) {
        UserInfo u = new UserInfo();
        u.uid = "u1";
        u.name = "Alice";
        u.home_space_id = homeSpaceId;
        u.home_space_name = homeSpaceName;
        u.is_external = isExternal;
        u.source_space_name = sourceSpaceName;
        return u;
    }

    /** 场景 1：跨 Space 搜索结果必须显示外部 Space 名。 */
    @Test
    public void crossSpace_newField_returnsHomeSpaceName() {
        String suffix = SearchUserAdapter.resolveExternalSpaceName(
                user("space_b", "Team B", 0, null),
                "space_a"
        );
        assertEquals("Team B", suffix);
    }

    /** 场景 2：同 Space 不渲染后缀。 */
    @Test
    public void sameSpace_returnsNull() {
        String suffix = SearchUserAdapter.resolveExternalSpaceName(
                user("space_a", "Team A", 0, null),
                "space_a"
        );
        assertNull(suffix);
    }

    /** 场景 3：is_external=1 但 source_space_name 为空 → 不渲染。 */
    @Test
    public void externalButEmptySpaceName_returnsNull() {
        String suffix = SearchUserAdapter.resolveExternalSpaceName(
                user(null, null, 1, ""),
                "space_a"
        );
        assertNull(suffix);
    }

    /** 场景 4：Legacy 降级路径（旧后端无 home_space_id）。 */
    @Test
    public void legacyFallback_returnsSourceSpaceName() {
        String suffix = SearchUserAdapter.resolveExternalSpaceName(
                user(null, null, 1, "LegacySpace"),
                "space_a"
        );
        assertEquals("LegacySpace", suffix);
    }

    /** 边界：UserInfo 为空时稳定返回 null，防 NPE。 */
    @Test
    public void nullUser_returnsNull() {
        assertNull(SearchUserAdapter.resolveExternalSpaceName(null, "space_a"));
    }

    /** 边界：viewerSpaceId 为空时仍能走 new field 路径（home_space_id 非空就算外部）。 */
    @Test
    public void emptyViewerSpaceId_withHomeSpaceId_rendersSuffix() {
        String suffix = SearchUserAdapter.resolveExternalSpaceName(
                user("space_b", "Team B", 0, null),
                ""
        );
        assertEquals("Team B", suffix);
    }
}
