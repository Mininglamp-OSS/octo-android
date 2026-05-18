/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.fragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.uikit.category.CategoryEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 群聊列表分组渲染辅助（ P1 修复）。
 *
 * <p>背景：{@code ChatFragment.filterAndDisplay()} 的群聊 tab 先前完全由
 * 服务端 {@code /spaces/{spaceId}/categories} 返回的 category.groups 驱动渲染。
 * 提交 {@code c215fa5a} 把客户端的「未归组兜底」移除后，任何未被服务端
 * categories 覆盖的群都会在 UI 上消失——典型场景是外部群：
 *
 * <ul>
 *   <li>群 G 的归属 Space 是 B（{@code group.space_id == minglue_default}）。</li>
 *   <li>我在 Space A（{@code currentSpaceId == 测试空间1}）以外部成员身份加入 G
 *       （{@code is_external == 1}，{@code source_space_id == A}）。</li>
 *   <li>{@code SpaceFilter.cached-external-member} 放行 → G 进入
 *       {@code allConversations}（可见）。</li>
 *   <li>但 {@code /spaces/A/categories} 的 category.groups 只列出
 *       {@code group.space_id == A} 的群，不包含 G，
 *       {@link com.chat.uikit.fragment.ChatFragment#filterAndDisplay()} 就把 G 漏掉。</li>
 * </ul>
 *
 * <p>该类把「哪些群没被 category 覆盖」的判定抽成纯函数，供 {@code ChatFragment}
 * 渲染时把 orphan 群并入「未分组」section，同时保证纯 JVM 单元测试可跑。
 */
public final class CategoryDisplayHelper {

    private CategoryDisplayHelper() {
    }

    /**
     * 收集 categoryList 中所有已归组的 {@code group_no}。
     *
     * <p>空值（{@code null} category / 空 groups 列表 / 空 group_no）一律跳过，
     * 保证返回的 Set 干净可直接用于 containment 判断。
     */
    @NonNull
    public static Set<String> collectCoveredGroupNos(@Nullable List<CategoryEntity> categoryList) {
        if (categoryList == null || categoryList.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> covered = new HashSet<>();
        for (CategoryEntity category : categoryList) {
            if (category == null || category.groups == null) continue;
            for (CategoryEntity.CategoryGroup cg : category.groups) {
                if (cg != null && cg.group_no != null && !cg.group_no.isEmpty()) {
                    covered.add(cg.group_no);
                }
            }
        }
        return covered;
    }

    /**
     * 找出 {@code allGroupChannelIds} 里未被 {@code categoryList} 任一 category 覆盖的 group_no。
     *
     * <p>保留插入顺序（LinkedHashSet）以便调用方拿到稳定的 orphan 顺序，
     * 避免 UI 在相同数据下每次渲染顺序不同。
     */
    @NonNull
    public static List<String> findOrphanGroupNos(
            @Nullable Collection<String> allGroupChannelIds,
            @Nullable List<CategoryEntity> categoryList) {
        if (allGroupChannelIds == null || allGroupChannelIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> covered = collectCoveredGroupNos(categoryList);
        // LinkedHashSet 去重并保序（allConversations 本身可能含同 id 的重复项）。
        Set<String> uniqueOrdered = new LinkedHashSet<>(allGroupChannelIds);
        List<String> orphans = new ArrayList<>();
        for (String cid : uniqueOrdered) {
            if (cid == null || cid.isEmpty()) continue;
            if (!covered.contains(cid)) {
                orphans.add(cid);
            }
        }
        return orphans;
    }
}
