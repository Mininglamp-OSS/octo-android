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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.R;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.databinding.ActCutImgLayoutBinding;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKLogUtils;
import com.luck.picture.lib.config.CustomIntentKey;

import java.io.File;

/**
 * 2020-11-27 18:14
 * 剪切图片
 */
public class WKCropImageActivity extends WKBaseActivity<ActCutImgLayoutBinding> {

    private static final String TAG = "EditImgFlow";

    private int rotate = 0;

    @Override
    protected ActCutImgLayoutBinding getViewBinding() {
        return ActCutImgLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.crop);
    }


    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_done;
    }

    @Override
    protected int getRightIvLeftResourceId(ImageView imageView) {
        return R.mipmap.bg_rotate_large;
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: 点击完成，开始裁剪，调用栈: " + android.util.Log.getStackTraceString(new Throwable()));
        Bitmap bitmap = wkVBinding.cropImageView.getCroppedImage();
        if (bitmap == null) {
            WKLogUtils.e(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: getCroppedImage() 返回 null，裁剪失败");
            return;
        }
        WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: 裁剪产物尺寸 " + bitmap.getWidth() + "x" + bitmap.getHeight());
        ImageUtils.getInstance().saveBitmap(this, bitmap, false, path -> {
            File savedFile = new File(path);
            WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: 裁剪结果已落盘 path=" + path + " exists=" + savedFile.exists() + " size=" + savedFile.length());
            Intent intent = new Intent();
            intent.putExtra("path", path);
            Uri outputUri = Uri.fromFile(savedFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
            intent.putExtra(CustomIntentKey.EXTRA_OUTPUT_URI, outputUri);
            setResult(RESULT_OK, intent);
            WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: setResult(RESULT_OK) 并 finish()");
            finish();
        });
    }

    @Override
    protected void rightLeftLayoutClick() {
        super.rightLeftLayoutClick();
        rotate = rotate + 90;
        WKLogUtils.d(TAG, "WKCropImageActivity: 点击旋转，rotate=" + rotate);
        wkVBinding.cropImageView.rotateImage(rotate);
    }

    @Override
    protected void initView() {
        String path = getIntent().getStringExtra("path");
        WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: initView taskId=" + getTaskId()
                + " path=" + path + " callingActivity=" + getCallingActivity()
                + " callingPackage=" + getCallingPackage()
                + " intentExtras=" + getIntent().getExtras());
        if (!TextUtils.isEmpty(path)) {
            Bitmap bitmap = BitmapFactory.decodeFile(new File(path).getAbsolutePath());
            wkVBinding.cropImageView.setImageBitmap(bitmap);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: onResume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKLogUtils.d(TAG, "WKCropImageActivity[" + Integer.toHexString(hashCode()) + "]: onDestroy isFinishing=" + isFinishing());
    }
}
