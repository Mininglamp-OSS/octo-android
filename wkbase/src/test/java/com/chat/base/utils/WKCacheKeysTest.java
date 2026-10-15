package com.chat.base.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 缓存身份派生的回归测试（B-1）。全部断言直接调用生产 {@link WKCacheKeys}，
 * 不在测试里复制算法——生产实现改动会立刻反映到这里。
 */
public class WKCacheKeysTest {

    /** B-1 的原始反例：旧 sanitizeScope 把 ':' 与 '?' 都替换成 '_'，两者塌成同一键。 */
    @Test
    public void punctuationVariantsProduceDistinctIdentityAndBasename() {
        String a = WKCacheKeys.entryIdentity("b:parent-uuid", "m:a", 0);
        String b = WKCacheKeys.entryIdentity("b:parent-uuid", "m?a", 1);
        assertNotEquals(a, b);
        assertNotEquals(
                WKCacheKeys.basenameForIdentity(a),
                WKCacheKeys.basenameForIdentity(b));
    }

    /** 只在第 64 字符之后不同的两个 ID：旧实现截断到 64 字符后塌成同一键。 */
    @Test
    public void idsDifferingAfterCharacter64RemainDistinct() {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < 64; i++) prefix.append('x');
        String a = WKCacheKeys.entryIdentity("b:p", prefix + "AAAA", 0);
        String b = WKCacheKeys.entryIdentity("b:p", prefix + "BBBB", 0);
        assertNotEquals(a, b);
        assertNotEquals(
                WKCacheKeys.basenameForIdentity(a),
                WKCacheKeys.basenameForIdentity(b));
    }

    /** messageID 存在 vs 缺失走不同命名空间：一个叫 "3" 的 ID 不能与下标 3 撞车。 */
    @Test
    public void presentIdAndIndexFallbackUseSeparateNamespaces() {
        String withId = WKCacheKeys.entryIdentity("b:p", "3", 7);
        String withIndex = WKCacheKeys.entryIdentity("b:p", null, 3);
        assertNotEquals(withId, withIndex);
        String emptyId = WKCacheKeys.entryIdentity("b:p", "", 3);
        assertEquals(withIndex, emptyId);
    }

    /** bundle 作用域参与身份：同一 messageID 在不同 bundle 下必须不同。 */
    @Test
    public void bundleScopeIsPartOfIdentity() {
        assertNotEquals(
                WKCacheKeys.entryIdentity("b:p1", "m1", 0),
                WKCacheKeys.entryIdentity("b:p2", "m1", 0));
    }

    /** basename 只含文件系统安全字符且定长，无需再清洗。 */
    @Test
    public void basenameIsHexAndFixedLength() {
        String base = WKCacheKeys.basenameForIdentity(
                WKCacheKeys.entryIdentity("b:p", "m/../../etc/passwd", 0));
        assertEquals(64, base.length());
        assertTrue(base.matches("[0-9a-f]{64}"));
        assertFalse(base.contains("/"));
        assertFalse(base.contains("."));
    }

    /** payload 摘要跨"两次独立构造"稳定，且内容不同即不同。 */
    @Test
    public void payloadDigestIsContentStable() {
        assertEquals(digestA(), digestA());
        assertNotEquals(digestA(), digestB());
    }

    private static String digestA() {
        return WKCacheKeys.payloadDigest(
                new ArrayList<>(Arrays.asList("m1", "m2")),
                new ArrayList<>(Arrays.asList(1700000000L, 1700000001L)),
                new ArrayList<>(Arrays.asList("u1", "u2")),
                new ArrayList<>(Arrays.asList("{\"type\":6}", "{\"type\":1}")));
    }

    private static String digestB() {
        return WKCacheKeys.payloadDigest(
                new ArrayList<>(Arrays.asList("m1", "m9")),
                new ArrayList<>(Arrays.asList(1700000000L, 1700000002L)),
                new ArrayList<>(Arrays.asList("u1", "u2")),
                new ArrayList<>(Arrays.asList("{\"type\":6}", "{\"type\":1}")));
    }

    /** 字段串接不可被跨字段歧义构造（分隔符不可出现在业务值里）。 */
    @Test
    public void fieldSeparatorPreventsAmbiguity() {
        List<String> ids1 = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> ids2 = new ArrayList<>(Arrays.asList("ab", ""));
        List<Long> t = new ArrayList<>(Arrays.asList(0L, 0L));
        List<String> u = new ArrayList<>(Arrays.asList("", ""));
        List<String> c = new ArrayList<>(Arrays.asList("", ""));
        assertNotEquals(
                WKCacheKeys.payloadDigest(ids1, t, u, c),
                WKCacheKeys.payloadDigest(ids2, t, u, c));
    }

    /** framing：真实分隔符出现在业务值里时身份串仍须单射（v1 下这两种输入同名）。 */
    @Test
    public void separatorInsideValueCannotForgeAnotherIdentity() {
        String a = WKCacheKeys.entryIdentity("b:p", "xid:y", 0);
        String b = WKCacheKeys.entryIdentity("b:pid:x", "y", 0);
        assertNotEquals(a, b);
        assertNotEquals(WKCacheKeys.basenameForIdentity(a), WKCacheKeys.basenameForIdentity(b));
    }

    /**
     * payloadDigest 的 framing：必须命中**真实的 v1 别名对**。
     *
     * 旧编码（纯 0x1F 连接、无长度前缀）下，单条目的
     *   (id = "a" + 0x1F + "0", ts = 0, uid = "",        content = "x")
     *   (id = "a",              ts = 0, uid = "0" + 0x1F, content = "x")
     * 序列化为同一字节串（0x1F 的位置在"字段内"与"字段间"无法区分）。
     * 长度前缀编码后两者必不同。原用例的两组输入在旧编码下本就不同，对本缺陷
     * 零覆盖——不要退回那种写法。
     */
    @Test
    public void separatorInsidePayloadFieldCannotForgeCollision() {
        String us = String.valueOf((char) 0x1f);
        String d1 = WKCacheKeys.payloadDigest(
                new ArrayList<>(Arrays.asList("a" + us + "0")),
                new ArrayList<>(Arrays.asList(0L)),
                new ArrayList<>(Arrays.asList("")),
                new ArrayList<>(Arrays.asList("x")));
        String d2 = WKCacheKeys.payloadDigest(
                new ArrayList<>(Arrays.asList("a")),
                new ArrayList<>(Arrays.asList(0L)),
                new ArrayList<>(Arrays.asList("0" + us)),
                new ArrayList<>(Arrays.asList("x")));
        assertNotEquals("genuine v1 alias must be separated", d1, d2);
    }

    /** 普通聊天身份：B-1 的标点反例必须被区分。 */
    @Test
    public void chatIdentityDistinguishesPunctuationVariants() {
        String a = WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m:a");
        String b = WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m?a");
        assertNotEquals(a, b);
        assertNotEquals(WKCacheKeys.basenameForIdentity(a), WKCacheKeys.basenameForIdentity(b));
    }

    /** 普通聊天身份：仅第 64 字符之后不同的 clientMsgNO 不可塌缩。 */
    @Test
    public void chatIdentityDistinguishesIdsDifferingAfterCharacter64() {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 64; i++) p.append('x');
        String a = WKCacheKeys.chatFileIdentity((byte) 1, "ch1", p + "AAAA");
        String b = WKCacheKeys.chatFileIdentity((byte) 1, "ch1", p + "BBBB");
        assertNotEquals(a, b);
        assertNotEquals(WKCacheKeys.basenameForIdentity(a), WKCacheKeys.basenameForIdentity(b));
    }

    /** 普通聊天身份：频道参与身份，同一 clientMsgNO 跨频道不共享缓存。 */
    @Test
    public void chatIdentityIncludesChannel() {
        assertNotEquals(WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m1"),
                        WKCacheKeys.chatFileIdentity((byte) 1, "ch2", "m1"));
        assertNotEquals(WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m1"),
                        WKCacheKeys.chatFileIdentity((byte) 2, "ch1", "m1"));
    }

    /** 普通聊天与转发是两个命名空间。 */
    @Test
    public void chatAndForwardIdentitiesAreSeparateNamespaces() {
        assertNotEquals(WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m1"),
                        WKCacheKeys.entryIdentity("ch1", "m1", 0));
    }

    /** bundle 内重复 message_id 不得自别名到同一身份（P2-3）。 */
    @Test
    public void duplicateMessageIdInSameBundleStaysDistinct() {
        String a = WKCacheKeys.entryIdentity(
                WKCacheKeys.scopeWithIndex("b:p", 0), "dup", 0);
        String b = WKCacheKeys.entryIdentity(
                WKCacheKeys.scopeWithIndex("b:p", 1), "dup", 1);
        assertNotEquals(a, b);
        assertNotEquals(WKCacheKeys.basenameForIdentity(a),
                        WKCacheKeys.basenameForIdentity(b));
    }

    /** scopeWithIndex 的下标也走长度前缀，不可被构造碰撞。 */
    @Test
    public void scopeWithIndexIsInjective() {
        assertNotEquals(WKCacheKeys.scopeWithIndex("b:p", 11),
                        WKCacheKeys.scopeWithIndex("b:p1", 1));
    }

    /** SEP 必须是单个 0x1F 控制字符——源码里若被 Unicode 预处理改写会在此暴露。 */
    @Test
    public void separatorIsSingleUnitSeparatorChar() {
        String id = WKCacheKeys.entryIdentity("s", "m", 0);
        assertTrue(id.indexOf('\u001f') >= 0);
    }
}
