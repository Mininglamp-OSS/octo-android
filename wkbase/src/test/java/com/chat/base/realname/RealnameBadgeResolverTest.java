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

package com.chat.base.realname;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *  ( Phase A / iOS ) · 聊天气泡 + 群成员列表
 * 实名徽章可见性逻辑单元测试。
 *
 * <p>锁死的验收矩阵（来自 issue 「UI 规范」+ 「单测」段）：
 * <ul>
 *   <li>已实名 → show</li>
 *   <li>未实名 → gone</li>
 *   <li>字段缺失 → gone （不渲染负向标识）</li>
 * </ul>
 * 同时覆盖后端可能下发的等价 true 表示 (Boolean true / int 1 / "true" /
 * "1")，保证 JSON 解析漂移不会让徽章悄悄消失。
 *
 * <p> P0-2 追加：tri-state API 必须区分「显式 false」与「缺失 key」，
 * 让聊天气泡侧的 member → channel fallback 链只在 member 确实缺失 key 时才
 * 回落，修复 stale channel=true 覆盖 fresh member=false 的 bug。
 */
public class RealnameBadgeResolverTest {

    // ---------- isVerifiedFromMap（纯 Map，便于无 WK 依赖跑） ----------

    @Test
    public void verifiedTrue_rendersBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.TRUE);

        assertTrue(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void verifiedFalse_hidesBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.FALSE);

        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void missingKey_hidesBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        // 刻意不 put —— 模拟老后端 / 字段未下发
        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void nullMap_hidesBadge() {
        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(null));
    }

    @Test
    public void nullValue_hidesBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, null);

        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void numericOne_rendersBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, 1);

        assertTrue(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void numericZero_hidesBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, 0);

        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void stringTrue_caseInsensitive_rendersBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, " TRUE ");

        assertTrue(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void stringOne_rendersBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "1");

        assertTrue(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void stringFalse_hidesBadge() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "false");

        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    @Test
    public void unexpectedType_hidesBadge_defensiveDefault() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, new Object());

        assertFalse(RealnameBadgeResolver.isVerifiedFromMap(extra));
    }

    // ---------- isVerified(WKChannelMember) ----------

    @Test
    public void channelMember_verified() {
        WKChannelMember m = new WKChannelMember();
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, true);
        m.extraMap = extra;

        assertTrue(RealnameBadgeResolver.isVerified(m));
    }

    @Test
    public void channelMember_null_hidesBadge() {
        assertFalse(RealnameBadgeResolver.isVerified((WKChannelMember) null));
    }

    @Test
    public void channelMember_nullExtraMap_hidesBadge() {
        WKChannelMember m = new WKChannelMember();
        m.extraMap = null;

        assertFalse(RealnameBadgeResolver.isVerified(m));
    }

    // ---------- isVerified(WKChannel) —— 单聊 / 作者 fallback ----------

    @Test
    public void channel_verifiedFromRemoteExtra() {
        WKChannel c = new WKChannel();
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, 1);
        c.remoteExtraMap = extra;

        assertTrue(RealnameBadgeResolver.isVerified(c));
    }

    @Test
    public void channel_null_hidesBadge() {
        assertFalse(RealnameBadgeResolver.isVerified((WKChannel) null));
    }

    @Test
    public void channel_missingRealnameKey_hidesBadge() {
        WKChannel c = new WKChannel();
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("some_other_key", "x");
        c.remoteExtraMap = extra;

        assertFalse(RealnameBadgeResolver.isVerified(c));
    }

    // ---------- tri-state API ( P0-2) ----------

    @Test
    public void triState_explicitTrue_returnsBoxedTrue() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.TRUE);

        assertEquals(Boolean.TRUE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_explicitFalse_returnsBoxedFalse_notNull() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.FALSE);

        // 关键：显式 false 必须返回 Boolean.FALSE（不是 null），上层才不会 fallback
        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_missingKey_returnsNull_allowsFallback() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("other_key", "x");

        assertNull(RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_nullMap_returnsNull() {
        assertNull(RealnameBadgeResolver.isVerifiedTriStateFromMap(null));
    }

    @Test
    public void triState_nullValue_returnsNull_allowsFallback() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, null);

        assertNull(RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_numericOne_returnsTrue() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, 1);

        assertEquals(Boolean.TRUE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_numericZero_returnsFalse_notNull() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, 0);

        // numeric 0 是「显式 false」信号，不是 fallback 请求
        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_stringFalse_returnsFalse_notNull() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "false");

        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_stringZero_returnsFalse() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "0");

        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_emptyString_returnsFalse() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "");

        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_unrecognizedString_returnsNull_allowsFallback() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, "maybe");

        // 无法辨识的字符串 → null，让上层 fallback 决定
        assertNull(RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_unrecognizedType_returnsNull_allowsFallback() {
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, new Object());

        assertNull(RealnameBadgeResolver.isVerifiedTriStateFromMap(extra));
    }

    @Test
    public void triState_channelMember_null_returnsNull() {
        assertNull(RealnameBadgeResolver.isVerifiedTriState((WKChannelMember) null));
    }

    @Test
    public void triState_channelMember_nullExtraMap_returnsNull() {
        WKChannelMember m = new WKChannelMember();
        m.extraMap = null;

        assertNull(RealnameBadgeResolver.isVerifiedTriState(m));
    }

    @Test
    public void triState_channelMember_explicitFalse_returnsFalse() {
        WKChannelMember m = new WKChannelMember();
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.FALSE);
        m.extraMap = extra;

        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriState(m));
    }

    @Test
    public void triState_channel_null_returnsNull() {
        assertNull(RealnameBadgeResolver.isVerifiedTriState((WKChannel) null));
    }

    @Test
    public void triState_channel_explicitFalse_returnsFalse() {
        WKChannel c = new WKChannel();
        HashMap<String, Object> extra = new HashMap<>();
        extra.put(WKChannelMemberExtras.realnameVerified, Boolean.FALSE);
        c.remoteExtraMap = extra;

        assertEquals(Boolean.FALSE, RealnameBadgeResolver.isVerifiedTriState(c));
    }

    /**
     * 核心回归：bug 场景的 tri-state 复现。
     *
     * <p>memberOfFrom.extraMap.realname_verified = Boolean.FALSE（已取消实名）；
     * from.remoteExtraMap.realname_verified      = Boolean.TRUE（stale cache）。
     *
     * <p>修复前 boolean-OR：{@code false || true = true} → 错打勾。
     * 修复后 tri-state：member 侧显式 FALSE → 直接 FALSE，不 fallback → 正确。
     */
    @Test
    public void triState_fallbackChain_memberExplicitFalse_winsOverStaleChannelTrue() {
        WKChannelMember member = new WKChannelMember();
        HashMap<String, Object> memberExtra = new HashMap<>();
        memberExtra.put(WKChannelMemberExtras.realnameVerified, Boolean.FALSE);
        member.extraMap = memberExtra;

        WKChannel channel = new WKChannel();
        HashMap<String, Object> channelExtra = new HashMap<>();
        channelExtra.put(WKChannelMemberExtras.realnameVerified, Boolean.TRUE);
        channel.remoteExtraMap = channelExtra;

        // 模拟 WKChatBaseProvider.setFromName 的调用顺序
        Boolean memberTri = RealnameBadgeResolver.isVerifiedTriState(member);
        Boolean resolved = memberTri != null
                ? memberTri
                : RealnameBadgeResolver.isVerifiedTriState(channel);

        assertEquals(Boolean.FALSE, memberTri);
        assertEquals("已取消实名用户不应 fallback 到 stale channel=true", Boolean.FALSE, resolved);
    }

    /**
     * 对称验证：member 端 key 缺失时，fallback 到 channel 必须生效。
     */
    @Test
    public void triState_fallbackChain_memberMissingKey_fallsBackToChannelTrue() {
        WKChannelMember member = new WKChannelMember();
        member.extraMap = new HashMap<>(); // 空 map，没 key

        WKChannel channel = new WKChannel();
        HashMap<String, Object> channelExtra = new HashMap<>();
        channelExtra.put(WKChannelMemberExtras.realnameVerified, Boolean.TRUE);
        channel.remoteExtraMap = channelExtra;

        Boolean memberTri = RealnameBadgeResolver.isVerifiedTriState(member);
        Boolean resolved = memberTri != null
                ? memberTri
                : RealnameBadgeResolver.isVerifiedTriState(channel);

        assertNull(memberTri);
        assertEquals(Boolean.TRUE, resolved);
    }

    /**
     * 两侧都缺失 → 最终 false。
     */
    @Test
    public void triState_fallbackChain_bothMissing_resolvesToFalse() {
        WKChannelMember member = new WKChannelMember();
        member.extraMap = new HashMap<>();
        WKChannel channel = new WKChannel();
        channel.remoteExtraMap = new HashMap<>();

        Boolean memberTri = RealnameBadgeResolver.isVerifiedTriState(member);
        Boolean channelTri = RealnameBadgeResolver.isVerifiedTriState(channel);
        boolean resolved = Boolean.TRUE.equals(memberTri != null ? memberTri : channelTri);

        assertFalse(resolved);
    }
}
