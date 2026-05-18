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

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupMdLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.thread.service.ThreadModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.nio.charset.StandardCharsets;

public class GroupMdActivity extends WKBaseActivity<ActGroupMdLayoutBinding> {

    private static final int MAX_BYTES = 10240;
    private String groupNo;
    private String shortId;
    private String channelId;
    private boolean isThread;
    private String originalContent = "";
    private TextView titleRightTv;
    private boolean canEdit;

    @Override
    protected ActGroupMdLayoutBinding getViewBinding() {
        return ActGroupMdLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_md);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        titleRightTv = textView;
        return getString(R.string.save);
    }

    @Override
    protected boolean hideStatusBar() {
        return true;
    }

    @Override
    protected void rightLayoutClick() {
        String content = wkVBinding.contentEt.getText() != null
                ? wkVBinding.contentEt.getText().toString() : "";
        if (content.equals(originalContent)) {
            finish();
            return;
        }
        int byteLen = content.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen > MAX_BYTES) {
            showToast(getString(R.string.group_md_exceed_limit));
            return;
        }
        showTitleRightLoading();
        if (isThread) {
            ThreadModel.getInstance().updateThreadMd(groupNo, shortId, content, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    updateThreadMdExtra(!content.isEmpty());
                    finish();
                } else {
                    hideTitleRightLoading();
                    showToast(msg);
                }
            });
        } else {
            GroupModel.getInstance().updateGroupMd(groupNo, content, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    WKIM.getInstance().getChannelManager().fetchChannelInfo(groupNo, WKChannelType.GROUP);
                    finish();
                } else {
                    hideTitleRightLoading();
                    showToast(msg);
                }
            });
        }
    }

    @Override
    protected void initView() {
        wkVBinding.contentEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateByteCount(s.toString());
            }
        });
    }

    @Override
    protected void initListener() {}

    @Override
    protected void initData() {
        groupNo = getIntent().getStringExtra("groupNo");
        byte channelType = getIntent().getByteExtra("channelType", WKChannelType.GROUP);
        isThread = channelType == WKChannelType.COMMUNITY_TOPIC;

        if (isThread) {
            shortId = getIntent().getStringExtra("shortId");
            channelId = getIntent().getStringExtra("channelId");
            canEdit = getIntent().getBooleanExtra("canEdit", false);
            if (!canEdit) {
                WKChannelMember groupMember = WKIM.getInstance().getChannelMembersManager()
                        .getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
                canEdit = groupMember != null
                        && (groupMember.role == WKChannelMemberRole.admin
                        || groupMember.role == WKChannelMemberRole.manager);
            }
        } else {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager()
                    .getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
            canEdit = member != null
                    && (member.role == WKChannelMemberRole.admin
                    || member.role == WKChannelMemberRole.manager);
        }

        applyEditMode();

        if (loadingPopup != null) loadingPopup.show();
        GroupModel.IGroupMdListener mdListener = (code, msg, entity) -> {
            if (loadingPopup != null) loadingPopup.dismiss();
            if (code == HttpResponseCode.success && entity != null
                    && !TextUtils.isEmpty(entity.content)) {
                originalContent = entity.content;
                wkVBinding.contentEt.setText(entity.content);
                wkVBinding.contentEt.setSelection(entity.content.length());
                wkVBinding.readonlyHintLayout.setVisibility(View.GONE);
                wkVBinding.contentEt.setVisibility(View.VISIBLE);
                updateByteCount(entity.content);
            } else if (!canEdit) {
                wkVBinding.contentEt.setVisibility(View.GONE);
                wkVBinding.readonlyHintLayout.setVisibility(View.VISIBLE);
            }
            if (canEdit) {
                wkVBinding.contentEt.requestFocus();
                SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.contentEt);
            }
        };

        if (isThread) {
            ThreadModel.getInstance().getThreadMd(groupNo, shortId, mdListener);
        } else {
            GroupModel.getInstance().getGroupMd(groupNo, mdListener);
        }
    }

    private void applyEditMode() {
        if (canEdit) {
            if (titleRightTv != null) titleRightTv.setVisibility(View.VISIBLE);
            wkVBinding.contentEt.setEnabled(true);
            wkVBinding.contentEt.setFocusableInTouchMode(true);
            wkVBinding.byteCountTv.setVisibility(View.VISIBLE);
        } else {
            if (titleRightTv != null) titleRightTv.setVisibility(View.GONE);
            wkVBinding.contentEt.setFocusableInTouchMode(false);
            wkVBinding.contentEt.setEnabled(true);
            wkVBinding.byteCountTv.setVisibility(View.GONE);
        }
    }

    private void updateThreadMdExtra(boolean hasContent) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, WKChannelType.COMMUNITY_TOPIC);
        if (channel != null) {
            if (channel.remoteExtraMap == null) {
                channel.remoteExtraMap = new java.util.HashMap<>();
            }
            channel.remoteExtraMap.put("has_thread_md", hasContent);
            Object vObj = channel.remoteExtraMap.get("thread_md_version");
            int version = vObj instanceof Number ? ((Number) vObj).intValue() : 0;
            if (hasContent) version++;
            channel.remoteExtraMap.put("thread_md_version", version);
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
        }
    }

    private void updateByteCount(String text) {
        int byteLen = text.getBytes(StandardCharsets.UTF_8).length;
        wkVBinding.byteCountTv.setText(String.format(getString(R.string.group_md_byte_count), byteLen, MAX_BYTES));
        wkVBinding.byteCountTv.setTextColor(byteLen > MAX_BYTES
                ? ContextCompat.getColor(this, R.color.reminderColor)
                : 0xFF999999);
    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
    }
}
