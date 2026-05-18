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

public class MailListEntity implements MultiItemEntity {
    public String name;
    public String uid;
    public String phone;
    public String zone;
    public String vercode;
    public int is_friend;
    public String pying;
    public int itemType = 0;

    @Override
    public int getItemType() {
        return itemType;
    }
}
