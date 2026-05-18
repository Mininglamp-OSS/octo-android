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
import android.graphics.Bitmap;

import androidx.fragment.app.Fragment;

public class EditImgMenu {
    public Fragment fragment;
    public String path;
    public int requestCode;
    public IBack iBack;
    public Context context;
    public boolean isShowSaveDialog;
    public EditImgMenu(Context context, boolean isShowSaveDialog, String path, Fragment fragment, int requestCode, IBack iBack) {
        this.context = context;
        this.fragment = fragment;
        this.path = path;
        this.requestCode = requestCode;
        this.isShowSaveDialog = isShowSaveDialog;
        this.iBack = iBack;
    }

    public interface IBack {
        void onBack(Bitmap bitmap, String path);
    }
}
