package com.chat.base.external

import com.chat.base.external.ExternalViewerResolver.MemberOrgData
import com.xinbida.wukongim.entity.WKChannelMemberExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 与 dmwork-web `apps/web/__tests__/externalViewer.test.ts` 等价的视角相对化回归。
 * 覆盖 YUJ-87 验收清单：
 *  - 新字段路径（home_space_id 存在）→ 与 viewer 对比
 *  - 降级回落（新字段缺失）→ 退回 is_external + source_space_name
 *  - 同 Space / 跨 Space / 空字符串 / 自己查自己 / 空 extras
 */
class ExternalViewerResolverTest {

    // --- 主路径：home_space_id 存在 ---

    @Test
    fun newField_sameSpace_notExternal() {
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(homeSpaceId = "space_a", homeSpaceName = "Team A"),
            viewerSpaceId = "space_a",
        )
        assertFalse(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    @Test
    fun newField_crossSpace_external_showsHomeName() {
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(homeSpaceId = "space_b", homeSpaceName = "Team B"),
            viewerSpaceId = "space_a",
        )
        assertTrue(r.isExternal)
        assertEquals("Team B", r.sourceSpaceName)
    }

    @Test
    fun newField_crossSpace_missingHomeName_emptySuffix() {
        // 后端给了 home_space_id 但没给 home_space_name —— 避免渲染 "@null"
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(homeSpaceId = "space_b", homeSpaceName = null),
            viewerSpaceId = "space_a",
        )
        assertTrue(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    @Test
    fun newField_viewerSpaceNull_treatAsExternal() {
        // 未登录 Space（viewer == null 或 ""）时，非空 home 视为外部，保持相对视角直觉
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(homeSpaceId = "space_b", homeSpaceName = "Team B"),
            viewerSpaceId = null,
        )
        assertTrue(r.isExternal)
        assertEquals("Team B", r.sourceSpaceName)
    }

    // --- 降级路径：home_space_id 缺失 ---

