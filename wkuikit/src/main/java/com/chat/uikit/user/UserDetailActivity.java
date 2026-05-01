package com.chat.uikit.user;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
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
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.AlertDialog;
import com.chat.base.utils.StringUtils;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NormalClickableContent;
import com.chat.base.ui.components.NormalClickableSpan;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
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
            finish();
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
                    wkVBinding.nameTv.setText(TextUtils.isEmpty(userInfo.remark) ? userInfo.name : userInfo.remark);
                    wkVBinding.nickNameTv.setText(userInfo.name);
                    wkVBinding.nickNameLayout.setVisibility(TextUtils.isEmpty(userInfo.remark) ? View.GONE : View.VISIBLE);
                    if (TextUtils.isEmpty(userInfo.short_no)) {
                        wkVBinding.identityLayout.setVisibility(View.GONE);
                    } else {
                        wkVBinding.identityLayout.setVisibility(View.VISIBLE);
                        wkVBinding.appIdNumTv.setText(userInfo.short_no);
                    }
                    // YUJ-136 (对齐 web PR#1021)：先算 viewer-relative 外部判定，
                    // 同时驱动「来源」行 (YUJ-87) 与「发送消息」按钮是否隐藏 (本任务)。
                    ExternalViewerResolver.Resolution externalRes =
                            UserDetailExternalHelper.resolve(
                                    userInfo,
                                    WKIM.getInstance().getChannelMembersManager()
                                            .getMember(groupID, WKChannelType.GROUP, uid),
                                    WKConfig.getInstance().getUid(),
                                    MsgModel.getInstance().getCurrentSpaceId(),
                                    groupID);

                    if (!applyExternalSourceRow(userInfo, externalRes)) {
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
                    boolean hideSendMsgForExternal =
                            UserDetailExternalHelper.shouldHideSendMessageButton(externalRes);
                    wkVBinding.sendMsgBtn.setVisibility(
                            userInfo.follow == 1 && !hideSendMsgForExternal
                                    ? View.VISIBLE : View.GONE);
                    // YUJ-177 (对齐 web PR#1013/1091 · iOS YUJ-136)：跨 Space 外部成员不允许申请加好友。
                    // YUJ-146-2 (对齐 web PR YUJ-144)：同 Space 成员隐藏「解除好友 / 拉黑」，
                    // 跨 Space 成员保持原逻辑（delete 受 follow 控制，blacklist 常驻）。
                    boolean isExternalUser = isExternalUser(userInfo, externalRes);
                    wkVBinding.deleteLayout.setVisibility(
                            isExternalUser && userInfo.follow == 1 ? View.VISIBLE : View.GONE);
                    wkVBinding.pushBlackLayout.setVisibility(
                            isExternalUser ? View.VISIBLE : View.GONE);
                    wkVBinding.blacklistDescTv.setVisibility(userInfo.status == 2 ? View.VISIBLE : View.GONE);
                    wkVBinding.applyBtn.setVisibility(
                            UserDetailExternalHelper.shouldShowApplyButton(
                                    isExternalUser, userInfo.follow, !TextUtils.isEmpty(vercode))
                                    ? View.VISIBLE : View.GONE);

                    // YUJ-188 临时诊断：记录申请加好友按钮可见性相关输入，便于定位
                    // isExternalUser == false 的根因。上线前会随 revert PR 移除。
                    if (com.chat.base.BuildConfig.DEBUG) {
                        android.util.Log.d("YUJ188", "apply-btn-diag"
                                + " entry_groupID=" + groupID
                                + " viewerUid=" + WKConfig.getInstance().getUid()
                                + " viewerSpaceId=" + MsgModel.getInstance().getCurrentSpaceId()
                                + " userInfo.uid=" + userInfo.uid
                                + " userInfo.home_space_id=" + userInfo.home_space_id
                                + " userInfo.is_external=" + userInfo.is_external
                                + " userInfo.follow=" + userInfo.follow
                                + " vercode=" + (!TextUtils.isEmpty(vercode))
                                + " externalRes=" + (externalRes == null
                                        ? "null"
                                        : ("isExternal=" + externalRes.isExternal()))
                                + " isExternalUser=" + isExternalUser
                                + " shouldShowApplyButton=" + wkVBinding.applyBtn.getVisibility());
                    }

                    // Bot-specific UI
                    isBot = userInfo.robot == 1;
                    botDescription = userInfo.bot_description;
                    botCreatorName = userInfo.bot_creator_name;
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
        boolean hasDesc = !TextUtils.isEmpty(userInfo.bot_description);
        boolean hasCreator = !TextUtils.isEmpty(userInfo.bot_creator_name);
        boolean hasCommands = false;

        // Bot description
        wkVBinding.botDescLayout.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        if (hasDesc) {
            wkVBinding.botDescTv.setText(userInfo.bot_description);
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

        boolean hasAnyInfo = hasDesc || hasCreator || hasCommands;
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
     * 外部群视角下刷新「来源」行（YUJ-87 / 对齐 web #976）：
     *   - 当前在群内打开 UserInfo（groupID 非空）且 viewer-relative 判定为外部 →
     *     整行显示成员 home/source Space 名，替代老的 source_desc。
     *   - 同 Space / 自看 / 无 home_space_id 的非外部成员 → 整行隐藏，继续用上层 source_desc 逻辑。
     * 返回 true 表示「来源」行已经由本方法处理，调用方不应再覆盖。
     *
     * <p>YUJ-136：判定抽入 {@link UserDetailExternalHelper}（可单测），本方法只负责把
     * {@link ExternalViewerResolver.Resolution} 映射到 fromLayout 的可见性 / 文案，
     * 同一条 resolution 也喂给「发送消息」按钮隐藏逻辑，确保两处视觉判断完全一致。
     */
    private boolean applyExternalSourceRow(
            com.chat.uikit.enity.UserInfo userInfo,
            ExternalViewerResolver.Resolution res) {
        if (userInfo == null) return false;
        if (res == null) return false;
        if (!res.isExternal()) {
            // 同 Space（或未命中外部判定）→ 按产品规格「整行隐藏」，让上层 source_desc 不再抢占此行
            wkVBinding.fromLayout.setVisibility(View.GONE);
            return true;
        }
        if (TextUtils.isEmpty(res.getSourceSpaceName())) {
            // 是外部但没拿到 Space 名 —— 不渲染空「来源」，直接隐藏避免 UI 抖动
            wkVBinding.fromLayout.setVisibility(View.GONE);
            return true;
        }
        wkVBinding.sourceFromTv.setText(res.getSourceSpaceName());
        wkVBinding.fromLayout.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * YUJ-146-2 (对齐 web PR YUJ-144)：判定 UserInfo 面板当前展示的成员对 viewer 是否为外部。
     *
     * <p>优先使用群上下文下的 viewer-relative resolver（与 YUJ-136 同源）；resolver 无输入
     * （无 groupID / 自看 / 无 extras）时，回退到 UserInfo 自身字段：
     * <ul>
     *   <li>{@code home_space_id} vs 当前 viewer Space：不相等视为外部；</li>
     *   <li>{@code home_space_id} 不可用 → legacy {@code is_external == 1}。</li>
     * </ul>
     * 同 Space（或判定不出外部）的成员在 UI 上应隐藏「解除好友 / 拉黑」按钮。
     */
    private boolean isExternalUser(
            com.chat.uikit.enity.UserInfo userInfo,
            ExternalViewerResolver.Resolution res) {
        if (res != null) {
            return res.isExternal();
        }
        if (userInfo == null) return false;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        String home = userInfo.home_space_id;
        if (!TextUtils.isEmpty(home) && !TextUtils.isEmpty(currentSpaceId)) {
            return !home.equals(currentSpaceId);
        }
        return userInfo.is_external == 1;
    }
}
