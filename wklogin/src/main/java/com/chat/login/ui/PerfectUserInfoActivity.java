package com.chat.login.ui;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKReader;
import com.chat.login.R;
import com.chat.login.databinding.ActPerfectUserInfoLayoutBinding;
import com.chat.login.service.LoginModel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;
import java.util.Objects;

/**
 * 2020-08-28 13:43
 * 完善个人资料
 */
public class PerfectUserInfoActivity extends WKBaseActivity<ActPerfectUserInfoLayoutBinding> {

    String path;

    @Override
    protected ActPerfectUserInfoLayoutBinding getViewBinding() {
        return ActPerfectUserInfoLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.wklogin_perfect_userinfo);
    }

    @Override
    protected void initView() {
        wkVBinding.avatarView.setSize(120);
        wkVBinding.avatarView.setStrokeWidth(0);
        wkVBinding.avatarView.imageView.setImageResource(R.mipmap.icon_default_header);
    }

    @Override
    protected void initListener() {
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.avatarView.setOnClickListener(v -> chooseIMG());
        wkVBinding.sureBtn.setOnClickListener(v -> {

            if (TextUtils.isEmpty(path)) {
                showToast(R.string.wklogin_must_upload_header);
                return;
            }
            if (!checkEditInputIsEmpty(wkVBinding.nameEt, R.string.nickname_not_null)) {
                loadingPopup.show();
                LoginModel.getInstance().updateUserInfo("name", Objects.requireNonNull(wkVBinding.nameEt.getText()).toString(), (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
                        userInfoEntity.name = wkVBinding.nameEt.getText().toString();
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                        WKConfig.getInstance().setUserName(wkVBinding.nameEt.getText().toString());
                        List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
                        if (WKReader.isNotEmpty(list)) {
                            for (LoginMenu menu : list) {
                                if (menu.iMenuClick != null)
                                    menu.iMenuClick.onClick();
                            }
                        }
                        loadingPopup.dismiss();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }

        });
    }

    private void chooseIMG() {
        GlideUtils.getInstance().chooseIMG(this, 1, true, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    path = paths.get(0).path;
                    LoginModel.getInstance().uploadAvatar(path, code -> {
                        if (code == HttpResponseCode.success) {
                            GlideUtils.getInstance().showAvatarImg(PerfectUserInfoActivity.this, WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, "", wkVBinding.avatarView.imageView);
                            wkVBinding.coverIv.setVisibility(View.GONE);
                        }
                    });

                }
            }

            @Override
            public void onCancel() {

            }
        });
    }
}
