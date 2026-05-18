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
import android.view.View;

import java.lang.ref.WeakReference;

public class SearchChatEditStickerMenu {

    public String content;
    public View view;
    public final WeakReference<Context> context;
    public IResult iResult;
    public interface IResult {
        void onResult();
    }

    public SearchChatEditStickerMenu(Context context, String content, View view, IResult iResult) {
        this.content = content;
        this.view = view;
        this.context = new WeakReference<>(context);
        this.iResult = iResult;
    }

}
