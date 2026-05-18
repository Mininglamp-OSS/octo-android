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

import android.content.Context;

import org.jetbrains.annotations.NotNull;

/**
 * 2020-11-25 18:37
 * 个人资料
 */
public class UserDetailMenu extends BaseEndpoint {
    public String uid;
    public String groupID;
    public Context context;
    public UserDetailMenu(@NotNull Context context, String uid) {
        this.uid = uid;
        this.context = context;
    }

    public UserDetailMenu(@NotNull Context context,String uid, String groupID) {
        this.uid = uid;
        this.groupID = groupID;
        this.context = context;
    }
}
