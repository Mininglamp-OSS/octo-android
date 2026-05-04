package com.chat.uikit.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * YUJ-330 · Jerry review 2026-05-04 06:49Z Critical #1 · {@link SpaceCacheFlag#computeBucket}
 * 必须对 {@code String.hashCode() == Integer.MIN_VALUE} 的 uid 也返回非负 bucket，且 bucket=0
 * 时不会对该 uid "反而强开" per-Space path。
 *
 * <p>固定 fixture：字符串 {@code "polygenelubricants"} 的 {@code hashCode()} 恰好等于
 * {@link Integer#MIN_VALUE}（Java String hash 算法公认示例）。原实现 {@code Math.abs(MIN_VALUE)}
 * 溢出回 {@code MIN_VALUE}（负数），再 {@code % 100} 得负数 → 与 {@code bucket} 比较恒成立，
 * 灰度 bucket=0 意图"全关"却对该 uid 变"强开"。修法 {@code & 0x7FFFFFFF} 清符号位，
 * 纯位运算不溢出，对所有 32-bit int 均正确。
 *
 * <p>本测试只覆盖 {@link SpaceCacheFlag#computeBucket(String)} 工具方法，避开 {@code isEnabled}
 * 的 SP + BuildConfig + BackfillGate 混合链路（那些层在 {@link SpaceCacheFlagTest} /
 * {@link SpaceCacheFlagBackfillGateTest} 已覆盖），保证回归锁点清晰。
 */
public class SpaceCacheFlagBucketHashTest {

    /** 著名的 "hashCode == Integer.MIN_VALUE" 短字符串（Effective Java Item 9 引文）。 */
    private static final String MIN_HASH_UID = "polygenelubricants";

    @Test
    public void fixtureReallyHashesToIntegerMinValue() {
        // 先锁定 fixture 真的触发 MIN_VALUE，否则后面的断言失去意义。
        assertEquals(Integer.MIN_VALUE, MIN_HASH_UID.hashCode());
    }

    @Test
    public void computeBucketNeverNegativeForIntegerMinValue() {
        int bucket = SpaceCacheFlag.computeBucket(MIN_HASH_UID);
        assertTrue("bucket must be non-negative, got=" + bucket, bucket >= 0);
        assertTrue("bucket must be < 100, got=" + bucket, bucket < 100);
    }

    @Test
    public void bucketZeroRolloutKeepsMinValueUidDisabled() {
        // 灰度 bucket=0 意图"全关"。判定逻辑 computeBucket(uid) < bucket 必须为 false
        // 对所有 uid —— 包括 MIN_HASH_UID。若修复前的 Math.abs 溢出路径还在，
        // computeBucket 会返回负数 → 条件为 true → 本断言失败。
        int bucket = SpaceCacheFlag.computeBucket(MIN_HASH_UID);
        assertTrue("bucket=0 rollout must NOT enable MIN_VALUE-hash uid", bucket >= 0);
        assertNotEquals("computeBucket must not return negative value", true, bucket < 0);
    }

    @Test
    public void computeBucketDeterministicAcrossCalls() {
        int b1 = SpaceCacheFlag.computeBucket("user-abc");
        int b2 = SpaceCacheFlag.computeBucket("user-abc");
        assertEquals(b1, b2);
    }

    @Test
    public void computeBucketEmptyStringMapsToZero() {
        // "".hashCode() == 0 → (0 & 0x7FFFFFFF) % 100 == 0，回归锁点。
        assertEquals(0, SpaceCacheFlag.computeBucket(""));
    }

    @Test
    public void computeBucketDistributesAcrossRange() {
        // 经验性：若有 5 个不同 uid 全落同一个 bucket，大概率是实现走偏（例如忘了取模）。
        // 松散校验，不追求均匀分布；只要 ≥ 2 个桶有命中即可。
        int[] seen = new int[100];
        String[] uids = {"a", "b", "c", "d", "e", "f", "g", "h", "polygenelubricants", "yu"};
        for (String uid : uids) {
            seen[SpaceCacheFlag.computeBucket(uid)]++;
        }
        int nonZero = 0;
        for (int v : seen) if (v > 0) nonZero++;
        assertTrue("expected multiple buckets hit, got " + nonZero, nonZero >= 2);
    }
}
