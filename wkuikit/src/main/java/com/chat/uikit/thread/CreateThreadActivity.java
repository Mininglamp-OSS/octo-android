package com.chat.uikit.thread;

import android.content.Intent;
import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;

import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.databinding.ActCreateThreadLayoutBinding;
import com.chat.uikit.thread.service.ThreadModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

public class CreateThreadActivity extends WKBaseActivity<ActCreateThreadLayoutBinding> {


    private String groupNo;
    private String sourceMessageId;
    private String sourceContent;
    private String sourceFromName;
    private String sourceFromUid;

    @Override
    protected ActCreateThreadLayoutBinding getViewBinding() {
        return ActCreateThreadLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_new_thread);
    }

    @Override
    protected void setSubtitle(TextView subtitleTv) {
        if (TextUtils.isEmpty(groupNo)) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
        if (channel != null && !TextUtils.isEmpty(channel.channelName)) {
            subtitleTv.setVisibility(View.VISIBLE);
            subtitleTv.setText(channel.channelName);
        }
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("groupNo");
        sourceMessageId = getIntent().getStringExtra("sourceMessageId");
        sourceContent = getIntent().getStringExtra("sourceContent");
        sourceFromName = getIntent().getStringExtra("sourceFromName");
        sourceFromUid = getIntent().getStringExtra("sourceFromUid");

        if (!TextUtils.isEmpty(sourceMessageId) && !TextUtils.isEmpty(sourceContent)) {
            wkVBinding.divider.setVisibility(View.VISIBLE);
            wkVBinding.sourceMessageLayout.setVisibility(View.VISIBLE);
            wkVBinding.sourceContentTv.setText(sourceContent);
            if (!TextUtils.isEmpty(sourceFromName)) {
                wkVBinding.sourceNameTv.setText(sourceFromName);
            }
            if (!TextUtils.isEmpty(sourceFromUid)) {
                wkVBinding.sourceAvatarView.showAvatar(sourceFromUid, WKChannelType.PERSONAL);
            }
            wkVBinding.threadNameEt.setHint(sourceContent.length() > 30
                    ? sourceContent.substring(0, 30) + "..."
                    : sourceContent);
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.createBtn.setOnClickListener(v -> createThread());
    }

    @Override
    protected void initData() {
        super.initData();
    }

    private boolean keyboardShown = false;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !keyboardShown) {
            keyboardShown = true;
            wkVBinding.threadNameEt.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(wkVBinding.threadNameEt, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
            }
        }
    }

    private void createThread() {
        String name = wkVBinding.threadNameEt.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            if (!TextUtils.isEmpty(sourceContent)) {
                name = sourceContent.length() > 50
                        ? sourceContent.substring(0, 50)
                        : sourceContent;
            } else {
                name = getString(R.string.str_new_thread);
            }
        }

        // 构建源消息 payload，与 iOS 一致
        JSONObject sourcePayload = null;
        if (!TextUtils.isEmpty(sourceMessageId) && !TextUtils.isEmpty(sourceContent)) {
            sourcePayload = new JSONObject();
            sourcePayload.put("type", 1);
            sourcePayload.put("content", sourceContent);
            if (!TextUtils.isEmpty(sourceFromUid)) {
                sourcePayload.put("from_uid", sourceFromUid);
            }
            if (!TextUtils.isEmpty(sourceFromName)) {
                sourcePayload.put("from_name", sourceFromName);
            }
        }

        wkVBinding.createBtn.setEnabled(false);
        String finalName = name;
        ThreadModel.getInstance().createThread(groupNo, finalName, sourceMessageId, sourcePayload, (code, msg, entity) -> {
            wkVBinding.createBtn.setEnabled(true);
            if (code == HttpResponseCode.success && entity != null) {
                // 创建者自动加入子区成员，与 iOS 一致
                ThreadModel.getInstance().joinThread(groupNo, entity.short_id, (c, m) -> {});
                String channelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("channelId", channelId);
                intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC);
                // YUJ-298 · Fix A：走 ChatReuseNavigator，窄屏下若栈中已有
                // ChatActivity 会 reorder 到栈顶走 onNewIntent 热路径。
                com.chat.uikit.chat.ChatReuseNavigator.launchChat(this, intent, this);
                finish();
            } else {
                WKToastUtils.getInstance().showToast(msg);
            }
        });
    }
}