    @Test
    fun legacy_externalFlagOne_usesSourceName() {
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(
                homeSpaceId = null,
                isExternalLegacy = 1,
                sourceSpaceNameLegacy = "LegacySpace",
            ),
            viewerSpaceId = "space_a",
        )
        assertTrue(r.isExternal)
        assertEquals("LegacySpace", r.sourceSpaceName)
    }

    @Test
    fun legacy_externalFlagZero_notExternal() {
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(
                homeSpaceId = null,
                isExternalLegacy = 0,
                sourceSpaceNameLegacy = "LegacySpace",
            ),
            viewerSpaceId = "space_a",
        )
        assertFalse(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    @Test
    fun legacy_emptyHomeIdFallsBack() {
        // home_space_id = "" 视为字段缺失，走降级逻辑
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(
                homeSpaceId = "",
                homeSpaceName = "Whatever",
                isExternalLegacy = 1,
                sourceSpaceNameLegacy = "LegacyName",
            ),
            viewerSpaceId = "space_a",
        )
        assertTrue(r.isExternal)
        assertEquals("LegacyName", r.sourceSpaceName)
    }

    // --- 边界 ---

    @Test
    fun nullOrg_returnsNotExternal() {
        val r = ExternalViewerResolver.resolve(null, "space_a")
        assertFalse(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    @Test
    fun selfViewingSelf_sameHome_notExternal() {
        // viewer 和 member 都 home=space_a —— 自己看自己绝不应该被打上外部标
        val r = ExternalViewerResolver.resolve(
            MemberOrgData(homeSpaceId = "space_a", homeSpaceName = "Team A"),
            viewerSpaceId = "space_a",
        )
        assertFalse(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    // --- extras Map 便捷入口 ---

    @Test
    fun resolveFromExtras_newField() {
        val extras = mapOf<String, Any?>(
            "home_space_id" to "space_b",
            "home_space_name" to "Team B",
            "is_external" to 1,
            "source_space_name" to "LegacyIgnored",
        )
        val r = ExternalViewerResolver.resolveFromExtras(extras, "space_a")
        assertTrue(r.isExternal)
        // 新字段存在时必须用 home_space_name，不能穿透到 legacy
        assertEquals("Team B", r.sourceSpaceName)
    }

    @Test
    fun resolveFromExtras_legacyOnly() {
        val extras = mapOf<String, Any?>(
            "is_external" to 1,
            "source_space_name" to "LegacySpace",
        )
        val r = ExternalViewerResolver.resolveFromExtras(extras, "space_a")
        assertTrue(r.isExternal)
        assertEquals("LegacySpace", r.sourceSpaceName)
    }

    @Test
    fun resolveFromExtras_booleanFlag() {
        // 有些反序列化路径把 0/1 变成 Boolean —— 确保不因此漏判
        val extras = mapOf<String, Any?>(
            "is_external" to true,
            "source_space_name" to "BoolSpace",
        )
        val r = ExternalViewerResolver.resolveFromExtras(extras, "space_a")
        assertTrue(r.isExternal)
        assertEquals("BoolSpace", r.sourceSpaceName)
    }

    @Test
    fun resolveFromExtras_nullMap_safe() {
        val r = ExternalViewerResolver.resolveFromExtras(null, "space_a")
        assertFalse(r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }

    /**
     * 防止 Web YUJ-53 事故重演：model 层 key 改名或 resolver 常量漂移会让 UI 静默失效。
     * 显式锁死 `resolveFromExtras` 使用的字符串 key 必须与 [WKChannelMemberExtras] 对齐。
     */
    @Test
    fun extrasKeyContract_matchesWKChannelMemberExtras() {
        assertEquals("home_space_id", WKChannelMemberExtras.homeSpaceID)
        assertEquals("home_space_name", WKChannelMemberExtras.homeSpaceName)
        assertEquals("is_external", WKChannelMemberExtras.isExternal)
        assertEquals("source_space_name", WKChannelMemberExtras.sourceSpaceName)

        // 把 extras 按 WKChannelMemberExtras 常量灌进去，resolver 仍应走新字段主路径
        val extras = mapOf<String, Any?>(
            WKChannelMemberExtras.homeSpaceID to "space_b",
            WKChannelMemberExtras.homeSpaceName to "Team B",
            WKChannelMemberExtras.isExternal to 1,
            WKChannelMemberExtras.sourceSpaceName to "LegacyIgnored",
        )
        val r = ExternalViewerResolver.resolveFromExtras(extras, "space_a")
        assertTrue(r.isExternal)
        assertEquals("Team B", r.sourceSpaceName)
    }

    /**
     * Codex review P2 回归（UserDetailActivity.applyExternalSourceRow）：
     *
     * 场景：WKIM 缓存里有 home-space 上线前写入的陈旧 extras
     *   {is_external=1, source_space_name="OldSpace"}
     * 后端刚返回的 userInfo.group_member 带新字段
     *   {home_space_id=viewerSpace, home_space_name="Team A", is_external=0}
     *
     * 以前的实现若发现 cache 非空就直接用 cache，resolver 走降级路径 → 误标外部。
     * 修复后：调用方必须把 fresh group_member 覆盖到 cache 上面（或新建 extras），
     * resolver 看到 home_space_id == viewer 自然判非外部。
     *
     * 这条 test 在 resolver 层复现融合后的 extras，保证 resolver 行为正确；UI 层的
     * 融合策略已经落在 UserDetailActivity.applyExternalSourceRow 里。
     */
    @Test
    fun staleCache_freshHomeSpace_freshWins() {
        val cache = mutableMapOf<String, Any?>(
            "is_external" to 1,
            "source_space_name" to "OldSpace",
        )
        // 模拟 UserDetailActivity 的融合：freshHasHomeSpace=true → 覆盖 cache
        val freshHomeId = "space_a"
        val freshHomeName = "Team A"
        val merged = cache.toMutableMap().apply {
            put(WKChannelMemberExtras.homeSpaceID, freshHomeId)
            put(WKChannelMemberExtras.homeSpaceName, freshHomeName)
            put(WKChannelMemberExtras.isExternal, 0)
        }
        val r = ExternalViewerResolver.resolveFromExtras(merged, "space_a")
        assertFalse("fresh home_space_id == viewer, should not be external", r.isExternal)
        assertEquals("", r.sourceSpaceName)
    }
}

