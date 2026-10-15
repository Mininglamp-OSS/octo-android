package com.chat.uikit.chat.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.chat.base.utils.WKCacheKeys;
import org.junit.Test;

/** 普通聊天受管理缓存磁盘名回归（B-1 普通聊天分支）。 */
public class WKFileProviderCacheNameTest {

    @Test
    public void punctuationVariantClientMsgNoProduceDistinctFileNames() {
        String a = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m:a"), "pdf", "A.pdf");
        String b = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m?a"), "pdf", "B.pdf");
        assertNotEquals(a, b);
    }

    @Test
    public void clientMsgNoDifferingAfterCharacter64ProduceDistinctFileNames() {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 64; i++) p.append('x');
        String a = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", p + "AAAA"), "pdf", "A.pdf");
        String b = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", p + "BBBB"), "pdf", "B.pdf");
        assertNotEquals(a, b);
    }

    @Test
    public void fileNameIsHexBasenamePlusDisplayExtension() {
        String name = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m1"), "bin", "report.pdf");
        assertTrue(name.matches("[0-9a-f]{64}\\.pdf"));
    }

    @Test
    public void traversalAttemptStaysSingleSegment() {
        String name = WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "../../etc/passwd"),
                "pdf", "../../x.pdf");
        assertEquals(-1, name.indexOf('/'));
        assertTrue(name.matches("[0-9a-f]{64}\\.pdf"));
    }

    /** 旧名与新名在形状上永不相交——清理逻辑的自删防护依赖这一点。 */
    @Test
    public void legacyShapeNeverMatchesIdentityDerivedShape() {
        assertTrue(WKFileProvider.buildCacheFileNameForIdentity(
                WKCacheKeys.chatFileIdentity((byte) 1, "ch1", "m1"), "pdf", "report.pdf")
                .matches("[0-9a-f]{64}\\..*"));
        assertTrue(!"report.pdf".matches("[0-9a-f]{64}\\..*"));
    }

    /** 语音路径段必须收敛掉穿越结构（P1-3）。 */
    @Test
    public void safePathSegmentNeutralizesTraversal() {
        assertEquals(-1, WKFileProvider.safePathSegment("../../../../x", "voice").indexOf('/'));
        assertEquals("voice", WKFileProvider.safePathSegment("", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment(null, "voice"));
    }

    /**
     * 控制字符不得把点序列剥离掩蔽过去（P2-5）。
     *
     * 这些就是缺陷输入：`..` 中间夹 NUL 时，旧 pass 顺序下 `.replace("..","")`
     * 匹配不上，控制字符随后才被删，函数吐出 `..`。断言必须打在这些输入上，
     * 打在裸 `".."` 上会误判为已修复。
     */
    @Test
    public void controlCharsCannotMaskDotSequences() {
        String nul = String.valueOf((char) 0x00);
        assertEquals("voice", WKFileProvider.safePathSegment("." + nul + ".", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment(
                "." + nul + "." + nul + "." + nul + ".", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment(" ." + nul + ". ", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment("x/." + nul + ".", "voice"));
        // 夹在正常字符之间时只删点序列，不吃掉周围内容
        assertEquals("ab", WKFileProvider.safePathSegment("a" + nul + "." + nul + ".b", "voice"));
        // DEL (0x7f) 同为不可见控制字符，原正则漏掉
        assertEquals("voice", WKFileProvider.safePathSegment(
                "." + ((char) 0x7f) + ".", "voice"));
    }

    /** 纯点残留（任意长度）都没有可用语义，必须落 fallback。 */
    @Test
    public void dotOnlyResidueFallsBack() {
        assertEquals("voice", WKFileProvider.safePathSegment(".", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment("..", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment("....", "voice"));
        assertEquals("voice", WKFileProvider.safePathSegment("   ", "voice"));
    }

    /**
     * 语音路径不变量：收敛后的段拼上扩展名必须仍是目录内的单段名（P1-3）。
     *
     * 注：WKVoiceProvider 拼的是 fileDir + seg + ".amr"（无分隔符），所以即便
     * seg 是 ".." 也只得到 "..amr"、不会逃逸；但一旦有调用方在 seg 之后拼
     * 分隔符，点残留立刻变成真穿越。因此这里既断言无分隔符、也断言无点残留。
     */
    @Test
    public void voicePathSegmentStaysInsideDirectory() {
        String nul = String.valueOf((char) 0x00);
        String[] hostile = {
                "../../../../x", "." + nul + ".", ".", "..",
                "a/../b", "x" + nul + "/" + nul + "y",
        };
        for (String h : hostile) {
            String seg = WKFileProvider.safePathSegment(h, "voice");
            assertEquals("no separator in: " + seg, -1, seg.indexOf('/'));
            assertEquals("no separator in: " + seg, -1, seg.indexOf('\\'));
            assertTrue("dot-only residue: " + seg, !seg.chars().allMatch(c -> c == '.'));
            assertTrue("empty segment", !seg.isEmpty());
        }
    }
}
