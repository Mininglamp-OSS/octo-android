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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.chat.base.act.WKCropImageActivity;
import com.chat.base.act.WKAnimatedAvatarPreviewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.UserDetailViewMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.external.ExternalViewerResolver;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.AlertDialog;
import com.chat.base.utils.StringUtils;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NormalClickableContent;
import com.chat.base.ui.components.NormalClickableSpan;
import com.chat.base.utils.AnimatedImageUtils;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActUserDetailLayoutBinding;
import com.chat.uikit.db.WKContactsDB;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 2020-03-19 22:06
 * 个人资料
 */
public class UserDetailActivity extends WKBaseActivity<ActUserDetailLayoutBinding> {
    String uid;
    String groupID;
    private String vercode;
    private WKChannel userChannel;
    private boolean isBot;
    private String botDescription;
    private String botCreatorName;
    /**
     *  (对齐 web PR#1092 BotDetailModal)：从 /users/{uid} 拿到的 bot 创建者 uid，
     * 用于判定当前登录者是否为该 bot 的 owner（可编辑头像 / 简介）。
     */
    private String botCreatorUid;
    /** ：当前页面的 bot 是否归属当前登录者。showBotInfo() 内计算并缓存。 */
    private boolean isBotOwner;

    /**
     * ：外部成员 " @SpaceName" 后缀的灰紫色（对齐 GroupMemberAdapter /
     * SearchUserAdapter / RemindMemberAdapter 的 0xFF8B5CF6，避免各处渲染色号漂移）。
     */
    private static final int EXTERNAL_SPACE_SUFFIX_COLOR = 0xFF8B5CF6;

