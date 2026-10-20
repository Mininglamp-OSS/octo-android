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

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMyInfoLayoutBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-06-29 22:28
 * 登录用户个人信息
 */
public class MyInfoActivity extends WKBaseActivity<ActMyInfoLayoutBinding> {

    //  I02a：「我的」页手机号/邮箱。phoneEmailLoaded=false 时两行整体隐藏，
    // 不沿用上一次看到的值。区号取本次响应的 zone，不取登录缓存。
    private boolean phoneEmailLoaded;
    //  仅首次加载成功前才允许失败回调隐藏两行；之后再 resume 失败不清值。
    private boolean phoneEmailEverLoaded;
    private String myPhone;
    private String myEmail;
    private String myZone;

    @Override
    protected ActMyInfoLayoutBinding getViewBinding() {
        return ActMyInfoLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.personal_info);
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        wkVBinding.idLeftTv.setText(String.format(getString(R.string.identity), getString(R.string.app_name)));
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.avatarView.setSize(32);
        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
        WKAPPConfig appConfig = WKConfig.getInstance().getAppConfig();
        if (userInfoEntity.short_status == 1 || appConfig.shortno_edit_off == 1) {
            wkVBinding.identityLayout.setEnabled(false);
            wkVBinding.identityIv.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        //  (#227)：名字行应用 displayName + ✓ 勾 + 已实名 tag
        UserInfoEntity me = WKConfig.getInstance().getUserInfo();
        wkVBinding.nameTv.setText(me.getDisplayName());
        int verifiedVis = me.realname_verified ? View.VISIBLE : View.GONE;
        wkVBinding.realnameVerifiedIv.setVisibility(verifiedVis);
        wkVBinding.realnameVerifiedTag.setVisibility(verifiedVis);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
            wkVBinding.avatarView.showAvatar(channel);
        } else
            wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);

        //  I02a：首次加载前两行保持 XML 默认 gone；再次 resume 保留当前值，
        // 在飞请求回来后覆盖，仅首次失败才隐藏。
        UserModel.getInstance().getUserInfo(WKConfig.getInstance().getUid(), "", (code, msg, userInfo) -> {
            if (isFinishing() || isDestroyed()) return;
            if (code == HttpResponseCode.success && userInfo != null) {
                myPhone = userInfo.phone;
                myEmail = userInfo.email;
                myZone = userInfo.zone;
                phoneEmailLoaded = true;
                phoneEmailEverLoaded = true;
            } else {
                // 请求失败：仅首次加载失败才隐藏两行，不展示占位文案；重进失败沿用已渲染值。
                if (!phoneEmailEverLoaded) phoneEmailLoaded = false;
            }
            renderPhoneEmail();
        });
    }

    @Override
    protected void initListener() {
        WKCommonModel.getInstance().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, (code, msg, entity) -> {
            if (entity != null && entity.extra != null) {
                Object sexObject = entity.extra.get("sex");
                if (sexObject != null) {
                    int sex = (int) sexObject;
                    wkVBinding.sexTv.setText(sex == 1 ? R.string.male : R.string.female);
                }
                Object shortNoObject = entity.extra.get("short_no");
                if (shortNoObject != null) {
                    String shortNo = (String) shortNoObject;
                    wkVBinding.identityTv.setText(shortNo);
                    // ：channel 回包里的 name 是 nickname，实名态下仍以 displayName 为准
                    wkVBinding.nameTv.setText(WKConfig.getInstance().getUserInfo().getDisplayName());
                }
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.headLayout, view -> startActivity(new Intent(MyInfoActivity.this, MyHeadPortraitActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.nameLayout, view1 -> {
            Intent intent = new Intent(this, UpdateUserInfoActivity.class);
            intent.putExtra("oldStr", wkVBinding.nameTv.getText().toString());
            intent.putExtra("updateType", 1);
            chooseResultLac.launch(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.identityLayout, view1 -> {
            if (WKConfig.getInstance().getAppConfig().shortno_edit_off == 0) {
                Intent intent = new Intent(this, UpdateUserInfoActivity.class);
                intent.putExtra("oldStr", wkVBinding.identityTv.getText().toString());
                intent.putExtra("updateType", 2);
                chooseResultLac.launch(intent);
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.qrLayout, view1 -> startActivity(new Intent(this, UserQrActivity.class)));
        wkVBinding.sexLayout.setOnClickListener(v -> {
            List<BottomSheetItem> list = new ArrayList<>();
            list.add(new BottomSheetItem(getString(R.string.male), 0, () -> updateSex(1)));
            list.add(new BottomSheetItem(getString(R.string.female), 0, () -> updateSex(0)));
            WKDialogUtils.getInstance().showBottomSheet(this,getString(R.string.sex),false,list);
        });
    }
    /**  I02a：「我的」页两行渲染。不受 profile_contact_info_on 总开关影响。 */
    private void renderPhoneEmail() {
        int vis = phoneEmailLoaded ? View.VISIBLE : View.GONE;
        wkVBinding.myPhoneLayout.setVisibility(vis);
        wkVBinding.myPhoneDividerView.setVisibility(vis);
        wkVBinding.myEmailLayout.setVisibility(vis);
        wkVBinding.myEmailDividerView.setVisibility(vis);
        if (!phoneEmailLoaded) return;

        //  I02b：判定改走策略层（RowMode.FULL_VALUE 才算「已绑定」），
        // 行可见性仍只由 phoneEmailLoaded 决定 —— null 与 "" 都显示「未绑定」，不隐行。
        boolean hasPhone = ContactInfoPolicy.phoneRowMode(myPhone, true, false)
                == ContactInfoPolicy.RowMode.FULL_VALUE;
        // 本页空值占位用「未绑定」，不是「用户暂未添加该信息」。
        wkVBinding.myPhoneTv.setText(hasPhone
                ? ContactInfoPolicy.meInfoPhoneDisplay(myPhone, myZone)
                : getString(R.string.contact_not_linked));
        if (hasPhone) {
            final String copyPhone = myPhone; // 复制原始号码，不含区号格式
            SingleClickUtil.onSingleClick(wkVBinding.myPhoneLayout, v -> copyToClipboard(copyPhone));
        } else {
            wkVBinding.myPhoneLayout.setOnClickListener(null);
        }

        boolean hasEmail =
                ContactInfoPolicy.emailRowMode(myEmail) == ContactInfoPolicy.RowMode.FULL_VALUE;
        wkVBinding.myEmailTv.setText(hasEmail ? myEmail : getString(R.string.contact_not_linked));
        if (hasEmail) {
            final String copyEmail = myEmail; // 完整字符串，视觉截断不影响复制
            SingleClickUtil.onSingleClick(wkVBinding.myEmailLayout, v -> copyToClipboard(copyEmail));
        } else {
            wkVBinding.myEmailLayout.setOnClickListener(null);
        }
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Label", text));
        com.chat.base.utils.WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed));
    }

    private void updateSex(int value){
        UserModel.getInstance().updateUserInfo("sex", String.valueOf(value), (code, msg) -> {
            if (code == HttpResponseCode.success)
                wkVBinding.sexTv.setText(value == 1 ? R.string.male : R.string.female);
            else showToast(msg);
        });
    }
    ActivityResultLauncher<Intent> chooseResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            String resultStr = result.getData().getStringExtra("result");
            int updateType = result.getData().getIntExtra("updateType", 1);
            if (updateType == 1) {
                //  (#227)：用户改的是 nickname（name），displayName 在实名态下
                // 仍应显示 realname。先把 name 落盘，再按 displayName 重绘。
                UserInfoEntity local = WKConfig.getInstance().getUserInfo();
                local.name = resultStr;
                WKConfig.getInstance().saveUserInfo(local);
                WKConfig.getInstance().setUserName(resultStr);
                wkVBinding.nameTv.setText(local.getDisplayName());
            } else if (updateType == 2) {
                wkVBinding.identityTv.setText(resultStr);
                wkVBinding.identityIv.setVisibility(View.GONE);
            }
        }
    });
}
