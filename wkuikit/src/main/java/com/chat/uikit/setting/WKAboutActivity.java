package com.chat.uikit.setting;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActAboutLayoutBinding;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 5/26/21 3:03 PM
 * 关于
 */
public class WKAboutActivity extends WKBaseActivity<ActAboutLayoutBinding> {
    int click = 0;

    @Override
    protected ActAboutLayoutBinding getViewBinding() {
        return ActAboutLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(String.format("%s%s", getString(R.string.about), getString(R.string.app_name)));
    }

    @Override
    protected void initView() {

        SingleClickUtil.onSingleClick(wkVBinding.icpTV, view1 -> {
            // 隐私政策
            showWebView("https://beian.miit.gov.cn/#/home");
        });
        SingleClickUtil.onSingleClick(wkVBinding.privacyPolicyLayout, view1 -> {
            // 隐私政策
            showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html");
        });
        SingleClickUtil.onSingleClick(wkVBinding.userAgreementLayout, view1 -> {
            // 用户协议
            showWebView(WKApiConfig.baseWebUrl + "user_agreement.html");
        });
        SingleClickUtil.onSingleClick(wkVBinding.checkNewVersionLayout, view1 -> checkNewVersion(true));
        checkNewVersion(false);
        String v = WKDeviceUtils.getInstance().getVersionName(this);
        wkVBinding.versionTv.setText(String.format("version %s", v));
        wkVBinding.appNameTv.setText(R.string.app_name);
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarView.setSize(80);
        wkVBinding.avatarView.showAvatar(WKSystemAccount.system_team, WKChannelType.PERSONAL);
    }

    private void checkNewVersion(boolean isShowDialog) {
        WKCommonModel.getInstance().getAppNewVersion(isShowDialog, version -> {
            if (version != null && !TextUtils.isEmpty(version.download_url)) {
                if (isShowDialog) {
                    WKDialogUtils.getInstance().showNewVersionDialog(WKAboutActivity.this, version);
                } else {
                    wkVBinding.newVersionIv.setVisibility(View.VISIBLE);
                }
            } else {
                wkVBinding.newVersionIv.setVisibility(View.GONE);
            }
        });
    }


}
