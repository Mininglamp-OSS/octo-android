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

package com.chat.uikit.user;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.WKBaseApplication;
import com.chat.base.act.WKCropImageActivity;
import com.chat.base.act.WKAnimatedAvatarPreviewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.AnimatedImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMyHeadPortraitLayoutBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 2020-06-29 23:35
 * 我的头像
 */
public class MyHeadPortraitActivity extends WKBaseActivity<ActMyHeadPortraitLayoutBinding> {
    @Override
    protected ActMyHeadPortraitLayoutBinding getViewBinding() {
        return ActMyHeadPortraitLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.head_portrait);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_other;
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        showBottomDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WKBaseApplication.getInstance().disconnect = true;
    }

    @Override
    protected void initView() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        String url = WKApiConfig.getAvatarUrl(WKConfig.getInstance().getUid());

        if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
            GlideUtils.getInstance().showAvatarImg(this, channel.channelID, channel.channelType, channel.avatarCacheKey, wkVBinding.avatarIv);
        } else {
            GlideUtils.getInstance().showImg(this, url + "?width=500&height=500", wkVBinding.avatarIv);
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarIv.setOnLongClickListener(view1 -> {
            showBottomDialog();
            return true;
        });
    }

    private void showBottomDialog() {
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.update_avatar), R.mipmap.msg_edit, () -> {
            WKBaseApplication.getInstance().disconnect = false;
            chooseIMG();
        }));

        list.add(new PopupMenuItem(getString(R.string.save_img), R.mipmap.msg_download, () -> {
            String avatarURL = WKApiConfig.getAvatarUrl(WKConfig.getInstance().getUid());
            avatarURL = avatarURL + "?key=" + UUID.randomUUID().toString().replaceAll("-","");
            ImageUtils.getInstance().downloadImg(this, avatarURL, bitmap -> {
                if (bitmap != null) {
                    ImageUtils.getInstance().saveBitmap(MyHeadPortraitActivity.this, bitmap, true, path -> showToast(R.string.saved_album));
                }
            });
        }));
        ImageView rightIV = findViewById(R.id.titleRightIv);
        WKDialogUtils.getInstance().showScreenPopup(rightIV, list);
    }

    private void chooseIMG() {
        String desc = String.format(getString(R.string.file_permissions_des), getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        success();
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA);
        } else {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        success();
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE);
        }


    }

    private void success() {

        GlideUtils.getInstance().chooseIMG(MyHeadPortraitActivity.this, 1, true, ChooseMimeType.img, false,false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    if (isFinishing() || isDestroyed()) return;
                    String path = paths.get(0).path;
                    if (!TextUtils.isEmpty(path)) {
                        Intent intent;
                        if (AnimatedImageUtils.isAnimatedGif(path)) {
                            intent = new Intent(MyHeadPortraitActivity.this, WKAnimatedAvatarPreviewActivity.class);
                        } else {
                            intent = new Intent(MyHeadPortraitActivity.this, WKCropImageActivity.class);
                        }
                        intent.putExtra("path", path);
                        chooseResultLac.launch(intent);
                    }
                }
            }

            @Override
            public void onCancel() {

            }
        });

    }

    ActivityResultLauncher<Intent> chooseResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            String path = result.getData().getStringExtra("path");
            UserModel.getInstance().uploadAvatar(path, code -> {
                if (code == HttpResponseCode.success) {
                    WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
                    if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                        channel = new WKChannel();
                        channel.channelType = WKChannelType.PERSONAL;
                        channel.channelID = WKConfig.getInstance().getUid();
                        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                    }
                    channel.avatarCacheKey = UUID.randomUUID().toString().replace("-", "");
                    WKIM.getInstance().getChannelManager().updateAvatarCacheKey(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, channel.avatarCacheKey);
                    GlideUtils.getInstance().showAvatarImg(this, channel.channelID, WKChannelType.PERSONAL, channel.avatarCacheKey, wkVBinding.avatarIv);
                    String avatarURL = WKApiConfig.getAvatarUrl(WKConfig.getInstance().getUid());
                    avatarURL = avatarURL + "?key=" + channel.avatarCacheKey;
                    EndpointManager.getInstance().invoke("updateRtcAvatarUrl", avatarURL);
                }
            });
        }
    });
}
