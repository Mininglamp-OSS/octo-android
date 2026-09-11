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

package com.chat.uikit.chat;

import android.Manifest;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.act.WKCropImageActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.glide.GlideUtils;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActPreviewNewImgLayoutBinding;

/**
 * 2020-08-01 23:09
 * 预览新图片
 */
public class PreviewNewImgActivity extends WKBaseActivity<ActPreviewNewImgLayoutBinding> {
    private static final String TAG = "EditImgFlow";

    private String path;

    private final ActivityResultLauncher<Intent> cropResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 裁剪页返回 resultCode=" + result.getResultCode());
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String newPath = result.getData().getStringExtra("path");
                    WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 收到裁剪后 path=" + newPath);
                    if (!TextUtils.isEmpty(newPath)) {
                        path = newPath;
                        GlideUtils.getInstance().showImg(this, path, wkVBinding.imageView);
                        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 已用裁剪后图片刷新预览页");
                    }
                }
            });

    @Override
    protected ActPreviewNewImgLayoutBinding getViewBinding() {
        return ActPreviewNewImgLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.preview);
    }


    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.str_send);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 点击完成(发送)，path=" + path);
        GlideUtils.getInstance().compressImg(this, path, files -> {
            if (WKReader.isNotEmpty(files)) {
                WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 压缩完成，返回 path=" + files.get(0).getAbsolutePath());
                Intent intent = new Intent();
                intent.putExtra("path", files.get(0).getAbsolutePath());
                setResult(RESULT_OK, intent);
                WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: setResult(RESULT_OK) 并 finish()");
                finish();
            } else {
                WKLogUtils.e(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: compressImg 返回空文件列表，发送流程中断");
            }
        });
    }

    @Override
    protected int getRightIvLeftResourceId(ImageView imageView) {
        imageView.setColorFilter(new PorterDuffColorFilter(
                Theme.colorAccount, PorterDuff.Mode.MULTIPLY
        ));
        return R.mipmap.msg_edit;
    }

    @Override
    protected void rightLeftLayoutClick() {
        super.rightLeftLayoutClick();
        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: 点击编辑按钮，跳转裁剪页，path=" + path);
        Intent intent = new Intent(this, WKCropImageActivity.class);
        intent.putExtra("path", path);
        cropResultLauncher.launch(intent);
    }

    @Override
    protected void initPresenter() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {

                }

                @Override
                public void clickResult(boolean isCancel) {

                }
            }, this, getString(R.string.personal_info), Manifest.permission.WRITE_EXTERNAL_STORAGE);

        }

    }

    @Override
    protected void initView() {
        path = getIntent().getStringExtra("path");
        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: initView taskId=" + getTaskId() + " path=" + path);
        GlideUtils.getInstance().showImg(this, path, wkVBinding.imageView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: onResume path=" + path);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKLogUtils.d(TAG, "PreviewNewImgActivity[" + Integer.toHexString(hashCode()) + "]: onDestroy isFinishing=" + isFinishing());
    }

    @Override
    protected void initListener() {

    }
}
