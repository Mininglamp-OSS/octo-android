package com.chat.base.realname;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import java.util.Map;

/**
 * YUJ-380 (对齐 web YUJ-379 · dmwork-web#1169 Phase A / iOS YUJ-381) ·
 * 实名徽章可见性解析。
 *
 * <p>方案 J v3 遗留：后端在 users/{uid}、group_members、订阅列表里透传
 * {@code realname_verified}；Android 侧已在
 * {@link com.xinbida.wukongim.entity.WKChannelMemberExtras#realnameVerified}
 * 规范化了 key，并在 {@code UserModel.buildMemberFromUserInfo} / {@code GroupModel.serialize}
 * 把值写进 {@code WKChannelMember.extraMap}。
 *
 * <p>本 resolver 是一个**读侧的纯函数**：收敛「把 Object → Boolean」的容错
 * （兼容 Boolean / Number / "true|1|false|0|空串" / null），避免聊天气泡 +
 * 群成员列表两处各自 cast 扩散。
 *
 * <h3>tri-state 语义（YUJ-395 修复）</h3>
 * <p>聊天气泡侧存在 member 侧 → channel 侧的 fallback 链。如果 resolver 只返回
 * {@code boolean}，调用方无法区分「member 端显式 false（已取消实名）」与
 * 「member 端缺失 key（未知，应 fallback 到 channel）」—— stale 的
 * {@code channel.remoteExtraMap.realname_verified=true} 会把新数据
 * {@code false} 覆盖掉，给已取消实名的用户错打勾。
 *
 * <p>为此新增 {@link #isVerifiedTriState(WKChannelMember)} /
 * {@link #isVerifiedTriState(WKChannel)} / {@link #isVerifiedTriStateFromMap(Map)}：
 * <ul>
 *   <li>{@code Boolean.TRUE}  — 显式 true，直接 verified = true，不 fallback</li>
 *   <li>{@code Boolean.FALSE} — 显式 false，直接 verified = false，不 fallback</li>
 *   <li>{@code null}          — 缺失 key / null 值 / 未识别类型，上层可 fallback</li>
 * </ul>
 *
 * <p>原有 boolean-API {@link #isVerified(WKChannelMember)} /
 * {@link #isVerified(WKChannel)} / {@link #isVerifiedFromMap(Map)} 保留，
 * 语义为「tri-state == TRUE」（null / FALSE 都是 false），方便没有 fallback 需求
 * 的调用方（如群成员列表 {@code AllMembersAdapter}）一行搞定。
 *
 * <p>UI 规范：<b>true 才渲染 ✓</b>；false / 缺失均不渲染（不渲染负向标识）。
 */
public final class RealnameBadgeResolver {

    private RealnameBadgeResolver() {
    }

    // ---------------- boolean API (兼容无 fallback 需求的调用方) ----------------

    /**
     * 群成员列表 / 群聊气泡作者名使用的入口。
     * {@code member} 为 null 或 extraMap 缺失 key 时返回 false。
     */
    public static boolean isVerified(WKChannelMember member) {
        return Boolean.TRUE.equals(isVerifiedTriState(member));
    }

    /**
     * 聊天气泡 fallback 入口：WKChannelMember 缺失时 (例如单聊 from user) 从
     * {@link WKChannel#remoteExtraMap} 读取——后端 /users/{uid} 在 extra 里
     * 也可能透传 realname_verified。
     */
    public static boolean isVerified(WKChannel channel) {
        return Boolean.TRUE.equals(isVerifiedTriState(channel));
    }

    /**
     * 直接从原始 extra map 读取（供测试 / 上层复用，不依赖 WK 实体）。
     */
    public static boolean isVerifiedFromMap(Map<?, ?> extra) {
        return Boolean.TRUE.equals(isVerifiedTriStateFromMap(extra));
    }

    // ---------------- tri-state API (YUJ-395, 有 fallback 需求时使用) ----------------

    /**
     * tri-state 版：member 端是否有显式 realname_verified flag。
     * <ul>
     *   <li>返回 {@code Boolean.TRUE}  — 显式 true</li>
     *   <li>返回 {@code Boolean.FALSE} — 显式 false（已取消实名）</li>
     *   <li>返回 {@code null}          — member 或 extraMap 为 null，或 key 缺失，
     *                                     或值是未识别的类型；上层可 fallback 到
     *                                     {@link WKChannel#remoteExtraMap}。</li>
     * </ul>
     */
    public static Boolean isVerifiedTriState(WKChannelMember member) {
        if (member == null) return null;
        return isVerifiedTriStateFromMap(member.extraMap);
    }

    /**
     * tri-state 版 channel 入口（{@link WKChannel#remoteExtraMap}）。
     */
    public static Boolean isVerifiedTriState(WKChannel channel) {
        if (channel == null) return null;
        return isVerifiedTriStateFromMap(channel.remoteExtraMap);
    }

    /**
     * tri-state 版原始入口。把后端可能回传的值归一化：
     * <ul>
     *     <li>{@code Boolean}   — 原样返回</li>
     *     <li>{@code Number}    — 0 → FALSE, 非 0 → TRUE</li>
     *     <li>{@code String}    — "true"/"1" (忽略大小写+两端空格) → TRUE,
     *                             "false"/"0"/"" → FALSE, 其他未识别字符串 → null</li>
     *     <li>{@code null} 值 / key 缺失 / 未识别类型 — null (让上层 fallback)</li>
     * </ul>
     */
    public static Boolean isVerifiedTriStateFromMap(Map<?, ?> extra) {
        if (extra == null || extra.isEmpty()) return null;
        if (!extra.containsKey(WKChannelMemberExtras.realnameVerified)) return null;
        Object raw = extra.get(WKChannelMemberExtras.realnameVerified);
        return asTriState(raw);
    }

    /**
     * Object → Boolean?（null 表示 unknown / fallback-eligible）。
     */
    static Boolean asTriState(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() != 0;
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(s) || "0".equals(s) || s.isEmpty()) return Boolean.FALSE;
            return null;
        }
        return null;
    }
}
