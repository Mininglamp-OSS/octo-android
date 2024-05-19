package com.chat.uikit.setting;

import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatBgItemMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.systembar.WKOSUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMsgNoticesSetLayoutBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2020-06-30 13:31
 * 新消息通知设置
 */
public class MsgNoticesSettingActivity extends WKBaseActivity<ActMsgNoticesSetLayoutBinding> {
    UserInfoEntity userInfoEntity;

    @Override
    protected ActMsgNoticesSetLayoutBinding getViewBinding() {
        return ActMsgNoticesSetLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.new_msg_notice);
    }

    @Override
    protected void initPresenter() {
        userInfoEntity = WKConfig.getInstance().getUserInfo();
    }

    @Override
    protected void initView() {
        wkVBinding.voiceShockDescTv.setText(String.format(getString(R.string.voice_shock_desc), getString(R.string.app_name)));
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.newMsgNoticeSwitch.setChecked(userInfoEntity.setting.new_msg_notice == 1);
        wkVBinding.voiceSwitch.setChecked(userInfoEntity.setting.voice_on == 1);
        wkVBinding.shockSwitch.setChecked(userInfoEntity.setting.shock_on == 1);
        wkVBinding.newMsgNoticeDetailSwitch.setChecked(userInfoEntity.setting.msg_show_detail == 1);
        View keepAliveView = (View) EndpointManager.getInstance().invoke("show_keep_alive_item", this);
        if (keepAliveView != null) {
            wkVBinding.keepAliveLayout.addView(keepAliveView);
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.newMsgNoticeSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                userInfoEntity.setting.new_msg_notice = b ? 1 : 0;
                UserModel.getInstance().updateUserSetting("new_msg_notice", userInfoEntity.setting.new_msg_notice, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    } else showToast(msg);
                });
            }
        });
        wkVBinding.voiceSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                userInfoEntity.setting.voice_on = b ? 1 : 0;
                UserModel.getInstance().updateUserSetting("voice_on", userInfoEntity.setting.voice_on, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    } else showToast(msg);
                });
            }
        });
        wkVBinding.shockSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                userInfoEntity.setting.shock_on = b ? 1 : 0;
                UserModel.getInstance().updateUserSetting("shock_on", userInfoEntity.setting.shock_on, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    } else showToast(msg);
                });
            }
        });
        wkVBinding.newMsgNoticeDetailSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                userInfoEntity.setting.msg_show_detail = b ? 1 : 0;
                UserModel.getInstance().updateUserSetting("msg_show_detail", userInfoEntity.setting.search_by_phone, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    } else showToast(msg);
                });
            }
        });
        wkVBinding.openNoticeLayout.setOnClickListener(v -> {
            WKOSUtils.openChannelSetting(this, WKConstants.newMsgChannelID);
        });
        wkVBinding.openRTCNoticeLayout.setOnClickListener(v -> {
            WKOSUtils.openChannelSetting(this, WKConstants.newRTCChannelID);
        });
    }
}
