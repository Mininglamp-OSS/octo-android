package com.chat.uikit.thread;

import android.content.Intent;
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
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.chat.uikit.thread.service.entity.ThreadMember;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
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

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.threadMembersLayout, v -> {
            Intent intent = new Intent(this, ThreadMembersActivity.class);
            intent.putExtra("groupNo", groupNo);
            intent.putExtra("shortId", shortId);
            intent.putExtra("channelId", channelId);
            startActivity(intent);
        });

        wkVBinding.leaveBtn.setOnClickListener(v ->
                WKDialogUtils.getInstance().showDialog(this,
                        getString(R.string.str_leave_thread),
                        getString(R.string.str_leave_thread) + "?",
                        true, "", getString(R.string.sure),
                        0, ContextCompat.getColor(this, R.color.red),
                        index -> {
                            if (index == 1) {
                                ThreadModel.getInstance().leaveThread(groupNo, shortId, (code, msg) -> {
                                    if (code == HttpResponseCode.success) {
                                        // 通知子区聊天页也关闭
                                        EndpointManager.getInstance().invokes(EndpointCategory.wkExitChat,
                                                new WKChannel(channelId, WKChannelType.COMMUNITY_TOPIC));
                                        finish();
                                    } else {
                                        WKToastUtils.getInstance().showToast(msg);
                                    }
                                });
                            }
                        }));

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
        loadDetail();
    }

    private void loadDetail() {
        if (groupNo == null || shortId == null) return;
        ThreadModel.getInstance().getThreadDetail(groupNo, shortId, (code, msg, entity) -> {
            if (code == HttpResponseCode.success && entity != null) {
                threadEntity = entity;
                wkVBinding.threadNameTv.setText(entity.name);
                wkVBinding.memberCountTv.setText(String.valueOf(entity.member_count));
                wkVBinding.creatorNameTv.setText(entity.creator_name);

                String currentUid = WKConfig.getInstance().getUid();
                boolean isCreator = currentUid.equals(entity.creator_uid);
                if (isCreator) {
                    wkVBinding.archiveBtn.setVisibility(View.VISIBLE);
                    if (entity.status == 1) {
                        wkVBinding.archiveBtn.setText(R.string.str_archive_thread);
                    } else {
                        wkVBinding.archiveBtn.setText(R.string.str_unarchive_thread);
                    }
                }

                // 通过成员列表判断当前用户是否已加入子区
                checkMembership(currentUid);
            }
        });
    }

    private void checkMembership(String currentUid) {
        ThreadModel.getInstance().getThreadMembers(groupNo, shortId, "", 1, 100, (code, msg, members) -> {
            boolean isMember = false;
            if (code == HttpResponseCode.success && members != null) {
                for (ThreadMember member : members) {
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
