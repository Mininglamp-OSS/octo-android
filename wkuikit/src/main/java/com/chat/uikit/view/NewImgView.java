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

package com.chat.uikit.view;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.chat.base.glide.GlideUtils;
import com.chat.uikit.R;
import com.lxj.xpopup.core.AttachPopupView;

/**
 * 2020-08-01 22:48
 * 最新图片弹框
 */
public class NewImgView extends AttachPopupView {
    String path;
    Context context;
    private final IClick iClick;

    public NewImgView(@NonNull Context context, String path, IClick iClick) {
        super(context);
        this.context = context;
        this.path = path;
        this.iClick = iClick;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        ImageView imageView = findViewById(R.id.imageView);
        GlideUtils.getInstance().showImg(context, path, imageView);
        findViewById(R.id.imageLayout).setOnClickListener(view -> {
            dismiss();
            iClick.onClick(path);
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.new_img_layout;
    }

    public interface IClick {
        void onClick(String path);
    }
}
