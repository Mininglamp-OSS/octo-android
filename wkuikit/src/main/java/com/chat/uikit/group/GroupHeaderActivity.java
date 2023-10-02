package com.chat.uikit.group;

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
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupHeaderLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupHeaderActivity extends WKBaseActivity<ActGroupHeaderLayoutBinding> {
    private String groupNO;
    WKChannelMember member = null;

    @Override
    protected ActGroupHeaderLayoutBinding getViewBinding() {
        return ActGroupHeaderLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_avatar);
    }

    @Override
    protected void initPresenter() {
        groupNO = getIntent().getStringExtra("groupNo");
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
    protected void initView() {

    }

    @Override
    protected void initListener() {

    }

    @Override
    protected void initData() {
        super.initData();
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupNO, WKChannelType.GROUP);
        String key = "";
        if (channel != null) {
            key = channel.avatarCacheKey;
        }
        member = WKIM.getInstance().getChannelMembersManager().getMember(groupNO, WKChannelType.GROUP, WKConfig.getInstance().getUid());
        if (member == null) {
            hideTitleRightView();
        } else {
            showTitleRightView();
        }
        GlideUtils.getInstance().showAvatarImg(this, groupNO, WKChannelType.GROUP, key, wkVBinding.avatarIv);

    }

    private void showBottomDialog() {
        List<PopupMenuItem> list = new ArrayList<>();
        if (member != null && member.role != WKChannelMemberRole.normal)
            list.add(new PopupMenuItem(getString(R.string.update_avatar), R.mipmap.msg_edit, () -> {
                WKBaseApplication.getInstance().disconnect = false;
                chooseIMG();
            }));
        list.add(new PopupMenuItem(getString(R.string.save_img), R.mipmap.msg_download, () -> {
            String url = String.format("%s?key=%s", WKApiConfig.getGroupUrl(groupNO), UUID.randomUUID().toString().replaceAll("-", ""));
            ImageUtils.getInstance().downloadImg(this, url, bitmap -> {
                if (bitmap != null) {
                    ImageUtils.getInstance().saveBitmap(GroupHeaderActivity.this, bitmap, true, path -> showToast(R.string.saved_album));
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
            }, this, desc, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void success() {

        GlideUtils.getInstance().chooseIMG(GroupHeaderActivity.this, 1, true, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (paths.size() > 0) {
                    String path = paths.get(0).path;
                    if (!TextUtils.isEmpty(path)) {
                        Intent intent = new Intent(GroupHeaderActivity.this, WKCropImageActivity.class);
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
            GroupModel.getInstance().uploadAvatar(groupNO, path, code -> {
                if (code == HttpResponseCode.success) {
                    WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupNO, WKChannelType.GROUP);
                    channel.avatarCacheKey = UUID.randomUUID().toString().replace("-", "");
                    WKIM.getInstance().getChannelManager().updateAvatarCacheKey(groupNO, WKChannelType.GROUP, channel.avatarCacheKey);
                    GlideUtils.getInstance().showAvatarImg(this, channel.channelID, WKChannelType.GROUP, channel.avatarCacheKey, wkVBinding.avatarIv);
                }
            });
        }
    });
}
