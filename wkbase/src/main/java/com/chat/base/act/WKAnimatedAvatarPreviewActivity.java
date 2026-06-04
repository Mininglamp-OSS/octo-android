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

package com.chat.base.act;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.R;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.databinding.ActAnimatedAvatarPreviewLayoutBinding;
import com.chat.base.utils.AnimatedImageUtils;
import com.chat.base.utils.WKToastUtils;

import java.io.File;

public class WKAnimatedAvatarPreviewActivity extends WKBaseActivity<ActAnimatedAvatarPreviewLayoutBinding> {

    private String path = "";

    @Override
    protected ActAnimatedAvatarPreviewLayoutBinding getViewBinding() {
        return ActAnimatedAvatarPreviewLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.animated_avatar_title);
    }

    @Override
    protected int getBackResourceID(ImageView backIv) {
        return R.mipmap.ic_ab_back;
    }

    @Override
    protected void initPresenter() {}

    @Override
    protected void initView() {
        path = getIntent().getStringExtra("path");
        if (TextUtils.isEmpty(path)) {
            finish();
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            finish();
            return;
        }

        long fileSize = file.length();
        if (fileSize > AnimatedImageUtils.MAX_ANIMATED_AVATAR_BYTES) {
            WKToastUtils.getInstance().showToast(getString(R.string.animated_avatar_too_large));
            finish();
            return;
        }

        float sizeMB = fileSize / (1024f * 1024f);
        wkVBinding.sizeTv.setText(String.format(getString(R.string.animated_avatar_size_format), sizeMB));

        Glide.with(this)
                .asGif()
                .load(file)
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(wkVBinding.previewIv);
    }

    @Override
    protected void initListener() {
        wkVBinding.reselectBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        wkVBinding.confirmBtn.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra("path", path);
            setResult(RESULT_OK, intent);
            finish();
        });
    }

    @Override
    protected boolean supportSlideBack() {
        return false;
    }
}
