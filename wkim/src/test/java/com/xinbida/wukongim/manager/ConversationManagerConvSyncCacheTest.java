package com.xinbida.wukongim.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;

/**
 * GH dmwork-android#251 Round-3 — conv sync space-cache 生命周期回归测试。
 *
 * <p>覆盖两个 blocker：
 * <ol>
 *   <li>stale cache：当 sync entry 的 space_id / my_source_space_id 为空时，
 *       {@code prefillSpaceExtrasFromConvSync} 必须主动 remove 对应 key。</li>
 *   <li>跨用户泄漏：{@code clearConvSyncSpaceCache} 必须同时清空两张 map。</li>
 * </ol>
 *
 * <p>用反射访问私有字段 / 方法，避开 ConversationManager 单例对 Android Context / DB
 * 的依赖（getter 也走相同 map，等价路径覆盖）。
 */
public class ConversationManagerConvSyncCacheTest {

    private ConversationManager mgr;
    private Method prefill;
    private ConcurrentHashMap<String, String> spaceMap;
    private ConcurrentHashMap<String, String> externalMap;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mgr = ConversationManager.getInstance();

        prefill = ConversationManager.class.getDeclaredMethod(
                "prefillSpaceExtrasFromConvSync",
                String.class, byte.class, String.class, String.class);
        prefill.setAccessible(true);

        Field spaceField = ConversationManager.class.getDeclaredField("convSyncSpaceMap");
        spaceField.setAccessible(true);
        spaceMap = (ConcurrentHashMap<String, String>) spaceField.get(mgr);

        Field externalField = ConversationManager.class.getDeclaredField("convSyncExternalMap");
        externalField.setAccessible(true);
        externalMap = (ConcurrentHashMap<String, String>) externalField.get(mgr);

        // 单例可能被其他 test 污染，每次跑前清干净。
        mgr.clearConvSyncSpaceCache();
    }

    @Test
    public void prefillWritesNonEmptyValues() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");

        assertEquals("space-A", spaceMap.get("ch1"));
        assertEquals("ext-A", externalMap.get("ch1"));
        assertEquals("space-A", mgr.getConvSyncSpaceId("ch1"));
        assertEquals("ext-A", mgr.getConvSyncMySourceSpaceId("ch1"));
    }

    /** Blocker 1：value 为空必须 remove 旧条目（stale-cache 修复）。 */
    @Test
    public void prefillRemovesEntriesWhenSpaceIdGoesEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        assertEquals("space-A", spaceMap.get("ch1"));

        // 服务端在下一次 sync 中把 space_id 清空（群被移出 space）
        prefill.invoke(mgr, "ch1", (byte) 2, "", "ext-A");

        assertNull("space_id 空值必须从 map 中移除", spaceMap.get("ch1"));
        assertEquals("my_source_space_id 仍在则保留", "ext-A", externalMap.get("ch1"));
    }

    @Test
    public void prefillRemovesEntriesWhenMySourceGoesEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        assertEquals("ext-A", externalMap.get("ch1"));

        // 当前用户退出该 space 关系
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", null);

        assertNull("my_source_space_id 空值必须从 map 中移除", externalMap.get("ch1"));
        assertEquals("space_id 仍在则保留", "space-A", spaceMap.get("ch1"));
    }

    @Test
    public void prefillRemovesBothWhenBothEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        prefill.invoke(mgr, "ch1", (byte) 2, null, null);

        assertNull(spaceMap.get("ch1"));
        assertNull(externalMap.get("ch1"));
    }

    /** Blocker 2：clearConvSyncSpaceCache 必须把两张 map 清空（跨用户泄漏防御）。 */
    @Test
    public void clearConvSyncSpaceCacheEmptiesBothMaps() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        prefill.invoke(mgr, "ch2", (byte) 2, "space-B", "ext-B");
        assertEquals(2, spaceMap.size());
        assertEquals(2, externalMap.size());

        mgr.clearConvSyncSpaceCache();

        assertTrue("convSyncSpaceMap 必须被清空", spaceMap.isEmpty());
        assertTrue("convSyncExternalMap 必须被清空", externalMap.isEmpty());
        assertNull(mgr.getConvSyncSpaceId("ch1"));
        assertNull(mgr.getConvSyncMySourceSpaceId("ch2"));
    }

    @Test
    public void clearConvSyncSpaceCacheIsIdempotent() {
        mgr.clearConvSyncSpaceCache();
        mgr.clearConvSyncSpaceCache(); // 不应抛
        assertTrue(spaceMap.isEmpty());
        assertTrue(externalMap.isEmpty());
    }
}
