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

    /**
     * 语音身份派生必须抗碰撞：punctuation 变体产生不同磁盘名（B-3）。
     *
     * 复现：消息 A (clientMsgNO = "m:a") 先下载到 m_a.amr（旧实现），消息 B
     * (clientMsgNO = "m?a") 的 exists 检查命中 A 的路径、直接播放 A 的字节。
     * 新实现下两者必须映射到不同的 64 位 hex basename。
     */
    @Test
    public void voiceIdentityDistinguishesPunctuationVariants() {
        String identityA = com.chat.base.utils.WKCacheKeys.voiceIdentity(
                (byte) 1, "ch1", "m:a");
        String identityB = com.chat.base.utils.WKCacheKeys.voiceIdentity(
                (byte) 1, "ch1", "m?a");
        String basenameA = com.chat.base.utils.WKCacheKeys.basenameForIdentity(identityA);
        String basenameB = com.chat.base.utils.WKCacheKeys.basenameForIdentity(identityB);

        assertNotEquals("punctuation variants must produce distinct basenames", basenameA, basenameB);
        assertTrue("voice basename is 64-hex", basenameA.matches("[0-9a-f]{64}"));
        assertTrue("voice basename is 64-hex", basenameB.matches("[0-9a-f]{64}"));

        // 旧实现的路径：相同（碰撞）
        String oldA = WKFileProvider.safePathSegment("m:a", "voice") + ".amr";
        String oldB = WKFileProvider.safePathSegment("m?a", "voice") + ".amr";
        assertEquals("old implementation collides", oldA, oldB);

        // 新实现的路径：不同
        String newA = basenameA + ".amr";
        String newB = basenameB + ".amr";
        assertNotEquals("new implementation distinguishes", newA, newB);
    }

    /** 语音身份：仅第 64 字符之后不同的 clientMsgNO 不可塌缩（B-3）。 */
    @Test
    public void voiceIdentityDistinguishesIdsDifferingAfterCharacter64() {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 64; i++) p.append('x');
        String a = com.chat.base.utils.WKCacheKeys.basenameForIdentity(
                com.chat.base.utils.WKCacheKeys.voiceIdentity((byte) 1, "ch1", p + "AAAA"));
        String b = com.chat.base.utils.WKCacheKeys.basenameForIdentity(
                com.chat.base.utils.WKCacheKeys.voiceIdentity((byte) 1, "ch1", p + "BBBB"));
        assertNotEquals(a, b);
    }

    /**
     * 语音缓存路径不变量：A 落盘后，B 的派生路径必须不等于 A 的路径
     * ——即 B 的 exists 分支不可能命中 A 的字节（B-3 的核心断言）。
     */
    @Test
    public void secondVoiceMessageCannotHitFirstMessagePath() {
        String dir = "/voice/1/ch1/";
        String pathA = dir + com.chat.base.utils.WKCacheKeys.basenameForIdentity(
                com.chat.base.utils.WKCacheKeys.voiceIdentity((byte) 1, "ch1", "m:a")) + ".amr";
        String pathB = dir + com.chat.base.utils.WKCacheKeys.basenameForIdentity(
                com.chat.base.utils.WKCacheKeys.voiceIdentity((byte) 1, "ch1", "m?a")) + ".amr";
        assertNotEquals("B must not resolve to A's cache path", pathA, pathB);
        // 频道也参与身份：同 clientMsgNO 跨频道不共享
        String pathC = dir + com.chat.base.utils.WKCacheKeys.basenameForIdentity(
                com.chat.base.utils.WKCacheKeys.voiceIdentity((byte) 2, "ch1", "m:a")) + ".amr";
        assertNotEquals(pathA, pathC);
    }

    /** boundedSaveName 必须截断超长名（预览页保存建议名的字节上限）。 */
    @Test
    public void boundedSaveNameTruncatesOverlongNames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('a');
        String out = WKFileProvider.boundedSaveName(sb + ".pdf", "file");
        assertTrue("must be byte-capped", out.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 200);
        assertTrue("extension preserved", out.endsWith(".pdf"));
    }
}
