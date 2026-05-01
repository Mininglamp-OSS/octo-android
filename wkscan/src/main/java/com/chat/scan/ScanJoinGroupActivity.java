package com.chat.scan;

import android.text.TextUtils;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.BaseObserver;
import com.chat.base.net.RetrofitUtils;
import com.chat.base.space.JoinSuccessHelper;
import com.chat.base.space.ScanJoinEffectiveResolver;
import com.chat.base.space.SpaceFilter;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.scan.databinding.ActScanJoinGroupLayoutBinding;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.IOException;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;

/**
 * 扫码加群 — 原生确认页面
 */
public class ScanJoinGroupActivity extends WKBaseActivity<ActScanJoinGroupLayoutBinding> {

    private String groupNo;
    private String authCode;
    private boolean isMember;
    private String groupName;
    private String targetSpaceId;
    private String targetSpaceName;

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
        isMember = getIntent().getBooleanExtra("is_member", false);
        groupName = getIntent().getStringExtra("group_name");
        String avatar = getIntent().getStringExtra("avatar");
        int memberCount = getIntent().getIntExtra("member_count", 0);
        targetSpaceId = getIntent().getStringExtra("space_id");
        targetSpaceName = getIntent().getStringExtra("space_name");

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

        // 根据是否已是群成员切换按钮文案
        if (isMember) {
            wkVBinding.joinBtn.setText(R.string.scan_enter_group);
        } else {
            wkVBinding.joinBtn.setText(R.string.scan_join_group_confirm_full);
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.joinBtn.setOnClickListener(v -> {
            if (isMember) {
                // 已是群成员，直接进入群聊
                ChatViewMenu chatViewMenu = new ChatViewMenu(ScanJoinGroupActivity.this, groupNo, WKChannelType.GROUP, 0, true);
                EndpointManager.getInstance().invoke(EndpointSID.chatView, chatViewMenu);
                finish();
                return;
            }
            // 非群成员，调用加群 API
            wkVBinding.joinBtn.setEnabled(false);
            ScanService service = RetrofitUtils.getInstance().createService(ScanService.class);
            service.scanJoinGroup(groupNo, authCode)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new BaseObserver<ResponseBody>() {
                        @Override
                        protected void onSuccess(ResponseBody result) {
                            // YUJ-212 / YUJ-200 Path B：scanjoin 响应以 space_id/space_name/group_name
                            // 作为权威字段（PR#1250 新增），二维码预扫 payload 仅用作 fallback。
                            // is_external=1 时此 Toast 分支跳过（外部群走既有 UX 路径）。
                            String respSpaceId = null;
                            String respSpaceName = null;
                            String respGroupName = null;
                            int respIsExternal = 0;
                            try {
                                if (result != null) {
                                    String body = result.string();
                                    if (!TextUtils.isEmpty(body)) {
                                        JSONObject j = JSON.parseObject(body);
                                        if (j != null) {
                                            respSpaceId = j.getString("space_id");
                                            respSpaceName = j.getString("space_name");
                                            respGroupName = j.getString("group_name");
                                            // 后端可能以 int 或 bool 下发；fastjson 对缺字段返回 0，够用
                                            Integer iv = j.getInteger("is_external");
                                            if (iv != null) respIsExternal = iv;
                                        }
                                    }
                                }
                            } catch (IOException | RuntimeException ignored) {
                                // 响应体不可解析时 fail-open：回退到 intent 的 pre-scan 字段
                            }

                            // round-2（Jerry-Xin review）：区分 null（字段缺失，用 pre-scan fallback）
                            // 与 ""（后端显式返回，表示「这是公共群」），避免把公共群误判为跨 Space。
                            // 解析逻辑抽到 ScanJoinEffectiveResolver 以便 host-side 单测覆盖。
                            String effectiveSpaceId = ScanJoinEffectiveResolver.resolve(respSpaceId, targetSpaceId);
                            String effectiveSpaceName = ScanJoinEffectiveResolver.resolve(respSpaceName, targetSpaceName);
                            String effectiveGroupName = ScanJoinEffectiveResolver.resolve(respGroupName, groupName);

                            String viewerSpaceId = SpaceFilter.getCurrentSpaceId();
                            boolean crossSpace = ScanJoinEffectiveResolver.isCrossSpace(effectiveSpaceId, viewerSpaceId);

                            // 硬约束：
                            //   1) 公共群(space_id='')/同 Space → 走常规 toast，不持久化跨空间通知
                            //   2) is_external=1 → 外部群不走此 Toast 路径，保持既有行为
                            if (crossSpace && respIsExternal != 1) {
                                JoinSuccessHelper.computeAndSave(groupNo, effectiveGroupName,
                                        effectiveSpaceId, effectiveSpaceName);
                                finish();
                                return;
                            }

                            WKToastUtils.getInstance().showToast(getString(R.string.scan_join_group_success));
                            ChatViewMenu chatViewMenu = new ChatViewMenu(ScanJoinGroupActivity.this, groupNo, WKChannelType.GROUP, 0, true);
                            EndpointManager.getInstance().invoke(EndpointSID.chatView, chatViewMenu);
                            finish();
                        }

                        @Override
                        protected void onFail(int code, String msg, String errJson) {
                            wkVBinding.joinBtn.setEnabled(true);
                            if (!TextUtils.isEmpty(msg)) {
                                WKToastUtils.getInstance().showToast(msg);
                            }
                        }
                    });
        });
    }
}
