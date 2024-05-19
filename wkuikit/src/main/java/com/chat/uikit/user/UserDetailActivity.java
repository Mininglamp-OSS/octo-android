package com.chat.uikit.user;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.UserDetailViewMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NormalClickableContent;
import com.chat.base.ui.components.NormalClickableSpan;
import com.chat.base.utils.LayoutHelper;
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
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;

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

        //频道资料刷新
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("user_detail_refresh_channel1", (channel, isEnd) -> {
            if (channel != null && channel.channelID.equals(uid) && channel.channelType == WKChannelType.PERSONAL) {
                userChannel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                setData();
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.applyBtn, v -> WKDialogUtils.getInstance().showInputDialog(UserDetailActivity.this, getString(R.string.apply), getString(R.string.input_remark), "", getString(R.string.input_remark), 20, text -> FriendModel.getInstance().applyAddFriend(uid, vercode, text, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                wkVBinding.applyBtn.setText(R.string.applyed);
                wkVBinding.applyBtn.setAlpha(0.2f);
                wkVBinding.applyBtn.setEnabled(false);
            } else showToast(msg);
        })));
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
                    wkVBinding.nameTv.setText(TextUtils.isEmpty(userInfo.remark) ? userInfo.name : userInfo.remark);
                    wkVBinding.nickNameTv.setText(userInfo.name);
                    wkVBinding.nickNameLayout.setVisibility(TextUtils.isEmpty(userInfo.remark) ? View.GONE : View.VISIBLE);
                    if (TextUtils.isEmpty(userInfo.short_no)) {
                        wkVBinding.identityLayout.setVisibility(View.GONE);
                    } else {
                        wkVBinding.identityLayout.setVisibility(View.VISIBLE);
                        wkVBinding.appIdNumTv.setText(userInfo.short_no);
                    }
                    if (!TextUtils.isEmpty(userInfo.source_desc)) {
                        wkVBinding.sourceFromTv.setText(userInfo.source_desc);
                        wkVBinding.fromLayout.setVisibility(View.VISIBLE);
                    } else {
                        wkVBinding.fromLayout.setVisibility(View.GONE);
                    }

                    if (userInfo.status == 2) {
                        wkVBinding.blacklistTv.setText(R.string.pull_out_black_list);
                    } else {
                        wkVBinding.blacklistTv.setText(R.string.push_black_list);
                    }
                    wkVBinding.sendMsgBtn.setVisibility(userInfo.follow == 1 ? View.VISIBLE : View.GONE);
                    wkVBinding.applyBtn.setVisibility(userInfo.follow == 1 ? View.GONE : View.VISIBLE);
                    wkVBinding.deleteLayout.setVisibility(userInfo.follow == 1 ? View.VISIBLE : View.GONE);
                    wkVBinding.blacklistDescTv.setVisibility(userInfo.status == 2 ? View.VISIBLE : View.GONE);
                }
            } else {
                showToast(msg);
            }
        });
    }


//    private void showShortNum() {
//        boolean isShowId = true;
//        boolean isFriend = false;
//        int status = 1;
//        if (!TextUtils.isEmpty(groupID)) {
//            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupID, WKChannelType.GROUP);
//            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(groupID, WKChannelType.GROUP, WKConfig.getInstance().getUid());
//            if (member != null && member.role == WKChannelMemberRole.normal && channel != null && channel.remoteExtraMap != null) {
//                Object object = channel.remoteExtraMap.get(WKChannelExtras.forbiddenAddFriend);
//                int forbiddenAddFriend = 0;
//                if (object != null) {
//                    forbiddenAddFriend = (int) object;
//                }
//                if (forbiddenAddFriend == 1)
//                    isShowId = false;
//            }
//        }
//
//        String sourceDesc = "";
//        if (userChannel != null && userChannel.remoteExtraMap != null) {
//            Object sourceDescObject = userChannel.remoteExtraMap.get(WKChannelExtras.sourceDesc);
//            if (sourceDescObject != null) {
//                sourceDesc = (String) sourceDescObject;
//            }
//        }
//        if (!TextUtils.isEmpty(sourceDesc)) {
//            wkVBinding.sourceFromTv.setText(sourceDesc);
//            wkVBinding.fromLayout.setVisibility(View.VISIBLE);
//        } else {
//            wkVBinding.fromLayout.setVisibility(View.GONE);
//        }
//
//        if (userChannel != null) {
//            status = userChannel.status;
//            if (userChannel.follow == 1) {
//                isFriend = true;
//            }
//            if (status == WKChannelStatus.statusBlacklist) {
//                isShowId = false;
//            }
//            if (userChannel.status == 2) {
//                wkVBinding.blacklistTv.setText(R.string.pull_out_black_list);
//            } else {
//                wkVBinding.blacklistTv.setText(R.string.push_black_list);
//            }
//        }
//        if (isFriend || isShowId) {
//            wkVBinding.identityLayout.setVisibility(View.VISIBLE);
//        } else {
//            wkVBinding.identityLayout.setVisibility(View.GONE);
//        }
//
//        wkVBinding.sendMsgBtn.setVisibility(isFriend ? View.VISIBLE : View.GONE);
//        wkVBinding.applyBtn.setVisibility(isFriend ? View.GONE : View.VISIBLE);
//        wkVBinding.deleteLayout.setVisibility(isFriend ? View.VISIBLE : View.GONE);
//        wkVBinding.blacklistDescTv.setVisibility(status == 2 ? View.VISIBLE : View.GONE);
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("user_detail_refresh_channel");
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("user_detail_refresh_channel1");
    }


    private void showImg() {
        String uri = WKApiConfig.getAvatarUrl(uid) + "?key=" + WKTimeUtils.getInstance().getCurrentMills();
        //查看大图
        List<Object> tempImgList = new ArrayList<>();
        List<ImageView> imageViewList = new ArrayList<>();
        imageViewList.add(wkVBinding.avatarView.imageView);
        tempImgList.add(WKApiConfig.getShowUrl(uri));
        int index = 0;
        WKDialogUtils.getInstance().showImagePopup(this, tempImgList, imageViewList, wkVBinding.avatarView.imageView, index, new ArrayList<>(), null, null);
        WKIM.getInstance().getChannelManager().updateAvatarCacheKey(uid, WKChannelType.PERSONAL, UUID.randomUUID().toString().replaceAll("-", ""));
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
}
