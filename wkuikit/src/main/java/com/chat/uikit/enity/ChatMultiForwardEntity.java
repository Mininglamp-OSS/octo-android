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

package com.chat.uikit.enity;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.xinbida.wukongim.entity.WKMsg;

/**
 * 2020-09-22 12:10
 * 合并转发
 */
public class ChatMultiForwardEntity implements MultiItemEntity {

    public int itemType;
    public String title;
    public WKMsg msg;

    @Override
    public int getItemType() {
        return itemType;
    }
}
