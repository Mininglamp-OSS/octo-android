package com.chat.uikit.fragment;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertTrue;

import com.chat.uikit.category.CategoryEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * YUJ-183 · {@link CategoryDisplayHelper} host-side 单元测试。
 *
 * <p>复现 bug：外部群 G（{@code group.space_id == minglue_default}）通过 SpaceFilter
 * 进入 {@code allConversations}，但服务端 {@code /spaces/{spaceId}/categories}
 * 的任何 {@code category.groups} 里都找不到 G——客户端必须把它识别为 orphan，
 * 并由 {@code ChatFragment.filterAndDisplay()} 兜底展示到「未分组」section。
 *
 * <p>测试策略：只验证纯函数的 orphan 识别 / 去重 / 稳定顺序 / 空值兜底。
 * UI 渲染路径由 {@code ChatFragment} 承担，这里不涉及。
 */
public class CategoryDisplayHelperTest {

    // 场景 1/覆盖矩阵核心：外部群 orphan 场景
    @Test
    public void findOrphan_externalGroupMissingFromCategories_isIdentifiedAsOrphan() {
        CategoryEntity cat = category("cat1", "工作", false, "gInternalA", "gInternalB");
        List<String> allGroups = Arrays.asList("gInternalA", "gInternalB", "gExternal");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Collections.singletonList(cat));
        assertEquals(1, orphans.size());
        assertEquals("gExternal", orphans.get(0));
    }

    @Test
    public void findOrphan_multipleExternalGroups_allIdentifiedInInsertionOrder() {
        CategoryEntity cat = category("cat1", "工作", false, "gInternalA");
        List<String> allGroups = Arrays.asList("gInternalA", "gExt1", "gExt2", "gExt3");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Collections.singletonList(cat));
        // 保持 allGroups 的插入顺序，避免 UI 每次刷新排序抖动
        assertEquals(Arrays.asList("gExt1", "gExt2", "gExt3"), orphans);
    }

    @Test
    public void findOrphan_groupInDefaultCategory_isNotOrphan() {
        CategoryEntity userCat = category("cat1", "工作", false, "gWork");
        CategoryEntity defaultCat = category(null, "未分组", true, "gInternal");
        List<String> allGroups = Arrays.asList("gWork", "gInternal", "gExternal");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Arrays.asList(userCat, defaultCat));
        assertEquals(Collections.singletonList("gExternal"), orphans);
    }

    @Test
    public void findOrphan_allGroupsCovered_returnsEmpty() {
        CategoryEntity cat = category("cat1", "工作", false, "g1", "g2", "g3");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(
                Arrays.asList("g1", "g2", "g3"), Collections.singletonList(cat));
        assertTrue(orphans.isEmpty());
    }

    @Test
    public void findOrphan_emptyCategoryList_returnsAllAsOrphans() {
        // 场景：服务端 categories 接口返回空（冷启动 / 失败兜底）。
        // 客户端仍须把 allConversations 里所有群兜底渲染，避免整页空白。
        List<String> allGroups = Arrays.asList("g1", "g2");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups, Collections.emptyList());
        assertEquals(allGroups, orphans);
    }

    @Test
    public void findOrphan_nullCategoryList_returnsAllAsOrphans() {
        List<String> allGroups = Arrays.asList("g1");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups, null);
        assertEquals(allGroups, orphans);
    }

    @Test
    public void findOrphan_emptyAllGroups_returnsEmpty() {
        CategoryEntity cat = category("cat1", "x", false, "g1");
        assertTrue(CategoryDisplayHelper.findOrphanGroupNos(
                Collections.emptyList(), Collections.singletonList(cat)).isEmpty());
        assertTrue(CategoryDisplayHelper.findOrphanGroupNos(
                null, Collections.singletonList(cat)).isEmpty());
    }

    @Test
    public void findOrphan_duplicateAllGroupIds_returnsDeduped() {
        CategoryEntity cat = category("cat1", "x", false, "gKnown");
        // allConversations 理论上应唯一，但给一层保险（duplicate → 单次）。
        List<String> allGroups = Arrays.asList("gExt1", "gExt1", "gKnown", "gExt2");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Collections.singletonList(cat));
        assertEquals(Arrays.asList("gExt1", "gExt2"), orphans);
    }

    @Test
    public void findOrphan_categoryWithNullGroupNo_skippedSafely() {
        CategoryEntity cat = new CategoryEntity();
        cat.category_id = "cat1";
        cat.groups = new ArrayList<>();
        CategoryEntity.CategoryGroup badCg = new CategoryEntity.CategoryGroup();
        badCg.group_no = null; // 防御空值
        cat.groups.add(badCg);
        CategoryEntity.CategoryGroup emptyCg = new CategoryEntity.CategoryGroup();
        emptyCg.group_no = ""; // 防御空串
        cat.groups.add(emptyCg);
        CategoryEntity.CategoryGroup goodCg = new CategoryEntity.CategoryGroup();
        goodCg.group_no = "gKnown";
        cat.groups.add(goodCg);

        List<String> allGroups = Arrays.asList("gKnown", "gExternal");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Collections.singletonList(cat));
        assertEquals(Collections.singletonList("gExternal"), orphans);
    }

    @Test
    public void findOrphan_allGroupsWithNullOrEmpty_skippedSafely() {
        CategoryEntity cat = category("cat1", "x", false, "gKnown");
        List<String> allGroups = Arrays.asList("gKnown", null, "", "gExt");
        List<String> orphans = CategoryDisplayHelper.findOrphanGroupNos(allGroups,
                Collections.singletonList(cat));
        assertEquals(Collections.singletonList("gExt"), orphans);
    }

    // ------------------------------------------------------------------
    // collectCoveredGroupNos
    // ------------------------------------------------------------------

    @Test
    public void collectCovered_unionsAcrossCategories() {
        CategoryEntity a = category("catA", "A", false, "g1", "g2");
        CategoryEntity b = category("catB", "B", false, "g2", "g3");
        CategoryEntity def = category(null, "默认", true, "g4");
        Set<String> covered = CategoryDisplayHelper.collectCoveredGroupNos(Arrays.asList(a, b, def));
        assertEquals(4, covered.size());
        assertTrue(covered.contains("g1"));
        assertTrue(covered.contains("g2"));
        assertTrue(covered.contains("g3"));
        assertTrue(covered.contains("g4"));
    }

    @Test
    public void collectCovered_nullOrEmpty_returnsEmptySet() {
        assertTrue(CategoryDisplayHelper.collectCoveredGroupNos(null).isEmpty());
        assertTrue(CategoryDisplayHelper.collectCoveredGroupNos(Collections.emptyList()).isEmpty());
    }

    @Test
    public void collectCovered_skipsNullCategoryAndNullGroups() {
        CategoryEntity withNullGroups = new CategoryEntity();
        withNullGroups.category_id = "cat1";
        withNullGroups.groups = null;
        Set<String> covered = CategoryDisplayHelper.collectCoveredGroupNos(
                Arrays.asList((CategoryEntity) null, withNullGroups));
        assertTrue(covered.isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static CategoryEntity category(String id, String name, boolean isDefault, String... groupNos) {
        CategoryEntity ce = new CategoryEntity();
        ce.category_id = id;
        ce.name = name;
        ce.is_default = isDefault;
        ce.groups = new ArrayList<>();
        for (String no : groupNos) {
            CategoryEntity.CategoryGroup cg = new CategoryEntity.CategoryGroup();
            cg.group_no = no;
            ce.groups.add(cg);
        }
        return ce;
    }
}
