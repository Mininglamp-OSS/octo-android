/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 *  Path B · Round-2 (review) · host-side 单元测试。
 *
 * <p>锁定：后端显式返回 {@code space_id=""}（「这是公共群」语义）+ pre-scan QR
 * 带旧 {@code targetSpaceId} 非空的分支 → 必须被视为公共群、不触发跨 Space Toast。
 */
public class ScanJoinEffectiveResolverTest {

    // ------------------------------------------------------------------
    // resolve: null → fallback,  "" → keep as-is (public-group signal)
    // ------------------------------------------------------------------

    @Test
    public void resolve_nullResponse_usesFallback() {
        // 后端未返回该字段 → fallback 到 pre-scan QR payload
        assertEquals("sA", ScanJoinEffectiveResolver.resolve(null, "sA"));
    }

    @Test
    public void resolve_emptyResponse_keepsEmpty() {
        // 后端显式返回空串 = 公共群；必须保留 "" 而非 fallback
        assertEquals("", ScanJoinEffectiveResolver.resolve("", "sA"));
    }

    @Test
    public void resolve_nonEmptyResponse_takesResponse() {
        assertEquals("sB", ScanJoinEffectiveResolver.resolve("sB", "sA"));
    }

    @Test
    public void resolve_nullResponseNullFallback_returnsNull() {
        assertEquals(null, ScanJoinEffectiveResolver.resolve(null, null));
    }

    @Test
    public void resolve_returnsResponseReferenceWhenNonNull() {
        // 保证不分配新字符串，纯 ternary
        String resp = "x";
        assertSame(resp, ScanJoinEffectiveResolver.resolve(resp, "y"));
    }

    // ------------------------------------------------------------------
    // isCrossSpace: 硬约束分支
    // ------------------------------------------------------------------

    @Test
    public void isCrossSpace_differentSpaces_true() {
        assertTrue(ScanJoinEffectiveResolver.isCrossSpace("space_B", "space_A"));
    }

    @Test
    public void isCrossSpace_sameSpace_false() {
        assertFalse(ScanJoinEffectiveResolver.isCrossSpace("space_A", "space_A"));
    }

    @Test
    public void isCrossSpace_targetNull_false() {
        assertFalse(ScanJoinEffectiveResolver.isCrossSpace(null, "space_A"));
    }

    @Test
    public void isCrossSpace_targetEmpty_false() {
        // 公共群（后端空串）→ 永远不算跨 Space
        assertFalse(ScanJoinEffectiveResolver.isCrossSpace("", "space_A"));
    }

    @Test
    public void isCrossSpace_viewerNull_false() {
        // 非 Space 模式（viewer 未选择任何 Space）
        assertFalse(ScanJoinEffectiveResolver.isCrossSpace("space_B", null));
    }

    @Test
    public void isCrossSpace_viewerEmpty_false() {
        assertFalse(ScanJoinEffectiveResolver.isCrossSpace("space_B", ""));
    }

    // ------------------------------------------------------------------
    // 核心回归：后端 space_id="" + pre-scan targetSpaceId 非空 → 不弹 Toast
    // ------------------------------------------------------------------

    /**
     * 回归分支（review 指出的 blocker 场景）：
     * 群原本位于 space_B，QR 是在那个时点生成的（pre-scan 携带 targetSpaceId="space_B"）。
     * 随后群被移至公共 → 后端 scanjoin 响应 space_id="" 表示「这是公共群」。
     * viewer 当前站在 space_A。round-2 前：fallback 到 pre-scan 的 "space_B"
     * → 误判为跨 Space；round-2 后：保留 ""，不跨 Space。
     */
    @Test
    public void regression_emptyRespSpaceId_overridesStalePreScanFallback() {
        String respSpaceId = "";          // 后端显式：公共群
        String preScanTargetSpaceId = "space_B"; // QR 留存的旧值
        String viewerSpaceId = "space_A";

        String effective = ScanJoinEffectiveResolver.resolve(respSpaceId, preScanTargetSpaceId);
        assertEquals("", effective);
        assertFalse("public group must not trigger cross-Space notice",
                ScanJoinEffectiveResolver.isCrossSpace(effective, viewerSpaceId));
    }

    /**
     * 补位分支：后端未返回 space_id（字段缺失 → null）时才 fallback 到 pre-scan 值。
     * 这是 round-2 之前就正确的场景，锁定行为不退化。
     */
    @Test
    public void regression_nullRespSpaceId_fallsBackToPreScan() {
        String respSpaceId = null;
        String preScanTargetSpaceId = "space_B";
        String viewerSpaceId = "space_A";

        String effective = ScanJoinEffectiveResolver.resolve(respSpaceId, preScanTargetSpaceId);
        assertEquals("space_B", effective);
        assertTrue("missing field falls back to pre-scan → still cross-Space",
                ScanJoinEffectiveResolver.isCrossSpace(effective, viewerSpaceId));
    }
}
