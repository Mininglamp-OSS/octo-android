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

package com.chat.uikit.setting;

import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMsgNoticesSetLayoutBinding;
import com.chat.uikit.user.service.UserModel;

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
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.newMsgNoticeSwitch.setChecked(userInfoEntity.setting.new_msg_notice == 1);
        wkVBinding.voiceSwitch.setChecked(userInfoEntity.setting.voice_on == 1);
        wkVBinding.shockSwitch.setChecked(userInfoEntity.setting.shock_on == 1);
        wkVBinding.newMsgNoticeDetailSwitch.setChecked(userInfoEntity.setting.msg_show_detail == 1);
        updateChildSwitchState(userInfoEntity.setting.new_msg_notice == 1);
    }

    @Override
    protected void initListener() {
        wkVBinding.newMsgNoticeSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                userInfoEntity.setting.new_msg_notice = b ? 1 : 0;
                updateChildSwitchState(b);
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
                UserModel.getInstance().updateUserSetting("msg_show_detail", userInfoEntity.setting.msg_show_detail, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    } else showToast(msg);
                });
            }
        });
    }

    private void updateChildSwitchState(boolean masterOn) {
        float alpha = masterOn ? 1.0f : 0.4f;
        wkVBinding.detailRow.setAlpha(alpha);
        wkVBinding.voiceRow.setAlpha(alpha);
        wkVBinding.shockRow.setAlpha(alpha);
        wkVBinding.newMsgNoticeDetailSwitch.setEnabled(masterOn);
        wkVBinding.voiceSwitch.setEnabled(masterOn);
        wkVBinding.shockSwitch.setEnabled(masterOn);
    }
}
