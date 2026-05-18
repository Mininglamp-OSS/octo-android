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

package com.chat.base.endpoint.entity;

/**
 * 1/4/21 3:17 PM
 * 表情菜单分类刷新
 */
public class StickerCategoryRefreshMenu {
    public IRefreshCategory iRefreshCategory;

    public StickerCategoryRefreshMenu(IRefreshCategory iRefreshCategory) {
        this.iRefreshCategory = iRefreshCategory;
    }

    public interface IRefreshCategory {
        //刷新某项
        void onRefresh(String category, boolean isAdd);

        //重置数据
        void onReset();
    }
}
