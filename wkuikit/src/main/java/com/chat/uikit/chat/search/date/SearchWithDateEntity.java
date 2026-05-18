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

package com.chat.uikit.chat.search.date;

import com.chad.library.adapter.base.entity.MultiItemEntity;

import java.util.List;

/**
 * 3/23/21 6:02 PM
 * 通过日期搜索聊天记录
 */
public class SearchWithDateEntity implements MultiItemEntity {
    public int itemType;
    public String day;
    public boolean selected;
    public boolean isToDay;
    public String date;
    public long dayCount;
    public long orderSeq;
    public boolean isNull;
    public List<SearchWithDateEntity> list;

    @Override
    public int getItemType() {
        return itemType;
    }

    public SearchWithDateEntity(int itemType) {
        this.itemType = itemType;
    }

}
