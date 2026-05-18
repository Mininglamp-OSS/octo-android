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
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

public class UserDetailViewMenu {
    public WeakReference<Context> context;
    public String uid;
    public String groupNo;
    public ViewGroup parentView;

    public UserDetailViewMenu(Context context, ViewGroup parentView, String uid, String groupNo) {
        this.context = new WeakReference<>(context);
        this.groupNo = groupNo;
        this.uid = uid;
        this.parentView = parentView;
    }
}
