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

package com.chat.uikit.thread;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActThreadDetailLayoutBinding;
import com.chat.uikit.group.GroupMdActivity;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.chat.uikit.thread.service.entity.ThreadMember;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

public class ThreadDetailActivity extends WKBaseActivity<ActThreadDetailLayoutBinding> {

    private String channelId;
    private String groupNo;
    private String shortId;
    private ThreadEntity threadEntity;

    @Override
    protected ActThreadDetailLayoutBinding getViewBinding() {
        return ActThreadDetailLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.chat_info);
    }

    @Override
    protected void initView() {
        channelId = getIntent().getStringExtra("channelId");
        String[] parsed = ThreadModel.getInstance().parseChannelId(channelId);
        if (parsed != null) {
            groupNo = parsed[0];
            shortId = parsed[1];
        }
    }

    private boolean isCreator;
    private boolean isGroupAdmin;

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.threadNameLayout, v -> {
            if (!isCreator && !isGroupAdmin) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.str_rename_thread_no_permission));
                return;
            }
            showRenameDialog();
        });

        SingleClickUtil.onSingleClick(wkVBinding.threadMembersLayout, v -> {
            Intent intent = new Intent(this, ThreadMembersActivity.class);
            intent.putExtra("groupNo", groupNo);
            intent.putExtra("shortId", shortId);
            intent.putExtra("channelId", channelId);
            startActivity(intent);
        });

        wkVBinding.leaveBtn.setOnClickListener(v -> {
            if (isCreator) {
                // 创建者：关闭子区
                WKDialogUtils.getInstance().showDialog(this,
                        getString(R.string.str_close_thread),
                        getString(R.string.str_close_thread_confirm),
                        true, "", getString(R.string.sure),
                        0, ContextCompat.getColor(this, R.color.red),
                        index -> {
                            if (index == 1) {
                                ThreadModel.getInstance().deleteThread(groupNo, shortId, (code, msg) -> {
                                    if (code == HttpResponseCode.success) {
                                        // 关闭成功后清掉源消息映射, 让源消息长按菜单从"进入子区"
                                        // 切回"创建子区" (对齐 iOS WKThreadSettingVC confirmCloseThread)。
                                        if (threadEntity != null) {
                                            WKThreadCreatedContent.markThreadClosedForSourceMessageId(threadEntity.source_message_id);
                                        }
                                        EndpointManager.getInstance().invokes(EndpointCategory.wkExitChat,
                                                new WKChannel(channelId, WKChannelType.COMMUNITY_TOPIC));
                                        finish();
                                    } else {
                                        WKToastUtils.getInstance().showToast(msg);
                                    }
                                });
                            }
                        });
            } else {
                // 非创建者：离开子区
                WKDialogUtils.getInstance().showDialog(this,
                        getString(R.string.str_leave_thread),
                        getString(R.string.str_leave_thread) + "?",
                        true, "", getString(R.string.sure),
                        0, ContextCompat.getColor(this, R.color.red),
                        index -> {
                            if (index == 1) {
                                ThreadModel.getInstance().leaveThread(groupNo, shortId, (code, msg) -> {
                                    if (code == HttpResponseCode.success) {
                                        EndpointManager.getInstance().invokes(EndpointCategory.wkExitChat,
                                                new WKChannel(channelId, WKChannelType.COMMUNITY_TOPIC));
                                        finish();
                                    } else {
                                        WKToastUtils.getInstance().showToast(msg);
                                    }
                                });
                            }
                        });
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.threadMdLayout, v -> {
            Intent intent = new Intent(this, GroupMdActivity.class);
            intent.putExtra("groupNo", groupNo);
            intent.putExtra("shortId", shortId);
            intent.putExtra("channelId", channelId);
            intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC);
            intent.putExtra("canEdit", isCreator || isGroupAdmin);
            startActivity(intent);
        });

        wkVBinding.archiveBtn.setOnClickListener(v -> {
            if (threadEntity != null && threadEntity.status == 1) {
                ThreadModel.getInstance().archiveThread(groupNo, shortId, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        loadDetail();
                    } else {
                        WKToastUtils.getInstance().showToast(msg);
                    }
                });
            } else {
                ThreadModel.getInstance().unarchiveThread(groupNo, shortId, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        loadDetail();
                    } else {
                        WKToastUtils.getInstance().showToast(msg);
                    }
                });
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("thread_detail_channel", (channel, isEnd) -> {
            if (channel.channelType != WKChannelType.COMMUNITY_TOPIC) return;
            if (!channel.channelID.equals(channelId)) return;
            if (!TextUtils.isEmpty(channel.channelName)) {
                wkVBinding.threadNameTv.setText(channel.channelName);
            }
        });
        loadDetail();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("thread_detail_channel");
    }

    private void showRenameDialog() {
        String currentName = wkVBinding.threadNameTv.getText() != null
                ? wkVBinding.threadNameTv.getText().toString() : "";
        WKDialogUtils.getInstance().showInputDialog(this,
                getString(R.string.str_rename_thread),
                getString(R.string.str_rename_thread_hint),
                currentName, "", 50, text -> {
                    String trimmed = text.trim();
                    if (TextUtils.isEmpty(trimmed)) {
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.str_thread_name_empty));
                        return;
                    }
                    if (trimmed.equals(currentName)) return;
                    ThreadModel.getInstance().updateThreadName(groupNo, shortId, trimmed, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            wkVBinding.threadNameTv.setText(trimmed);
                            WKIM.getInstance().getChannelManager().fetchChannelInfo(channelId, WKChannelType.COMMUNITY_TOPIC);
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                });
    }

    private void loadDetail() {
        if (groupNo == null || shortId == null) return;
        ThreadModel.getInstance().getThreadDetail(groupNo, shortId, (code, msg, entity) -> {
            if (code == HttpResponseCode.success && entity != null) {
                threadEntity = entity;
                wkVBinding.threadNameTv.setText(entity.name);
                wkVBinding.memberCountTv.setText(String.valueOf(entity.member_count));

                String currentUid = WKConfig.getInstance().getUid();
                isCreator = currentUid.equals(entity.creator_uid);
                // 根据创建者身份设置按钮文本
                wkVBinding.leaveBtn.setText(isCreator ? R.string.str_close_thread : R.string.str_leave_thread);
                if (isCreator) {
                    wkVBinding.archiveBtn.setVisibility(View.VISIBLE);
                    if (entity.status == 1) {
                        wkVBinding.archiveBtn.setText(R.string.str_archive_thread);
                    } else {
                        wkVBinding.archiveBtn.setText(R.string.str_unarchive_thread);
                    }
                }

                WKChannelMember groupMember = WKIM.getInstance().getChannelMembersManager()
                        .getMember(groupNo, WKChannelType.GROUP, currentUid);
                isGroupAdmin = groupMember != null
                        && (groupMember.role == WKChannelMemberRole.admin
                        || groupMember.role == WKChannelMemberRole.manager);

                // 创建者名字：API 未返回时从群成员缓存查
                String displayCreatorName = entity.creator_name;
                if (TextUtils.isEmpty(displayCreatorName) && !TextUtils.isEmpty(entity.creator_uid)) {
                    WKChannelMember creatorMember = WKIM.getInstance().getChannelMembersManager()
                            .getMember(groupNo, WKChannelType.GROUP, entity.creator_uid);
                    if (creatorMember != null) {
                        displayCreatorName = !TextUtils.isEmpty(creatorMember.memberRemark)
                                ? creatorMember.memberRemark : creatorMember.memberName;
                    }
                }
                if (!TextUtils.isEmpty(displayCreatorName)) {
                    wkVBinding.creatorNameTv.setText(displayCreatorName);
                }

                // 同步 thread name 到 channelInfo
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, WKChannelType.COMMUNITY_TOPIC);
                if (channel != null && !TextUtils.isEmpty(entity.name)) {
                    channel.channelName = entity.name;
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                }

                // 通过成员列表判断当前用户是否已加入子区
                checkMembership(currentUid);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setThreadMdStatus();
    }

    private void setThreadMdStatus() {
        if (channelId == null) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, WKChannelType.COMMUNITY_TOPIC);
        if (channel != null && channel.remoteExtraMap != null) {
            Object hasObj = channel.remoteExtraMap.get("has_thread_md");
            boolean hasMd = false;
            if (hasObj instanceof Boolean) hasMd = (Boolean) hasObj;
            else if (hasObj instanceof Number) hasMd = ((Number) hasObj).intValue() == 1;
            if (hasMd) {
                int version = 0;
                Object vObj = channel.remoteExtraMap.get("thread_md_version");
                if (vObj instanceof Number) version = ((Number) vObj).intValue();
                wkVBinding.threadMdStatusTv.setText(String.format(getString(R.string.group_md_configured), version));
                return;
            }
        }
        wkVBinding.threadMdStatusTv.setText(R.string.group_md_not_configured);
    }

    private void checkMembership(String currentUid) {
        ThreadModel.getInstance().getThreadMembers(groupNo, shortId, "", 1, 100, (code, msg, members) -> {
            boolean isMember = false;
            if (code == HttpResponseCode.success && members != null) {
                for (Object item : members) {
                    if (!(item instanceof ThreadMember)) continue;
                    ThreadMember member = (ThreadMember) item;
                    if (currentUid.equals(member.uid) && member.is_deleted == 0) {
                        isMember = true;
                        break;
                    }
                }
            }
            wkVBinding.leaveBtn.setVisibility(isMember ? View.VISIBLE : View.GONE);
        });
    }

}
