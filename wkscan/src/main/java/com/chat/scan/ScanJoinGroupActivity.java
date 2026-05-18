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

package com.chat.scan;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
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
import com.chat.base.space.PendingGroupInvite;
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

    private static final String TAG = "YUJ372-need-space";

    /**
     * 跳转 SpaceGuideActivity 的组件名（app 模块下 Activity）。
     * wkscan 不依赖 app，这里用 className + packageName 跨模块启动。
     */
    private static final String SPACE_GUIDE_CLASS_NAME = "com.octoim.app.SpaceGuideActivity";

    private String groupNo;
    private String authCode;
    private boolean isMember;
    private String groupName;
    private String avatar;
    private int memberCount;
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
        avatar = getIntent().getStringExtra("avatar");
        memberCount = getIntent().getIntExtra("member_count", 0);
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
                            //  /  Path B：scanjoin 响应以 space_id/space_name/group_name
                            // 作为权威字段（PR#1250 新增），二维码预扫 payload 仅用作 fallback。
                            // is_external=1 时此 Toast 分支跳过（外部群走既有 UX 路径）。
                            //
                            //  Phase 2（ / PR#1320）：零 Space 用户命中
                            // {"status":"need_space","msg":"..."} 响应 → 不走 JoinSuccessHelper，
                            // 暂存上下文到 pending_group_invite 后拉起 SpaceGuideActivity。
                            String respSpaceId = null;
                            String respSpaceName = null;
                            String respGroupName = null;
                            int respIsExternal = 0;
                            String respStatus = null;
                            String respMsg = null;
                            try {
                                if (result != null) {
                                    String body = result.string();
                                    // ：need_space 识别走本地 helper，fail-open（解析失败视为正常响应）。
                                    if (PendingGroupInvite.isNeedSpaceResponse(body)) {
                                        handleNeedSpace(body);
                                        return;
                                    }
                                    if (!TextUtils.isEmpty(body)) {
                                        JSONObject j = JSON.parseObject(body);
                                        if (j != null) {
                                            respStatus = j.getString("status");
                                            respMsg = j.getString("msg");
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

                            //  兜底：极少数情况下 body 已经被消费过（IOException），
                            // 此时 respStatus 仍可能命中（fastjson 分支）。保持 defensive。
                            if (PendingGroupInvite.STATUS_NEED_SPACE.equals(respStatus)) {
                                handleNeedSpaceWithMsg(respMsg);
                                return;
                            }

                            // round-2（review）：区分 null（字段缺失，用 pre-scan fallback）
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

    // ------------------------------------------------------------------
    //  Phase 2 · need_space 处理
    // ------------------------------------------------------------------

    /**
     * 从已解析的响应体文本中提取 msg 后走 {@link #handleNeedSpaceWithMsg(String)}。
     */
    private void handleNeedSpace(String body) {
        String msg = null;
        try {
            JSONObject j = JSON.parseObject(body);
            if (j != null) {
                msg = j.getString("msg");
            }
        } catch (RuntimeException ignored) {
        }
        handleNeedSpaceWithMsg(msg);
    }

    /**
     * 统一入口：暂存 pending invite → 弹 Toast → 跳 SpaceGuideActivity → 结束自己。
     *
     * <p>后端默认 msg 为「请先加入一个 Space 后再入群」，UI 层允许本地化覆盖为
     * {@code R.string.scan_join_group_need_space} 以对齐「加入群聊」文案。
     */
    private void handleNeedSpaceWithMsg(String backendMsg) {
        PendingGroupInvite.Pending pending = new PendingGroupInvite.Pending(
                groupNo, authCode, groupName, avatar, memberCount, isMember,
                targetSpaceId, targetSpaceName);
        PendingGroupInvite.save(pending);

        String toast;
        if (!TextUtils.isEmpty(backendMsg)) {
            toast = backendMsg;
        } else {
            toast = getString(R.string.scan_join_group_need_space);
        }
        WKToastUtils.getInstance().showToast(toast);

        try {
            Intent guide = new Intent();
            guide.setClassName(getPackageName(), SPACE_GUIDE_CLASS_NAME);
            guide.putExtra("pending_group_invite", true);
            // 清掉 top 以便 SpaceGuideActivity 成为当前栈顶，用户完成加 Space 后再回退
            guide.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(guide);
        } catch (Throwable t) {
            // 极端情况下 SpaceGuideActivity 未注册（如单模块调试包）— 不 crash，仅留日志
            Log.w(TAG, "launch SpaceGuideActivity failed: " + t);
        }
        finish();
    }
}
