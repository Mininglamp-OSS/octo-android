package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * YUJ-326 · {@link SpaceChannelIdParser} 契约测试。
 *
 * <p>对齐 migration SQL 的 substr 提取规则 + 增强的 hex 校验。Yu review 2026-05-04 04:41Z
 * 提出的"异常 channel_id 短串 / 非 hex → space_id=''（不抛异常）"测试矩阵这里完整覆盖。
 */
public class SpaceChannelIdParserTest {

    private static final String VALID_HEX = "0123456789abcdef0123456789abcdef"; // 32 chars
    private static final String VALID_HEX_UPPER = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    public void canonicalSpaceChannelIdExtractsHexSpaceId() {
        String channelId = "s" + VALID_HEX + "_peerXYZ";
        assertEquals(VALID_HEX, SpaceChannelIdParser.extractSpaceId(channelId));
    }

    @Test
    public void acceptsUpperCaseHex() {
        String channelId = "s" + VALID_HEX_UPPER + "_peer";
        assertEquals(VALID_HEX_UPPER, SpaceChannelIdParser.extractSpaceId(channelId));
    }

    @Test
    public void longPeerIdOk() {
        String channelId = "s" + VALID_HEX + "_"
                + "very_long_peer_id_with_underscores____end";
        assertEquals(VALID_HEX, SpaceChannelIdParser.extractSpaceId(channelId));
    }

    @Test
    public void nullInputReturnsEmpty() {
        assertEquals("", SpaceChannelIdParser.extractSpaceId(null));
    }

    @Test
    public void shortStringReturnsEmpty() {
        // 长度 < 34 一律空串
        assertEquals("", SpaceChannelIdParser.extractSpaceId(""));
        assertEquals("", SpaceChannelIdParser.extractSpaceId("s"));
        assertEquals("", SpaceChannelIdParser.extractSpaceId("s0123456789abcdef")); // 17
        assertEquals("", SpaceChannelIdParser.extractSpaceId("s" + VALID_HEX)); // 33
    }

    @Test
    public void missingSPrefixReturnsEmpty() {
        // 普通 peer_id 开头（非 's'）。这是历史个人 / 群聊 channel_id 的主流格式。
        assertEquals("", SpaceChannelIdParser.extractSpaceId("peer12345"));
        assertEquals("", SpaceChannelIdParser.extractSpaceId("u_10000"));
        assertEquals("", SpaceChannelIdParser.extractSpaceId("g_community_abc"));
        String withoutS = "x" + VALID_HEX + "_peer";
        assertEquals("", SpaceChannelIdParser.extractSpaceId(withoutS));
    }

    @Test
    public void missingUnderscoreAtPosition33ReturnsEmpty() {
        // 第 34 个字符（1-indexed）不是 '_'
        String bad = "s" + VALID_HEX + "xpeer";
        assertEquals("", SpaceChannelIdParser.extractSpaceId(bad));
    }

    @Test
    public void nonHexMiddleReturnsEmpty() {
        // server 语义：space_id 必须 32-hex。异常输入（人为构造 / 老脏数据）拒绝。
        String bad = "s" + "zzz" + VALID_HEX.substring(3) + "_peer";
        assertEquals("", SpaceChannelIdParser.extractSpaceId(bad));
    }

    @Test
    public void isAllHexBoundaryCases() {
        assertTrue(SpaceChannelIdParser.isAllHex(""));
        assertTrue(SpaceChannelIdParser.isAllHex("0"));
        assertTrue(SpaceChannelIdParser.isAllHex("abcdef"));
        assertTrue(SpaceChannelIdParser.isAllHex("ABCDEF"));
        assertTrue(SpaceChannelIdParser.isAllHex("0123456789"));
        assertFalse(SpaceChannelIdParser.isAllHex("g"));
        assertFalse(SpaceChannelIdParser.isAllHex("xyz"));
        assertFalse(SpaceChannelIdParser.isAllHex("abc-123"));
    }

    @Test
    public void prefixLengthConstants() {
        assertEquals(34, SpaceChannelIdParser.PREFIX_LENGTH);
        assertEquals(32, SpaceChannelIdParser.SPACE_ID_LENGTH);
    }
}
