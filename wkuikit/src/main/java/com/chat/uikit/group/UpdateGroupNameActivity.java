package com.chat.uikit.group;

import android.text.TextUtils;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActUpdateGroupNameLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.Objects;

/**
 * 2020-01-29 10:24
 * 修改群名称
 */
public class UpdateGroupNameActivity extends WKBaseActivity<ActUpdateGroupNameLayoutBinding> {

    String groupNo;
    WKChannel channel;

    @Override
    protected ActUpdateGroupNameLayoutBinding getViewBinding() {
        return ActUpdateGroupNameLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_card);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        String name = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();

        if (channel == null || TextUtils.isEmpty(channel.channelID) || TextUtils.isEmpty(name))
            return;

        if (TextUtils.equals(name, channel.channelName)) {
            finish();
        }
        channel.channelName = name;

        showTitleRightLoading();
        GroupModel.getInstance().updateGroupInfo(channel.channelID, "name", name, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                WKIM.getInstance().getChannelManager().updateName(channel.channelID, WKChannelType.GROUP, name);
                finish();
            } else {
                hideTitleRightLoading();
                showToast(msg);
            }
        });
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.save);
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("groupNo");
        if (groupNo != null) {
            channel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
            wkVBinding.nameEt.setText(channel.channelName);
            wkVBinding.nameEt.setSelection(channel.channelName.length());
        }
        SoftKeyboardUtils.getInstance().showSoftKeyBoard(UpdateGroupNameActivity.this, wkVBinding.nameEt);
    }

    @Override
    protected void initListener() {

    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.nameEt);
    }
}
