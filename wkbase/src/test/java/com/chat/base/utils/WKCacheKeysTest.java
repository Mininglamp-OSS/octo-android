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
}
