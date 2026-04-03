package com.chat.scan;

import android.text.TextUtils;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.BaseObserver;
import com.chat.base.net.RetrofitUtils;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.scan.databinding.ActScanJoinGroupLayoutBinding;
import com.xinbida.wukongim.entity.WKChannelType;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;

/**
 * 扫码加群 — 原生确认页面
 */
public class ScanJoinGroupActivity extends WKBaseActivity<ActScanJoinGroupLayoutBinding> {

    private String groupNo;
    private String authCode;

    @Override
    protected ActScanJoinGroupLayoutBinding getViewBinding() {
        return ActScanJoinGroupLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.scan_join_group_info);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("group_no");
        authCode = getIntent().getStringExtra("auth_code");
        String groupName = getIntent().getStringExtra("group_name");
        String avatar = getIntent().getStringExtra("avatar");
        int memberCount = getIntent().getIntExtra("member_count", 0);

        // 群头像：用后端 avatar 或标准群头像 URL，加时间戳跳过 Glide 缓存
        AvatarView avatarView = wkVBinding.avatarView;
        avatarView.setSize(64);
        String avatarUrl;
        if (!TextUtils.isEmpty(avatar)) {
            avatarUrl = WKApiConfig.getShowUrl(avatar);
        } else {
            avatarUrl = WKApiConfig.getShowAvatar(groupNo, WKChannelType.GROUP);
        }
        String freshKey = String.valueOf(System.currentTimeMillis());
        GlideUtils.getInstance().showAvatarImg(this, avatarUrl, freshKey, avatarView.imageView);

        // 群名
        if (groupName != null && !groupName.isEmpty()) {
            wkVBinding.groupNameTv.setText(groupName);
        }

        // 成员数
        if (memberCount > 0) {
            wkVBinding.memberCountTv.setText(String.format(getString(R.string.scan_join_group_member_count), memberCount));
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.joinBtn.setOnClickListener(v -> {
            wkVBinding.joinBtn.setEnabled(false);
            ScanService service = RetrofitUtils.getInstance().createService(ScanService.class);
            service.scanJoinGroup(groupNo, authCode)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new BaseObserver<ResponseBody>() {
                        @Override
                        protected void onSuccess(ResponseBody result) {
                            WKToastUtils.getInstance().showToast(getString(R.string.scan_join_group_success));
                            ChatViewMenu chatViewMenu = new ChatViewMenu(ScanJoinGroupActivity.this, groupNo, WKChannelType.GROUP, 0, true);
                            EndpointManager.getInstance().invoke(EndpointSID.chatView, chatViewMenu);
                            finish();
                        }

                        @Override
                        protected void onFail(int code, String msg, String errJson) {
                            wkVBinding.joinBtn.setEnabled(true);
                            WKToastUtils.getInstance().showToast(msg);
                        }
                    });
        });
    }
}
