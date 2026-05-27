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
    private Field snapshotField;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mgr = ConversationManager.getInstance();

        prefill = ConversationManager.class.getDeclaredMethod(
                "prefillSpaceExtrasFromConvSync",
                String.class, byte.class, String.class, String.class);
        prefill.setAccessible(true);

        snapshotField = ConversationManager.class.getDeclaredField("spaceCacheSnapshot");
        snapshotField.setAccessible(true);

        mgr.clearConvSyncSpaceCache();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, String> getSpaceMap() throws Exception {
        Object snapshot = snapshotField.get(mgr);
        Field f = snapshot.getClass().getDeclaredField("spaceMap");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, String>) f.get(snapshot);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, String> getExternalMap() throws Exception {
        Object snapshot = snapshotField.get(mgr);
        Field f = snapshot.getClass().getDeclaredField("externalMap");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, String>) f.get(snapshot);
    }

    @Test
    public void prefillWritesNonEmptyValues() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");

        assertEquals("space-A", getSpaceMap().get("ch1"));
        assertEquals("ext-A", getExternalMap().get("ch1"));
        assertEquals("space-A", mgr.getConvSyncSpaceId("ch1"));
        assertEquals("ext-A", mgr.getConvSyncMySourceSpaceId("ch1"));
    }

    /** Blocker 1：value 为空必须 remove 旧条目（stale-cache 修复）。 */
    @Test
    public void prefillRemovesEntriesWhenSpaceIdGoesEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        assertEquals("space-A", getSpaceMap().get("ch1"));

        prefill.invoke(mgr, "ch1", (byte) 2, "", "ext-A");

        assertNull("space_id 空值必须从 map 中移除", getSpaceMap().get("ch1"));
        assertEquals("my_source_space_id 仍在则保留", "ext-A", getExternalMap().get("ch1"));
    }

    @Test
    public void prefillRemovesEntriesWhenMySourceGoesEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        assertEquals("ext-A", getExternalMap().get("ch1"));

        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", null);

        assertNull("my_source_space_id 空值必须从 map 中移除", getExternalMap().get("ch1"));
        assertEquals("space_id 仍在则保留", "space-A", getSpaceMap().get("ch1"));
    }

    @Test
    public void prefillRemovesBothWhenBothEmpty() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        prefill.invoke(mgr, "ch1", (byte) 2, null, null);

        assertNull(getSpaceMap().get("ch1"));
        assertNull(getExternalMap().get("ch1"));
    }

    /** Blocker 2：clearConvSyncSpaceCache 必须把两张 map 清空（跨用户泄漏防御）。 */
    @Test
    public void clearConvSyncSpaceCacheEmptiesBothMaps() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        prefill.invoke(mgr, "ch2", (byte) 2, "space-B", "ext-B");
        assertEquals(2, getSpaceMap().size());
        assertEquals(2, getExternalMap().size());

        mgr.clearConvSyncSpaceCache();

        assertTrue("convSyncSpaceMap 必须被清空", getSpaceMap().isEmpty());
        assertTrue("convSyncExternalMap 必须被清空", getExternalMap().isEmpty());
        assertNull(mgr.getConvSyncSpaceId("ch1"));
        assertNull(mgr.getConvSyncMySourceSpaceId("ch2"));
    }

    @Test
    public void clearConvSyncSpaceCacheIsIdempotent() throws Exception {
        mgr.clearConvSyncSpaceCache();
        mgr.clearConvSyncSpaceCache();
        assertTrue(getSpaceMap().isEmpty());
        assertTrue(getExternalMap().isEmpty());
    }

    @Test
    public void applySpaceMembershipsReplacesAllEntries() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        assertEquals("space-A", getSpaceMap().get("ch1"));

        java.util.List<com.xinbida.wukongim.entity.WKSpaceMembership> memberships = new java.util.ArrayList<>();
        com.xinbida.wukongim.entity.WKSpaceMembership m = new com.xinbida.wukongim.entity.WKSpaceMembership();
        m.channel_id = "ch2";
        m.space_id = "space-B";
        m.my_source_space_id = "ext-B";
        memberships.add(m);

        mgr.applySpaceMemberships(memberships);

        assertNull("旧条目应被替换掉", getSpaceMap().get("ch1"));
        assertEquals("space-B", getSpaceMap().get("ch2"));
        assertEquals("ext-B", getExternalMap().get("ch2"));
    }

    @Test
    public void applySpaceMembershipsNullIsNoOp() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        mgr.applySpaceMemberships(null);
        assertEquals("null 不应清空缓存", "space-A", getSpaceMap().get("ch1"));
    }

    @Test
    public void applySpaceMembershipsEmptyListClearsAll() throws Exception {
        prefill.invoke(mgr, "ch1", (byte) 2, "space-A", "ext-A");
        mgr.applySpaceMemberships(new java.util.ArrayList<>());
        assertTrue("空列表应清空所有缓存", getSpaceMap().isEmpty());
        assertTrue(getExternalMap().isEmpty());
    }
}