    @Override
    protected ActUserDetailLayoutBinding getViewBinding() {
        return ActUserDetailLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.user_card);
    }

    @Override
    protected void initPresenter() {
        initParams(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initParams(intent);
        initView();
        initListener();
        initData();
    }

    private void initParams(Intent mIntent) {
        uid = mIntent.getStringExtra("uid");
        if (TextUtils.isEmpty(uid)) finish();
        if (uid.equals(WKSystemAccount.system_file_helper)) {
            Intent intent = new Intent(this, WKFileHelperActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        if (uid.equals(WKSystemAccount.system_team)) {
            Intent intent = new Intent(this, WKSystemTeamActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        if (uid.equals(WKConfig.getInstance().getUid())) {
            Intent intent = new Intent(this, MyInfoActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        if (mIntent.hasExtra("groupID")) {
            groupID = mIntent.getStringExtra("groupID");
        } else {
            groupID = "";
        }
        if (mIntent.hasExtra("vercode")) {
            vercode = mIntent.getStringExtra("vercode");
        } else {
            vercode = "";
        }
        userChannel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        if (!TextUtils.isEmpty(groupID)) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(groupID, WKChannelType.GROUP, uid);
            if (member != null && member.extraMap != null && member.extraMap.containsKey(WKChannelMemberExtras.WKCode)) {
                vercode = (String) member.extraMap.get(WKChannelMemberExtras.WKCode);
            }
            if (member != null && !TextUtils.isEmpty(member.memberRemark)) {
                wkVBinding.inGroupNameLayout.setVisibility(View.VISIBLE);
                wkVBinding.inGroupNameTv.setText(member.memberRemark);
            }
            if (member != null && !TextUtils.isEmpty(member.memberInviteUID) && member.isDeleted == 0) {
                String name = "";
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(member.memberInviteUID, WKChannelType.PERSONAL);
                if (channel != null) {
                    name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                }
                if (TextUtils.isEmpty(name)) {
                    WKChannelMember member1 = WKIM.getInstance().getChannelMembersManager().getMember(groupID, WKChannelType.GROUP, member.memberInviteUID);
                    if (member1 != null) {
                        name = TextUtils.isEmpty(member1.memberRemark) ? member1.memberName : member1.memberRemark;
                    }
                }
                if (!TextUtils.isEmpty(name)) {
                    wkVBinding.joinGroupWayLayout.setVisibility(View.VISIBLE);
                    String showTime = "";
                    if (!TextUtils.isEmpty(member.createdAt) && member.createdAt.contains(" ")) {
                        showTime = member.createdAt.split(" ")[0];
                    }
                    String content = String.format("%s %s", showTime, String.format(getString(R.string.invite_join_group), name));
                    wkVBinding.joinGroupWayTv.setText(content);
                    int index = content.indexOf(name);
                    SpannableString span = new SpannableString(content);
                    span.setSpan(new NormalClickableSpan(false, Theme.colorAccount, new NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""), view -> {

                    }), index, index + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    wkVBinding.joinGroupWayTv.setText(span);
                }
            }
        } else {
            wkVBinding.joinGroupWayLayout.setVisibility(View.GONE);
        }

    }

    @Override
    protected void initView() {
        wkVBinding.applyBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.sendMsgBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.avatarView.setSize(50);
        wkVBinding.appIdNumLeftTv.setText(String.format(getString(R.string.app_idnum), getString(R.string.app_name)));
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.otherLayout.removeAllViews();
        List<View> list = EndpointManager.getInstance().invokes(EndpointCategory.wkUserDetailView, new UserDetailViewMenu(this, wkVBinding.otherLayout, uid, groupID));
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) != null)
                    wkVBinding.otherLayout.addView(list.get(i));
            }
        }
        if (wkVBinding.otherLayout.getChildCount() > 0) {
            LinearLayout view = new LinearLayout(this);
            view.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
            view.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 15));
            wkVBinding.otherLayout.addView(view);
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        if (!TextUtils.isEmpty(groupID) && !uid.equals(WKConfig.getInstance().getUid())) {
            WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("user_detail_refresh_channel", (channel, isEnd) -> {
                if (channel != null && channel.channelID.equals(groupID) && channel.channelType == WKChannelType.GROUP) {
                    getUserInfo();
                    wkVBinding.avatarView.showAvatar(channel);
                }
            });
        }

        wkVBinding.pushBlackLayout.setOnClickListener(v -> {

            if (userChannel == null) return;
            String title = getString(userChannel.status == 2 ? R.string.pull_out_black_list : R.string.push_black_list);
            String content = getString(userChannel.status == 2 ? R.string.pull_out_black_list_tips : R.string.join_black_list_tips);

            WKDialogUtils.getInstance().showDialog(this, title, content, true, "", "", 0, 0, index -> {
                if (index == 1) {
                    if (userChannel.status != 2)
                        UserModel.getInstance().addBlackList(uid, (code, msg) -> {
                            if (code == HttpResponseCode.success) {
                                finish();
                            } else showToast(msg);
                        });
                    else UserModel.getInstance().removeBlackList(uid, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            finish();
                        } else showToast(msg);
                    });

                }
            });

        });
        setonLongClick(wkVBinding.nameTv, wkVBinding.nameTv);
        setonLongClick(wkVBinding.identityLayout, wkVBinding.appIdNumTv);
        setonLongClick(wkVBinding.nickNameLayout, wkVBinding.nickNameTv);
        // Bot info: enable native text selection
        wkVBinding.botDescTv.setTextIsSelectable(true);
        wkVBinding.botCreatorTv.setTextIsSelectable(true);
        wkVBinding.botIdTv.setTextIsSelectable(true);

        //频道资料刷新
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("user_detail_refresh_channel1", (channel, isEnd) -> {
            if (channel != null && channel.channelID.equals(uid) && channel.channelType == WKChannelType.PERSONAL) {
                userChannel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                setData();
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.applyBtn, v -> {
            if (isBot) {
                showBotApplyDialog();
            } else {
                // Normal user: show input dialog for remark
                WKDialogUtils.getInstance().showInputDialog(UserDetailActivity.this, getString(R.string.apply), getString(R.string.input_remark), "", getString(R.string.input_remark), 20, text -> FriendModel.getInstance().applyAddFriend(uid, vercode, text, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        wkVBinding.applyBtn.setText(R.string.applyed);
                        wkVBinding.applyBtn.setAlpha(0.2f);
                        wkVBinding.applyBtn.setEnabled(false);
                    } else showToast(msg);
                }));
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.sendMsgBtn, v -> {
            WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, uid, WKChannelType.PERSONAL, 0, true));
        });
        wkVBinding.deleteLayout.setOnClickListener(v -> {
            String content = String.format(getString(R.string.delete_friends_tips), wkVBinding.nameTv.getText().toString());
            WKDialogUtils.getInstance().showDialog(this, getString(R.string.delete_friends), content, true, "", getString(R.string.delete), 0, ContextCompat.getColor(this, R.color.red), index -> {
                if (index == 1) {
                    UserModel.getInstance().deleteUser(uid, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            WKIM.getInstance().getConversationManager().deleteWitchChannel(uid, WKChannelType.PERSONAL);
                            MsgModel.getInstance().offsetMsg(uid, WKChannelType.PERSONAL, null);
                            WKIM.getInstance().getMsgManager().clearWithChannel(uid, WKChannelType.PERSONAL);
                            WKContactsDB.getInstance().updateFriendStatus(uid, 0);
                            WKIM.getInstance().getChannelManager().updateFollow(uid, WKChannelType.PERSONAL, 0);
                            EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
                            EndpointManager.getInstance().invokes(EndpointCategory.wkExitChat, new WKChannel(uid, WKChannelType.PERSONAL));
                            finish();
                        } else showToast(msg);
                    });
                }
            });
        });
        SingleClickUtil.onSingleClick(wkVBinding.remarkLayout, v -> {
            Intent intent = new Intent(this, SetUserRemarkActivity.class);
            intent.putExtra("uid", uid);
            intent.putExtra("oldStr", userChannel == null ? "" : userChannel.channelRemark);
            chooseResultLac.launch(intent);
        });
        wkVBinding.avatarView.setOnClickListener(v -> showImg());
    }

    private void showCopy(View view, float[] coordinate, String content) {
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.copy), R.mipmap.msg_copy, () -> {
            view.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", content);
            assert cm != null;
            cm.setPrimaryClip(mClipData);
            WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed));
        }));
        view.setBackgroundColor(ContextCompat.getColor(this, R.color.color999));
        WKDialogUtils.getInstance().showScreenPopup(view, coordinate, list, () -> view.setBackgroundColor(ContextCompat.getColor(UserDetailActivity.this, R.color.transparent)));
    }

    @Override
    protected void initData() {
        super.initData();
        setData();
        getUserInfo();
    }

    private void setData() {
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL);
        if (uid.equals(WKConfig.getInstance().getUid())) hideTitleRightView();
        if (userChannel != null) {
            if (!TextUtils.isEmpty(userChannel.channelRemark)) {
                wkVBinding.nickNameLayout.setVisibility(View.VISIBLE);
                wkVBinding.nickNameTv.setText(userChannel.channelName);
                wkVBinding.nameTv.setText(userChannel.channelRemark);
            } else {
                wkVBinding.nameTv.setText(userChannel.channelName);
                wkVBinding.nickNameLayout.setVisibility(View.GONE);
            }
        } else {
            wkVBinding.deleteLayout.setVisibility(View.GONE);
            wkVBinding.sendMsgBtn.setVisibility(View.GONE);
        }
    }

    private void getUserInfo() {
        WKIM.getInstance().getChannelManager().fetchChannelInfo(uid, WKChannelType.PERSONAL);
        UserModel.getInstance().getUserInfo(uid, groupID, (code, msg, userInfo) -> {
            if (code == HttpResponseCode.success) {
                if (userInfo != null) {
                    if (!TextUtils.isEmpty(userInfo.vercode)) {
                        vercode = userInfo.vercode;
                    }
                    //  (对齐 web PR#1021)：先算 viewer-relative 外部判定，
                    // 同时驱动「来源」行 () 与「发送消息」按钮是否隐藏，
                    // 以及  的 bottomPanel 全隐 + 来源 Space 后缀。
                    ExternalViewerResolver.Resolution externalRes =
                            UserDetailExternalHelper.resolve(
                                    userInfo,
                                    WKIM.getInstance().getChannelMembersManager()
                                            .getMember(groupID, WKChannelType.GROUP, uid),
                                    WKConfig.getInstance().getUid(),
                                    MsgModel.getInstance().getCurrentSpaceId(),
                                    groupID);
                    //  (对齐 web PR#1013/1091 · iOS )：跨 Space 外部成员不允许申请加好友。
                    // -2 (对齐 web PR )：同 Space 成员隐藏「解除好友 / 拉黑」，
                    // 跨 Space 成员由  统一走 bottomPanel 全隐。
                    boolean isExternalUser = isExternalUser(userInfo, externalRes);

                    // ：name 旁拼 " @SpaceName" 灰紫色后缀（对齐 web Subscribers/list.tsx:320）。
                    // 必须先于下方的 setText 分支，否则 nameTv 会被 userInfo.name / remark 覆盖。
                    String baseName = TextUtils.isEmpty(userInfo.remark) ? userInfo.name : userInfo.remark;
                    String sourceSpaceLabel = UserDetailExternalHelper.resolveSourceSpaceLabel(
                            isExternalUser, externalRes, userInfo);
                    wkVBinding.nameTv.setText(buildNameWithExternalSuffix(baseName, sourceSpaceLabel));
                    wkVBinding.nickNameTv.setText(userInfo.name);
                    wkVBinding.nickNameLayout.setVisibility(TextUtils.isEmpty(userInfo.remark) ? View.GONE : View.VISIBLE);
                    wkVBinding.realnameVerifiedIv.setVisibility(userInfo.realname_verified ? View.VISIBLE : View.GONE);
                    if (TextUtils.isEmpty(userInfo.short_no)) {
                        wkVBinding.identityLayout.setVisibility(View.GONE);
                    } else {
                        wkVBinding.identityLayout.setVisibility(View.VISIBLE);
                        wkVBinding.appIdNumTv.setText(userInfo.short_no);
                    }

                    if (!applyExternalSourceRow(userInfo, externalRes, isExternalUser, sourceSpaceLabel)) {
                        if (!TextUtils.isEmpty(userInfo.source_desc)) {
                            wkVBinding.sourceFromTv.setText(userInfo.source_desc);
                            wkVBinding.fromLayout.setVisibility(View.VISIBLE);
                        } else {
                            wkVBinding.fromLayout.setVisibility(View.GONE);
                        }
                    }

                    if (userInfo.status == 2) {
                        wkVBinding.blacklistTv.setText(R.string.pull_out_black_list);
                    } else {
                        wkVBinding.blacklistTv.setText(R.string.push_black_list);
                    }

                    // （对齐 web PR#1021 UserInfo/index.tsx:29-48）：
                    // 外部成员 viewer 下整个 bottomPanel（applyBtn / sendMsgBtn /
                    // deleteLayout / pushBlackLayout / blacklistDesc）一起隐藏，
                    // 用 externalHintTv「仅可在群内交流」替代。
                    boolean hideBottomPanel = UserDetailExternalHelper.shouldHideBottomPanel(isExternalUser);
                    boolean showExternalHint = UserDetailExternalHelper.shouldShowExternalHint(isExternalUser);
                    wkVBinding.externalHintTv.setVisibility(showExternalHint ? View.VISIBLE : View.GONE);

                    // （对齐 web UserInfo/index.tsx:52-55 / 企微）：Space 模式
                    // 下同 Space 非好友（人类）直接「发送消息」，跳过「申请加好友」。
                    // 嘉伟 2026-05-01 Android 真机实测：外部群点成员误显 applyBtn 的
                    // 根因之二。Bot 走独立的 bot_add_friend 审批流，所以此分支不接管 bot。
                    boolean spaceModeSendMsg =
                            UserDetailExternalHelper.shouldUseSpaceModeSendMessage(
                                    isExternalUser,
                                    MsgModel.getInstance().getCurrentSpaceId(),
                                    userInfo.robot == 1,
                                    userInfo.follow);

                    if (hideBottomPanel) {
                        wkVBinding.sendMsgBtn.setVisibility(View.GONE);
                        wkVBinding.deleteLayout.setVisibility(View.GONE);
                        wkVBinding.pushBlackLayout.setVisibility(View.GONE);
                        wkVBinding.blacklistDescTv.setVisibility(View.GONE);
                        wkVBinding.applyBtn.setVisibility(View.GONE);
                    } else {
                        boolean hideSendMsgForExternal =
                                UserDetailExternalHelper.shouldHideSendMessageButton(externalRes);
                        // ：follow=1 || Space 模式 + 非好友 + 非 bot → 显示 sendMsg。
                        wkVBinding.sendMsgBtn.setVisibility(
                                ((userInfo.follow == 1 && !hideSendMsgForExternal) || spaceModeSendMsg)
                                        ? View.VISIBLE : View.GONE);
                        // -2：同 Space 成员隐藏「解除好友 / 拉黑」。
                        wkVBinding.deleteLayout.setVisibility(View.GONE);
                        wkVBinding.pushBlackLayout.setVisibility(View.GONE);
                        wkVBinding.blacklistDescTv.setVisibility(userInfo.status == 2 ? View.VISIBLE : View.GONE);
                        // ：Space 模式下 applyBtn 让位给 sendMsgBtn，
                        // 仅在非 Space 模式 + 陌生人 + 持 vercode 时才展示（保持老语义）。
                        wkVBinding.applyBtn.setVisibility(
                                (!spaceModeSendMsg
                                        && UserDetailExternalHelper.shouldShowApplyButton(
                                                isExternalUser, userInfo.follow, !TextUtils.isEmpty(vercode)))
                                        ? View.VISIBLE : View.GONE);
                    }

                    // Bot-specific UI
                    isBot = userInfo.robot == 1;
                    botDescription = userInfo.bot_description;
                    botCreatorName = userInfo.bot_creator_name;
                    // ：缓存 bot_creator_uid 给 showBotInfo() / 编辑回调读取。
                    botCreatorUid = userInfo.bot_creator_uid;
                    if (isBot) {
                        // Show AI badge
                        wkVBinding.aiBadgeTv.setVisibility(View.VISIBLE);
                        // Hide sex icon for bot
                        wkVBinding.sexIv.setVisibility(View.GONE);
                        // Hide non-bot UI elements
                        wkVBinding.remarkLayout.setVisibility(View.GONE);
                        wkVBinding.pushBlackLayout.setVisibility(View.GONE);
                        wkVBinding.deleteLayout.setVisibility(View.GONE);

                        // Show bot info section
                        showBotInfo(userInfo);

                        // Change apply button text for Bot
                        if (userInfo.follow == 0 && !isExternalUser) {
                            wkVBinding.applyBtn.setText(R.string.bot_add_friend);
                            wkVBinding.applyBtn.setVisibility(View.VISIBLE);
                        }
                    } else {
                        wkVBinding.aiBadgeTv.setVisibility(View.GONE);
                        wkVBinding.sexIv.setVisibility(View.VISIBLE);
                        wkVBinding.botInfoLayout.setVisibility(View.GONE);
                    }

                    if (!TextUtils.isEmpty(userInfo.join_group_invite_uid)){
                        wkVBinding.joinGroupWayLayout.setVisibility(View.VISIBLE);
                        String content = String.format("%s %s", userInfo.join_group_time, String.format(getString(R.string.invite_join_group), userInfo.join_group_invite_name));
                        wkVBinding.joinGroupWayTv.setText(content);
                        int index = content.indexOf(userInfo.join_group_invite_name);
                        SpannableString span = new SpannableString(content);
                        span.setSpan(new NormalClickableSpan(false, Theme.colorAccount, new NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""), view -> {

                        }), index, index + userInfo.join_group_invite_name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        wkVBinding.joinGroupWayTv.setText(span);
                    }
                }
            } else {
                showToast(msg);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("user_detail_refresh_channel");
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("user_detail_refresh_channel1");
    }


    private void showBotInfo(com.chat.uikit.enity.UserInfo userInfo) {
        //  (对齐 web PR#1092 BotDetailModal)：bot 创建者 = 当前登录者时
        // 切到「可编辑」UI 分支（头像右下铅笔 + 简介右侧铅笔）；非 owner 保持只读。
        String loginUid = WKConfig.getInstance().getUid();
        isBotOwner = !TextUtils.isEmpty(userInfo.bot_creator_uid)
                && !TextUtils.isEmpty(loginUid)
                && userInfo.bot_creator_uid.equals(loginUid);

        boolean hasDesc = !TextUtils.isEmpty(userInfo.bot_description);
        boolean hasCreator = !TextUtils.isEmpty(userInfo.bot_creator_name);
        boolean hasCommands = false;

        // Bot description — owner 分支下即使简介为空也展示整行，让 owner 可以首次添加。
        boolean showDescRow = hasDesc || isBotOwner;
        wkVBinding.botDescLayout.setVisibility(showDescRow ? View.VISIBLE : View.GONE);
        if (showDescRow) {
            wkVBinding.botDescTv.setText(hasDesc
                    ? userInfo.bot_description
                    : getString(R.string.bot_no_description));
        }
        // ：简介右侧「编辑」铅笔。
        wkVBinding.botDescEditIv.setVisibility(isBotOwner ? View.VISIBLE : View.GONE);
        if (isBotOwner) {
            wkVBinding.botDescEditIv.setOnClickListener(v -> showEditBotDescriptionDialog());
        } else {
            wkVBinding.botDescEditIv.setOnClickListener(null);
        }

        // ：头像右下角「编辑」铅笔。
        wkVBinding.botAvatarEditIv.setVisibility(isBotOwner ? View.VISIBLE : View.GONE);
        if (isBotOwner) {
            wkVBinding.botAvatarEditIv.setOnClickListener(v -> chooseBotAvatar());
        } else {
            wkVBinding.botAvatarEditIv.setOnClickListener(null);
        }

        // Bot creator
        wkVBinding.botCreatorLayout.setVisibility(hasCreator ? View.VISIBLE : View.GONE);
        if (hasCreator) {
            wkVBinding.botCreatorTv.setText(userInfo.bot_creator_name);
        }

        // Bot ID
        wkVBinding.botIdLayout.setVisibility(View.VISIBLE);
        wkVBinding.botIdTv.setText(uid);

        // Bot commands
        if (!TextUtils.isEmpty(userInfo.bot_commands)) {
            try {
                JSONArray commands = new JSONArray(userInfo.bot_commands);
                if (commands.length() > 0) {
                    wkVBinding.botCommandsLayout.setVisibility(View.VISIBLE);
                    wkVBinding.botCommandsContainer.removeAllViews();
                    for (int i = 0; i < commands.length(); i++) {
                        JSONObject cmdObj = commands.getJSONObject(i);
                        String cmd = cmdObj.optString("cmd", "");
                        String remark = cmdObj.optString("remark", "");
                        if (!TextUtils.isEmpty(cmd)) {
                            TextView cmdTv = new TextView(this);
                            cmdTv.setTextSize(14);
                            cmdTv.setTextColor(ContextCompat.getColor(this, R.color.color999));
                            String cmdText = TextUtils.isEmpty(remark) ? cmd : cmd + " - " + remark;
                            cmdTv.setText(cmdText);
                            cmdTv.setPadding(0, com.chat.base.utils.AndroidUtilities.dp(4), 0, com.chat.base.utils.AndroidUtilities.dp(4));
                            wkVBinding.botCommandsContainer.addView(cmdTv);
                        }
                    }
                    hasCommands = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (!hasCommands) {
            wkVBinding.botCommandsLayout.setVisibility(View.GONE);
        }

        // ：owner 永远展示 bot_info 卡片（至少有 desc 行 + 头像编辑入口）。
        boolean hasAnyInfo = hasDesc || hasCreator || hasCommands || isBotOwner;
        wkVBinding.botInfoLayout.setVisibility(hasAnyInfo ? View.VISIBLE : View.GONE);
    }

    private void showBotApplyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.bot_add_friend));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int hPadding = AndroidUtilities.dp(24);

        // 简介
        addInfoRow(container, getString(R.string.bot_description_label),
                TextUtils.isEmpty(botDescription) ? getString(R.string.bot_no_description) : botDescription,
                hPadding);

        // 创建者
        if (!TextUtils.isEmpty(botCreatorName)) {
            addInfoRow(container, getString(R.string.bot_creator_label), botCreatorName, hPadding);
        }

        // 申请消息标签
        TextView applyLabel = new TextView(this);
        applyLabel.setText(R.string.bot_apply_message);
        applyLabel.setTextSize(14);
        applyLabel.setTextColor(ContextCompat.getColor(this, R.color.color999));
        container.addView(applyLabel, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, 24, 12, 24, 4));

        // 申请消息输入框
        EditText editText = new EditText(this);
        editText.setHint(R.string.bot_apply_message_hint);
        editText.setFilters(new InputFilter[]{StringUtils.getInputFilter(50)});
        editText.setTextSize(15);
        editText.setMinLines(1);
        editText.setMaxLines(3);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, 24, 0, 24, 0));

        builder.setView(container);
        builder.setPositiveButton(getString(R.string.sure), (dialog, which) -> {
            String remark = editText.getText().toString().trim();
            FriendModel.getInstance().applyAddFriend(uid, vercode, remark, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    wkVBinding.applyBtn.setText(R.string.applyed);
                    wkVBinding.applyBtn.setAlpha(0.2f);
                    wkVBinding.applyBtn.setEnabled(false);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> getUserInfo(), 500);
                } else showToast(msg);
            });
        });
        builder.setNegativeButton(getString(R.string.cancel), null);

        AlertDialog dialog = builder.create();
        builder.setOnPreDismissListener(d -> SoftKeyboardUtils.getInstance().hideInput(this, editText));
        dialog.setBlurParams(1f, true, true);
        dialog.show();

        TextView sureTv = (TextView) dialog.getButton(android.app.Dialog.BUTTON_POSITIVE);
        sureTv.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
        TextView cancelTv = (TextView) dialog.getButton(android.app.Dialog.BUTTON_NEGATIVE);
        cancelTv.setTextColor(ContextCompat.getColor(this, R.color.colorAccentUn));

        SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, editText);
    }

    private void addInfoRow(LinearLayout container, String label, String value, int hPadding) {
        // 标签
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(14);
        labelTv.setTextColor(ContextCompat.getColor(this, R.color.color999));
        container.addView(labelTv, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, 24, 12, 24, 2));

        // 值
        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(15);
        valueTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        container.addView(valueTv, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, 24, 0, 24, 0));
    }

    private void showImg() {
        // 参考 iOS：先刷新 cacheKey 再打开弹窗，确保全屏显示最新头像
        String newCacheKey = UUID.randomUUID().toString().replaceAll("-", "");
        WKIM.getInstance().getChannelManager().updateAvatarCacheKey(uid, WKChannelType.PERSONAL, newCacheKey);
        // 必须用 ?v= 参数（服务端只转发 v 参数到 CDN，?key= 会被忽略导致 CDN 返回旧缓存）
        String uri = WKApiConfig.getAvatarUrl(uid) + "?v=" + newCacheKey;
        List<Object> tempImgList = new ArrayList<>();
        List<ImageView> imageViewList = new ArrayList<>();
        imageViewList.add(wkVBinding.avatarView.imageView);
        tempImgList.add(WKApiConfig.getShowUrl(uri));
        int index = 0;
        WKDialogUtils.getInstance().showImagePopup(this, tempImgList, imageViewList, wkVBinding.avatarView.imageView, index, new ArrayList<>(), null, null);
    }

    ActivityResultLauncher<Intent> chooseResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            getUserInfo();
        }
    });

    // ====================  Bot owner edit avatar/description ====================
    //
    // 对齐 web PR#1092 BotDetailModal 的 handleAvatarUpload / handleSaveDescription：
    // 1. isBotOwner = (bot_creator_uid == currentLoginUid)，在 showBotInfo() 内计算。
    // 2. 头像：owner 点铅笔 → ImagePicker → WKCropImageActivity 裁切 →
    //    UserModel.uploadAvatar(targetBotUid, path) → 本地刷新 avatarCacheKey。
    //    关键差异：uid 参数传 bot uid，而不是沿用 MyHeadPortraitActivity 里写死的
    //    WKConfig.getUid()；后端 POST /users/:uid/avatar 有 creator_uid 校验。
    // 3. 简介：owner 点铅笔 → showInputDialog(预填旧值, maxLength=200) →
    //    UserModel.updateBotDescription(botUid, newDesc) → 本地刷新 + toast。

    /** 头像编辑铅笔入口：打开相册选择一张图后跳 WKCropImageActivity。 */
    private void chooseBotAvatar() {
        if (!isBotOwner) return; // 防御：非 owner 不应到达这里，但双保险避免越权请求。
        String desc = String.format(getString(R.string.file_permissions_des), getString(R.string.app_name));
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.CAMERA};
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                if (!result) return;
                GlideUtils.getInstance().chooseIMG(UserDetailActivity.this, 1, true, ChooseMimeType.img, false, false, new GlideUtils.ISelectBack() {
                    @Override
                    public void onBack(List<ChooseResult> paths) {
                        if (!WKReader.isNotEmpty(paths)) return;
                        if (isFinishing() || isDestroyed()) return;
                        String path = paths.get(0).path;
                        if (TextUtils.isEmpty(path)) return;
                        Intent intent;
                        if (AnimatedImageUtils.isAnimatedGif(path)) {
                            intent = new Intent(UserDetailActivity.this, WKAnimatedAvatarPreviewActivity.class);
                        } else {
                            intent = new Intent(UserDetailActivity.this, WKCropImageActivity.class);
                        }
                        intent.putExtra("path", path);
                        botAvatarCropLauncher.launch(intent);
                    }

                    @Override
                    public void onCancel() {
                    }
                });
            }

            @Override
            public void clickResult(boolean isCancel) {
            }
        }, this, desc, permissions);
    }

    private final ActivityResultLauncher<Intent> botAvatarCropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra("path");
                if (TextUtils.isEmpty(path)) return;
                //  关键点：uid 必须是 targetBotUid（this.uid），而不是 loginUid。
                UserModel.getInstance().uploadAvatar(uid, path, code -> {
                    if (code == HttpResponseCode.success) {
                        refreshBotAvatarInPlace();
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.upload_success));
                    } else {
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.upload_fail));
                    }
                });
            });

    /**
     * 对齐 web PR#1098 "refresh bot avatar in-place after upload"：
     * 成功上传后不重新拉接口，仅本地刷新 avatarCacheKey，让 AvatarView 走
     * 新缓存 key 重新请求 CDN。避免 CDN 仍缓存旧头像导致视觉无变化。
     */
    private void refreshBotAvatarInPlace() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        String newKey = UUID.randomUUID().toString().replace("-", "");
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            channel = new WKChannel();
            channel.channelID = uid;
            channel.channelType = WKChannelType.PERSONAL;
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
        }
        channel.avatarCacheKey = newKey;
        WKIM.getInstance().getChannelManager().updateAvatarCacheKey(uid, WKChannelType.PERSONAL, newKey);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL);
    }

    /** 简介编辑铅笔入口：预填旧值，保存后 PUT /robot/:uid/description。 */
    private void showEditBotDescriptionDialog() {
        if (!isBotOwner) return;
        String oldDesc = botDescription == null ? "" : botDescription;
        WKDialogUtils.getInstance().showInputDialog(
                this,
                getString(R.string.bot_edit_description_title),
                "",
                oldDesc,
                getString(R.string.bot_edit_description_hint),
                200,
                text -> {
                    String newDesc = text == null ? "" : text.trim();
                    if (newDesc.equals(oldDesc)) return; // 未改动不走接口
                    UserModel.getInstance().updateBotDescription(uid, newDesc, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            botDescription = newDesc;
                            // 本地刷新：保持「空值显示 bot_no_description」的语义一致。
                            wkVBinding.botDescTv.setText(
                                    TextUtils.isEmpty(newDesc)
                                            ? getString(R.string.bot_no_description)
                                            : newDesc);
                            WKToastUtils.getInstance().showToastNormal(
                                    getString(R.string.bot_description_update_success));
                        } else {
                            WKToastUtils.getInstance().showToastNormal(
                                    TextUtils.isEmpty(msg) ? getString(R.string.upload_fail) : msg);
                        }
                    });
                });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setonLongClick(View view, TextView textView) {
        final float[][] location = {new float[2]};
        view.setOnTouchListener((var view12, var motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                location[0] = new float[]{motionEvent.getRawX(), motionEvent.getRawY()};
            }
            return false;
        });
        view.setOnLongClickListener(view1 -> {
            showCopy(textView, location[0], textView.getText().toString());
            return true;
        });
    }

    /**
     * 外部群视角下刷新「来源」行（ /  · 对齐 web #976 / #1021）：
     *   - viewer-relative 判定为外部 → 整行显示成员 home/source Space 名，替代老的 source_desc。
     *   - 同 Space / 自看 / 未命中外部判定 → 整行隐藏，继续用上层 source_desc 逻辑。
     *   -  修复：当 viewer-relative resolver 返回 null（group_member 缺字段且无
     *     WKIM 成员缓存）但 UserInfo 顶层字段判定为外部时，也要渲染「来源」行 —— 否则
     *     外部 UserInfo 页底部显示了「仅可在群内交流」但用户看不到 Space 名，上下文丢失。
     * 返回 true 表示「来源」行已经由本方法处理，调用方不应再覆盖。
     */
    private boolean applyExternalSourceRow(
            com.chat.uikit.enity.UserInfo userInfo,
            ExternalViewerResolver.Resolution res,
            boolean isExternalUser,
            String sourceSpaceLabel) {
        if (userInfo == null) return false;
        // 同 Space / 未命中外部 → 产品规格「整行隐藏」，让上层 source_desc 不再抢占此行。
        // 但只有当我们已经确定「不是外部」时才接管；若 isExternalUser=true 但 res=null
        // 也当作接管，避免走回老 source_desc 分支覆盖 Space 名兜底。
        if (!isExternalUser) {
            if (res == null) {
                // 没有任何 viewer-relative 信号，且上游也判断非外部 —— 交还 source_desc
                return false;
            }
            wkVBinding.fromLayout.setVisibility(View.GONE);
            return true;
        }
        if (TextUtils.isEmpty(sourceSpaceLabel)) {
            // 是外部但没拿到 Space 名 —— 不渲染空「来源」，直接隐藏避免 UI 抖动
            wkVBinding.fromLayout.setVisibility(View.GONE);
            return true;
        }
        wkVBinding.sourceFromTv.setText(sourceSpaceLabel);
        wkVBinding.fromLayout.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * （对齐 web Subscribers/list.tsx:320）：外部成员 UserInfo 姓名旁拼
     * " @SpaceName" 灰紫色后缀，确保一眼看出对方归属的 Space。非外部 / 无 Space 名
     * 场景下返回原始昵称，保持老样式。
     */
    private CharSequence buildNameWithExternalSuffix(String baseName, String sourceSpaceLabel) {
        String safeName = baseName == null ? "" : baseName;
        if (TextUtils.isEmpty(sourceSpaceLabel)) {
            return safeName;
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(safeName);
        int start = ssb.length();
        ssb.append(' ').append(getString(R.string.external_member_space_suffix, sourceSpaceLabel));
        ssb.setSpan(new ForegroundColorSpan(EXTERNAL_SPACE_SUFFIX_COLOR),
                start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    /**
     * -2 (对齐 web PR ) +  (对齐 web PR#1021 `isExternalToViewer`)：
     * 判定 UserInfo 面板当前展示的成员对 viewer 是否为外部。
     *
     * <p>对齐 web 的「多源 OR」语义 — web 的 {@code UserInfoVM.isExternalToViewer} 会
     * 依次试 {@code fromSubscriberOfUser.orgData}（群成员 subscriber）和
     * {@code channelInfo.orgData}（/users/{uid}?group_no 顶层），只要**任一**路径
     * 判定为外部即返回 true；Android 对应的两个数据源分别是：
     * <ol>
     *   <li>群 context 下的 viewer-relative resolver（读 group_member / WKIM 成员缓存 extras） —
     *       等价于 web 的 subscriber；</li>
     *   <li>UserInfo 顶层 {@code home_space_id} / legacy {@code is_external} —
     *       等价于 web 的 channelInfo.orgData。</li>
     * </ol>
     * 任一路径判定为外部就按外部处理；两个路径都说「不是外部」才返回 false。
     *
     * <p>这样当后端只在 {@code group_member} 或只在顶层回填外部字段、或 WKIM 成员缓存
     * 因增量 sync 缺字段时，应用层依然能命中隐藏规则（ 根因下的兜底）。
     */
    private boolean isExternalUser(
            com.chat.uikit.enity.UserInfo userInfo,
            ExternalViewerResolver.Resolution res) {
        // 源 1：resolver（group_member DTO / 成员缓存 extras）
        if (res != null && res.isExternal()) {
            return true;
        }
        // 源 2：UserInfo 顶层字段（/users/{uid} 顶层 — 对齐 web channelInfo.orgData）
        if (userInfo == null) return false;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        String home = userInfo.home_space_id;
        if (!TextUtils.isEmpty(home)) {
            if (!TextUtils.isEmpty(currentSpaceId)) {
                if (!home.equals(currentSpaceId)) return true;
            } else {
                // viewer Space 未知时与 resolver 对齐：home 非空即按外部处理
                return true;
            }
        } else if (userInfo.is_external == 1) {
            return true;
        }
        return false;
    }
}
