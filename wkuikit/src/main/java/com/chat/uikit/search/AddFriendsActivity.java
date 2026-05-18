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

package com.chat.uikit.search;

import android.content.Intent;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.ui.Theme;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActAddFriendsLayoutBinding;
import com.chat.uikit.user.UserQrActivity;

/**
 * 2020-07-06 10:14
 * 添加好友
 */
public class AddFriendsActivity extends WKBaseActivity<ActAddFriendsLayoutBinding> {
    @Override
    protected ActAddFriendsLayoutBinding getViewBinding() {
        return ActAddFriendsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.add_friends);
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        Theme.setPressedBackground(wkVBinding.qrIv);
        wkVBinding.searchTitleTv.setText(String.format(getString(R.string.my_app_id), getString(R.string.app_name)));
        wkVBinding.identityTv.setText(WKConfig.getInstance().getUserInfo().short_no);
    }

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.qrIv, v -> startActivity(new Intent(this, UserQrActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.searchLayout, v -> startActivity(new Intent(this, SearchUserActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.scanLayout, v -> EndpointManager.getInstance().invoke("wk_scan_show", null));
        SingleClickUtil.onSingleClick(wkVBinding.mailListLayout, v -> startActivity(new Intent(this, MailListActivity.class)));
    }

}
