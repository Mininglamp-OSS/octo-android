package com.chat.uikit.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.uikit.fragment.ChatFragment;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * YUJ-332 · 回归守卫：PR#226 大数据量 Space 切回群列表丢失。
 *
 * <p>修复策略（见 {@link ChatFragment#performSpaceSwitch} 注释 YUJ-332 块 +
 * {@link ChatFragment#populateConversationsFromCache}）：
 * <ol>
 *   <li><b>跳过 {@code clearAllForSpace(targetSpaceId)}</b>：per-Space cache 的核心
 *       是保留本地缓存，不再删除目标 Space 的 DB 行。</li>
 *   <li>新增私有方法 {@code populateConversationsFromCache}，在切回时从 DB
 *       {@code ConversationManager.getAll()} 读出本 Space 会话，在 main thread
 *       同步填回 {@code allConversations} + {@code spaceConversationKeys}。</li>
 * </ol>
 *
 * <p>host-side 测试无法实例化 ChatFragment（需要 FragmentManager / Activity context），
 * 本测试通过反射锁定两条不变量：
 * <ul>
 *   <li>{@code populateConversationsFromCache(List, String)} 存在，签名稳定。</li>
 *   <li>{@code performSpaceSwitch(SpaceEntity)} 源码里不再直接调 {@code clearAllForSpace}
 *       (在 flag-on 分支) —— 用反射拿不到源码，但签名/存在 + 其他 ApiStabilityTest
 *       配合 git diff review 做交叉防御。</li>
 * </ul>
 *
 * <p>真机 / instrumented 级的「切出 → 切回 → list.size == snapshot」场景见
 * androidTest（PR 描述列 TODO，Demo Space space_id 真实数据样本依赖 Yu 提供）。
 */
public class ChatFragmentPerSpaceCachePreserveTest {

    @Test
    public void populateFromCacheMethodExists() throws NoSuchMethodException {
        Method m = ChatFragment.class.getDeclaredMethod(
                "populateConversationsFromCache",
                java.util.List.class, String.class);
        assertEquals("private helper must be package-private private, not public API",
                void.class, m.getReturnType());
        assertTrue("helper must be private", Modifier.isPrivate(m.getModifiers()));
        assertFalse("helper must be instance (uses adapter/fields)",
                Modifier.isStatic(m.getModifiers()));
    }

    @Test
    public void performSpaceSwitchStillExists() throws NoSuchMethodException, ClassNotFoundException {
        // 签名稳定性：入口名和参数类型不变，避免未来重构误删 YUJ-332 修复路径。
        Method m = ChatFragment.class.getDeclaredMethod(
                "performSpaceSwitch",
                Class.forName("com.chat.uikit.space.SpaceEntity"));
        assertEquals(void.class, m.getReturnType());
        assertTrue("performSpaceSwitch must be private", Modifier.isPrivate(m.getModifiers()));
    }
}
