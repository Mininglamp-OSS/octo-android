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

package com.chat.uikit.chat.face;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.endpoint.entity.ChatFunctionMenu;
import com.chat.uikit.R;

import java.util.List;

/**
 * 2019-11-14 13:27
 * 功能模块
 */
public class FunctionAdapter extends BaseQuickAdapter<ChatFunctionMenu, BaseViewHolder> {
    int h;

    public FunctionAdapter(@Nullable List<ChatFunctionMenu> data, int h) {
        super(R.layout.item_function_layout, data);
        this.h = h;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, ChatFunctionMenu item) {
        helper.setImageResource(R.id.functionIv, item.imgResourceID);
        helper.setText(R.id.functionNameTv, item.text);
        LinearLayout contentLayout = helper.getView(R.id.contentLayout);
        contentLayout.getLayoutParams().height = h / 2;
    }
}
