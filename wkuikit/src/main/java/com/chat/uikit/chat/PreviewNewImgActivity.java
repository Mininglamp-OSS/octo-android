package com.chat.uikit.chat;

import android.Manifest;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActPreviewNewImgLayoutBinding;

/**
 * 2020-08-01 23:09
 * 预览新图片
 */
public class PreviewNewImgActivity extends WKBaseActivity<ActPreviewNewImgLayoutBinding> {
    private String path;

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
        GlideUtils.getInstance().compressImg(this, path, files -> {
            if (WKReader.isNotEmpty(files)) {
                Intent intent = new Intent();
                intent.putExtra("path", files.get(0).getAbsolutePath());
                setResult(RESULT_OK, intent);
                finish();
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
        EndpointManager.getInstance().invoke("edit_img", new EditImgMenu(this, false, path, null, -1, (bitmap, path) -> {
            Intent intent = new Intent();
            intent.putExtra("path", path);
            setResult(RESULT_OK, intent);
            finish();
        }));

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
        GlideUtils.getInstance().showImg(this, path, wkVBinding.imageView);
    }

    @Override
    protected void initListener() {

    }
}
