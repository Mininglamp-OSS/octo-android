package com.chat.uikit.chat;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatSettingCellMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActChatPersonalLayoutBinding;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.user.UserDetailActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2019-12-08 12:26
 * 个人会话资料页面
 */
public class ChatPersonalActivity extends WKBaseActivity<ActChatPersonalLayoutBinding> {
    private String channelId;
    private WKChannel channel;

    @Override
    protected ActChatPersonalLayoutBinding getViewBinding() {
        return ActChatPersonalLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.chat_info);
    }

    @Override
    protected void initPresenter() {
        channelId = getIntent().getStringExtra("channelId");
    }

    @Override
    protected void initView() {
//        int w = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(10);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        View view = (View) EndpointManager.getInstance().invoke("msg_remind_view", new ChatSettingCellMenu(channelId, WKChannelType.PERSONAL, wkVBinding.msgRemindLayout));
        if (view != null) {
            wkVBinding.msgRemindLayout.removeAllViews();
            wkVBinding.msgRemindLayout.addView(view);
        }
        View findMsgView = (View) EndpointManager.getInstance().invoke("find_msg_view", new ChatSettingCellMenu(channelId, WKChannelType.PERSONAL, wkVBinding.findContentLayout));
        if (findMsgView != null) {
            wkVBinding.findContentLayout.removeAllViews();
            wkVBinding.findContentLayout.addView(findMsgView);
        }

        View msgReceiptView = (View) EndpointManager.getInstance().invoke("msg_receipt_view", new ChatSettingCellMenu(channelId, WKChannelType.PERSONAL, wkVBinding.msgSettingLayout));
        if (msgReceiptView != null) {
            wkVBinding.msgSettingLayout.removeAllViews();
            wkVBinding.msgSettingLayout.addView(msgReceiptView);
        }
        View msgPrivacyLayout = (View) EndpointManager.getInstance().invoke("chat_setting_msg_privacy", new ChatSettingCellMenu(channelId, WKChannelType.PERSONAL, wkVBinding.msgSettingLayout));
        if (msgPrivacyLayout != null) {
            wkVBinding.msgSettingLayout.addView(msgPrivacyLayout);
        }

        View chatPwdView = (View) EndpointManager.getInstance().invoke("chat_pwd_view", new ChatSettingCellMenu(channelId, WKChannelType.PERSONAL, wkVBinding.msgSettingLayout));
        if (chatPwdView != null) {
            wkVBinding.chatPwdView.addView(chatPwdView);
        }

    }

    @Override
    protected void initListener() {
        EndpointManager.getInstance().setMethod("chat_personal_activity", EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                if (channelId.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                    finish();
                }
            }
            return null;
        });

        //免打扰
        wkVBinding.muteSwitchView.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                FriendModel.getInstance().updateUserSetting(channelId, "mute", b ? 1 : 0, (code, msg) -> {
                    if (code != HttpResponseCode.success) {
                        wkVBinding.muteSwitchView.setChecked(!b);
                        showToast(msg);
                    }
                });
            }
        });
        //置顶
        wkVBinding.stickSwitchView.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed())
                FriendModel.getInstance().updateUserSetting(channelId, "top", b ? 1 : 0, (code, msg) -> {
                    if (code != HttpResponseCode.success) {
                        wkVBinding.stickSwitchView.setChecked(!b);
                        showToast(msg);
                    }
                });
        });
        wkVBinding.clearChatMsgLayout.setOnClickListener(v -> {
            String content = String.format(getString(R.string.clear_chat_personal_msg_dialog), channel == null ? "" : channel.channelName);
            showDialog(content, index -> {
                if (index == 1) {
                    MsgModel.getInstance().offsetMsg(channelId, WKChannelType.PERSONAL, null);
                    WKIM.getInstance().getMsgManager().clearWithChannel(channelId, WKChannelType.PERSONAL);
                    showToast(R.string.cleared);
                }
            });

        });
        SingleClickUtil.onSingleClick(wkVBinding.addIv, view1 -> {
            Intent intent = new Intent(ChatPersonalActivity.this, ChooseContactsActivity.class);
            intent.putExtra("unSelectUids", channelId);
            intent.putExtra("isIncludeUids", true);
            chooseCardResultLac.launch(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.avatarView, view1 -> {
            Intent intent = new Intent(ChatPersonalActivity.this, UserDetailActivity.class);
            intent.putExtra("uid", channelId);
            startActivity(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.reportLayout, view1 -> {
            Intent intent = new Intent(this, WKWebViewActivity.class);
            intent.putExtra("channelType", WKChannelType.PERSONAL);
            intent.putExtra("channelID", channelId);
            intent.putExtra("url", WKApiConfig.baseWebUrl + "report.html");
            startActivity(intent);
        });
    }

    @Override
    protected void initData() {
        super.initData();

        if (WKSystemAccount.isSystemAccount(channelId)) {
            Intent intent = new Intent(this, UserDetailActivity.class);
            intent.putExtra("uid", channelId);
            startActivity(intent);
            finish();
            return;
        }
        channel = WKIM.getInstance().getChannelManager().getChannel(channelId, WKChannelType.PERSONAL);
        if (channel != null) {
            wkVBinding.avatarView.showAvatar(channel.channelID, channel.channelType, false);
            wkVBinding.nameTv.setText(TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark);
            wkVBinding.muteSwitchView.setChecked(channel.mute == 1);
            wkVBinding.stickSwitchView.setChecked(channel.top == 1);

        }
    }

    ActivityResultLauncher<Intent> chooseCardResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            finish();
        }
    });

    @Override
    public void finish() {
        super.finish();
        EndpointManager.getInstance().remove("chat_personal_activity");
    }
}
