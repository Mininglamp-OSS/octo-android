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

package com.chat.uikit.group;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupQrLayoutBinding;
import com.chat.uikit.group.service.GroupContract;
import com.chat.uikit.group.service.GroupPresenter;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-04-06 22:30
 * 群聊二维码名片
 */
public class GroupQrActivity extends WKBaseActivity<ActGroupQrLayoutBinding> implements GroupContract.GroupView {

    private GroupPresenter presenter;
    String groupId;

    @Override
    protected ActGroupQrLayoutBinding getViewBinding() {
        return ActGroupQrLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_qr);
    }

    @Override
    protected void initPresenter() {
        presenter = new GroupPresenter(this);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_other;
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.save_img), R.mipmap.msg_download, () -> {
            Bitmap bitmap = ImageUtils.getInstance().loadBitmapFromView(wkVBinding.shadowLayout);
            ImageUtils.getInstance().saveBitmap(this, bitmap, true, path -> showToast(R.string.saved_album));
        }));
        ImageView rightIV = findViewById(R.id.titleRightIv);
        WKDialogUtils.getInstance().showScreenPopup(rightIV,  list);
    }

    @Override
    protected void initView() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.avatarView.setSize(45);
    }

    @Override
    protected void initListener() {
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("group_qr_channel_refresh", (channel, isEnd) -> getGroupInfo());
    }

    @Override
    protected void initData() {
        super.initData();
        groupId = getIntent().getStringExtra("groupId");
        getGroupInfo();
    }

    private void getGroupInfo() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
        if (channel != null) {
            wkVBinding.nameTv.setText(channel.channelName);
            wkVBinding.avatarView.showAvatar(groupId, channel.channelType, channel.avatarCacheKey);
            if (channel.invite == 1) {
                wkVBinding.qrTv.setVisibility(View.INVISIBLE);
                wkVBinding.unusedTv.setVisibility(View.VISIBLE);
                wkVBinding.qrIv.setImageResource(R.mipmap.icon_no_qr);
            } else {
                wkVBinding.unusedTv.setVisibility(View.INVISIBLE);
                wkVBinding.qrTv.setVisibility(View.VISIBLE);
                presenter.getQrData(groupId);
            }
        } else {
            presenter.getQrData(groupId);
        }
    }

    @Override
    public void onGroupInfo(ChannelInfoEntity groupEntity) {

    }

    @Override
    public void onRefreshGroupSetting(String key, int value) {

    }

    @Override
    public void setQrData(int day, String qrcode, String expire, String inviteUrl) {
        if (TextUtils.isEmpty(qrcode)) {
            wkVBinding.qrIv.setImageResource(R.mipmap.icon_no_qr);
        } else {
            Bitmap mBitmap = (Bitmap) EndpointManager.getInstance().invoke("create_qrcode", qrcode);
//            Bitmap mBitmap = CodeUtils.createQRCode(qrcode, 400, null);
            wkVBinding.qrIv.setImageBitmap(mBitmap);
            String content = String.format(getString(R.string.group_qr_desc), day, expire);
            wkVBinding.qrTv.setText(content);
        }
        // 外部群·EP8：仅当后端返回 invite_url 时展示「复制邀请链接」按钮，
        // 复制内容是 invite_url（不是 qrcode 图片 URL）。
        if (!TextUtils.isEmpty(inviteUrl)) {
            wkVBinding.copyInviteLinkBtn.setVisibility(View.VISIBLE);
            wkVBinding.copyInviteLinkBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setPrimaryClip(ClipData.newPlainText("invite_url", inviteUrl));
                WKToastUtils.getInstance().showToastNormal(getString(R.string.group_invite_link_copied));
            });
        } else {
            wkVBinding.copyInviteLinkBtn.setVisibility(View.GONE);
            wkVBinding.copyInviteLinkBtn.setOnClickListener(null);
        }
    }

    @Override
    public void setMyGroups(List<GroupEntity> list) {

    }

    @Override
    public void showError(String msg) {

    }

    @Override
    public void hideLoading() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("group_qr_channel_refresh");
    }
}
