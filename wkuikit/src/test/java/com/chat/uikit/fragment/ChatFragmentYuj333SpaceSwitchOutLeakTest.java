package com.chat.uikit.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.base.space.SpaceFilter;
import com.xinbida.wukongim.entity.WKChannelType;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YUJ-333 · 回归守卫：PR#226 切 Space 后串数据（YUJ-332 修复制造 SpaceFilter 泄漏）。
 *
 * <p>场景：
 * <pre>
 *     登录 → Demo Space Space → 切到 Space B
 *     (预期) adapter 只含 B 的群；(实测 7495730f) 短暂显示 Demo 的群
 * </pre>
 *
 * <p>本测试走两条路径：
 * <ol>
 *   <li><b>纯函数过滤</b>：{@link SpaceFilter#shouldSkipChannelForSpace(String, byte, String,
 *       SpaceFilter.ChannelInfoProvider)} 显式传入 {@code targetSpaceId=B}，
 *       对Demo群返回 {@code true}（拒绝），对 B 群返回 {@code false}（放行）。
 *       这保证 {@code populateConversationsFromCache} 用 targetSpaceId 过滤时
 *       不会受 SP 读时序污染。</li>
 *   <li><b>源码守卫</b>：扫描 {@code ChatFragment.java}，
 *       确认修复后的 {@code performSpaceSwitch} 包含
 *       {@code syncSpaceKeysToGlobal()} 调用（UI 清理同一 tick 同步全局 Set），
 *       并且 {@code populateConversationsFromCache} 用 {@code effectiveSpaceId}
 *       而非 {@code isChannelInCurrentSpace} 做过滤。</li>
 * </ol>
 */
public class ChatFragmentYuj333SpaceSwitchOutLeakTest {

    private static final String SPACE_MINGLUE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 32 hex 占位
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final byte GROUP = WKChannelType.GROUP;

    /** 固定返回 groupSpaceId / mySourceSpaceId / myCached 的 Stub provider。 */
    private static final class StubProvider implements SpaceFilter.ChannelInfoProvider {
        final String groupSpaceId;
        final String mySourceSpaceId;
        final boolean myCached;

        StubProvider(String groupSpaceId, String mySourceSpaceId, boolean myCached) {
            this.groupSpaceId = groupSpaceId;
            this.mySourceSpaceId = mySourceSpaceId;
            this.myCached = myCached;
        }

        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            return groupSpaceId;
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            return mySourceSpaceId;
        }

        @Override
        public boolean isMyMembershipCached(String channelID, byte channelType) {
            return myCached;
        }
    }

    /**
     * 核心回归：切出 Demo 到 B，Demo群必须被 targetSpaceId=B 的过滤拒掉。
     *
     * <p>覆盖 populateConversationsFromCache 的 GROUP 分支：
     * groupSpaceId=Demo, mySourceSpaceId=Demo (我以 Demo 身份加入), myCached=true。
     * SpaceFilter 走 cached-mismatch 分支 → skip=true → 条目被拒。
     */
    @Test
    public void minglueGroupRejectedWhenSwitchingToSpaceB() {
        StubProvider stub = new StubProvider(SPACE_MINGLUE, SPACE_MINGLUE, true);
        boolean skip = SpaceFilter.shouldSkipChannelForSpace(
                "s" + SPACE_MINGLUE + "_peer001", GROUP, SPACE_B, stub);
        assertTrue("Demo群在切到 B 时必须被拒绝，不得串入 B 的 adapter", skip);
    }

    /**
     * 反向：切回 Demo 时，Demo群必须放行（不能误杀 per-Space cache 场景）。
     */
    @Test
    public void minglueGroupKeptWhenSwitchingBackToMinglue() {
        StubProvider stub = new StubProvider(SPACE_MINGLUE, SPACE_MINGLUE, true);
        boolean skip = SpaceFilter.shouldSkipChannelForSpace(
                "s" + SPACE_MINGLUE + "_peer001", GROUP, SPACE_MINGLUE, stub);
        assertFalse("Demo群在Demo Space 下必须放行（per-Space cache 切回秒开）", skip);
    }

    /**
     * B 的原生群在切去 B 时必须放行。
     */
    @Test
    public void bNativeGroupKeptWhenSwitchingToSpaceB() {
        StubProvider stub = new StubProvider(SPACE_B, SPACE_B, true);
        boolean skip = SpaceFilter.shouldSkipChannelForSpace(
                "s" + SPACE_B + "_peer001", GROUP, SPACE_B, stub);
        assertFalse("B 的群在切到 B 时必须放行", skip);
    }

    /**
     * 外部群兜底：我在 Demo 以外部成员身份（source=Demo）加入一个归属 B 的群。
     * 切到 Demo 时该外部群应放行（cached-external-member）。
     */
    @Test
    public void externalGroupKeptByExternalMemberSourceSpace() {
        StubProvider stub = new StubProvider(SPACE_B, SPACE_MINGLUE, true);
        boolean skip = SpaceFilter.shouldSkipChannelForSpace(
                "s" + SPACE_B + "_peer999", GROUP, SPACE_MINGLUE, stub);
        assertFalse("外部群应按 mySourceSpaceId 放行，不应被 targetSpaceId 过滤误杀", skip);
    }

    // ------------------------------------------------------------------
    // 源码守卫：防止未来重构误删 YUJ-333 修复路径。
    // ------------------------------------------------------------------

    /**
     * performSpaceSwitch 里必须有 syncSpaceKeysToGlobal() —— 清完本地 Set
     * 就立刻同步到 WKUIKitApplication，关 push-in-gap 数据泄漏窗口。
     *
     * <p>Yu 2026-05-04 11:59Z 真机复现证明：清本地 Set 但不同步全局 Set →
     * IO 期间旧 Space 的 push 通过 isInCurrentSpace() 放行 → 串台。
     */
    @Test
    public void performSpaceSwitchSyncsGlobalKeysEagerly() throws IOException {
        String src = readFragmentSource();
        int switchStart = src.indexOf("private void performSpaceSwitch");
        assertTrue("performSpaceSwitch 源码必须存在", switchStart > 0);
        int populateStart = src.indexOf("private void populateConversationsFromCache");
        assertTrue("populateConversationsFromCache 源码必须存在", populateStart > 0);

        // 取 performSpaceSwitch → populateConversationsFromCache 之间的 slice，
        // 防止匹配到 populateConversationsFromCache 内部的 syncSpaceKeysToGlobal。
        int sliceEnd = Math.max(populateStart, switchStart + 1);
        // 如果 populate 在 switch 之前定义（当前布局），改为按方法体闭合扫：
        if (populateStart < switchStart) {
            sliceEnd = src.length();
        }
        String slice = src.substring(switchStart, sliceEnd);

        // 必须在 UI 清理 tick 调用 syncSpaceKeysToGlobal()
        assertTrue("performSpaceSwitch 必须在 spaceConversationKeys.clear() 后主动"
                        + " syncSpaceKeysToGlobal() 防止 push-in-gap 泄漏（YUJ-333）",
                slice.contains("syncSpaceKeysToGlobal()"));

        // 顺序：spaceConversationKeys.clear 出现的第一个位置必须在同一段里且
        // 在 syncSpaceKeysToGlobal 之前（在 UI 清理分支里）。
        int clearIdx = slice.indexOf("spaceConversationKeys.clear()");
        int syncIdx = slice.indexOf("syncSpaceKeysToGlobal()");
        assertTrue("spaceConversationKeys.clear() 必须在 performSpaceSwitch 里被调",
                clearIdx >= 0);
        assertTrue("syncSpaceKeysToGlobal() 必须在 clear 之后调用",
                syncIdx > clearIdx);
    }

    /**
     * populateConversationsFromCache 必须使用 targetSpaceId（通过
     * effectiveSpaceId 的纯函数签名），不能依赖 SP 读取的 isChannelInCurrentSpace。
     *
     * <p>YUJ-333 · 快速 A→B→C 切换 race：IO post 回主线程时 SP 已可能被后续
     * 切换盖写。闭包里的 targetSpaceId 是本次切换的真实目标。
     */
    @Test
    public void populateUsesExplicitTargetSpaceIdForFiltering() throws IOException {
        String src = readFragmentSource();
        int start = src.indexOf("private void populateConversationsFromCache");
        assertTrue("populateConversationsFromCache 源码必须存在", start > 0);
        // 取从方法头到末尾 dbQueryCompleted = true 之间的 slice
        int end = src.indexOf("dbQueryCompleted = true;", start);
        assertTrue("populateConversationsFromCache 方法体须完整", end > start);
        String body = src.substring(start, end);

        // 显式取 targetSpaceId，回落走 SpaceFilter.getCurrentSpaceId()。
        assertTrue("populate 必须计算 effectiveSpaceId（优先 targetSpaceId，空则回落 SP）",
                body.contains("effectiveSpaceId"));
        assertTrue("effectiveSpaceId 的空回落必须走 SpaceFilter.getCurrentSpaceId()",
                Pattern.compile("effectiveSpaceId[^;]*getCurrentSpaceId\\(\\)",
                        Pattern.DOTALL).matcher(body).find());

        // GROUP 分支必须把 effectiveSpaceId 传给 SpaceFilter 纯函数版，
        // 而不是回调 isChannelInCurrentSpace（后者总是读 SP）。
        Matcher m = Pattern.compile(
                "shouldSkipChannelForSpace\\(.*?effectiveSpaceId",
                Pattern.DOTALL).matcher(body);
        assertTrue("GROUP 分支必须把 effectiveSpaceId 传给 SpaceFilter.shouldSkipChannelForSpace",
                m.find());

        // PERSONAL 分支必须走带 currentSpaceId 的 shouldSkipMessageForSpace。
        Matcher personalM = Pattern.compile(
                "shouldSkipMessageForSpace\\(.*?effectiveSpaceId",
                Pattern.DOTALL).matcher(body);
        assertTrue("PERSONAL 分支必须把 effectiveSpaceId 传给 SpaceFilter.shouldSkipMessageForSpace",
                personalM.find());

        // 不能再出现旧的 isChannelInCurrentSpace 依赖（会读 SP）。
        assertFalse("populateConversationsFromCache 不得再用 isChannelInCurrentSpace（读 SP 有竞态）",
                body.contains("isChannelInCurrentSpace("));
    }

    /**
     * 切出 + 切入一轮「Demo → B → Demo」列表内容回归：用显式 targetSpaceId
     * 模拟两次 populate 过滤链，断言最终集合互不串台。
     */
    @Test
    public void switchOutThenBackProducesDisjointLists() {
        List<String> minglueGroups = Arrays.asList(
                "s" + SPACE_MINGLUE + "_m1",
                "s" + SPACE_MINGLUE + "_m2",
                "s" + SPACE_MINGLUE + "_m3");
        List<String> bGroups = Arrays.asList(
                "s" + SPACE_B + "_b1",
                "s" + SPACE_B + "_b2");

        // DB 里（per-Space cache）同时存有 Demo 和 B 的群
        Set<String> all = new HashSet<>();
        all.addAll(minglueGroups);
        all.addAll(bGroups);

        // 切到 B：过滤应只留 bGroups
        Set<String> visibleOnB = new HashSet<>();
        for (String cid : all) {
            StubProvider provider = cid.startsWith("s" + SPACE_MINGLUE)
                    ? new StubProvider(SPACE_MINGLUE, SPACE_MINGLUE, true)
                    : new StubProvider(SPACE_B, SPACE_B, true);
            if (!SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_B, provider)) {
                visibleOnB.add(cid);
            }
        }
        assertEquals("切到 B 时 adapter 只含 B 的群，不含Demo群（YUJ-333 核心断言）",
                new HashSet<>(bGroups), visibleOnB);

        // 切回 Demo：过滤应只留 minglueGroups
        Set<String> visibleOnMinglue = new HashSet<>();
        for (String cid : all) {
            StubProvider provider = cid.startsWith("s" + SPACE_MINGLUE)
                    ? new StubProvider(SPACE_MINGLUE, SPACE_MINGLUE, true)
                    : new StubProvider(SPACE_B, SPACE_B, true);
            if (!SpaceFilter.shouldSkipChannelForSpace(cid, GROUP, SPACE_MINGLUE, provider)) {
                visibleOnMinglue.add(cid);
            }
        }
        assertEquals("切回Demo时 adapter 只含Demo群，不含 B 群",
                new HashSet<>(minglueGroups), visibleOnMinglue);
    }

    // ------------------------------------------------------------------

    private static String readFragmentSource() throws IOException {
        // 测试从 wkuikit/ 模块根运行；ChatFragment.java 相对路径稳定。
        Path p = Paths.get("src/main/java/com/chat/uikit/fragment/ChatFragment.java");
        if (!Files.exists(p)) {
            // 有的执行环境以仓库根为 CWD（gradle :wkuikit:testDebugUnitTest）。
            p = Paths.get("wkuikit/src/main/java/com/chat/uikit/fragment/ChatFragment.java");
        }
        assertTrue("ChatFragment.java 源码必须存在: " + p.toAbsolutePath(), Files.exists(p));
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
