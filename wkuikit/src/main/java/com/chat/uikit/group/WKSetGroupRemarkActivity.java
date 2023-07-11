package com.chat.uikit.group;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.databinding.ActUpdateGroupRemarkLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 4/2/21 2:47 PM
 * 修改群备注
 */
public class WKSetGroupRemarkActivity extends WKBaseActivity<ActUpdateGroupRemarkLayoutBinding> {
    private String groupNo;
    WKChannel channel;

    @Override
    protected ActUpdateGroupRemarkLayoutBinding getViewBinding() {
        return ActUpdateGroupRemarkLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText("");
    }

    @Override
    protected void initPresenter() {
        groupNo = getIntent().getStringExtra("groupNo");
    }

    @Override
    protected void initView() {
        wkVBinding.saveBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.enter.setTextColor(Theme.colorAccount);
        wkVBinding.remarkEt.setFilters(new InputFilter[]{StringUtils.getInputFilter(40)});
        channel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
        if (channel != null) {
            if (!TextUtils.isEmpty(channel.channelRemark)) {
                wkVBinding.remarkEt.setText(channel.channelRemark);
                wkVBinding.remarkEt.setSelection(channel.channelRemark.length());
            }
            wkVBinding.avatarView.showAvatar(channel);
            wkVBinding.groupNameTv.setText(channel.channelName);
        }
        SoftKeyboardUtils.getInstance().showSoftKeyBoard(WKSetGroupRemarkActivity.this, wkVBinding.remarkEt);
    }

    @Override
    protected void initListener() {
        wkVBinding.enter.setOnClickListener(v -> {
            wkVBinding.remarkEt.setText(channel.channelName);
            wkVBinding.remarkEt.setSelection(channel.channelName.length());
        });
        wkVBinding.groupNameTv.setMaxWidth(AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(135));
        wkVBinding.remarkEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if ((TextUtils.isEmpty(s.toString()) && TextUtils.isEmpty(channel.channelRemark)) || channel.channelRemark.equals(s.toString())) {
                    wkVBinding.saveBtn.setAlpha(0.2f);
                    wkVBinding.saveBtn.setEnabled(false);
                } else {
                    wkVBinding.saveBtn.setAlpha(1f);
                    wkVBinding.saveBtn.setEnabled(true);
                }
            }
        });
        wkVBinding.saveBtn.setOnClickListener(v -> {
            String remark = wkVBinding.remarkEt.getText().toString();
            GroupModel.getInstance().updateGroupSetting(groupNo, "remark", remark, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    showToast(msg);
                } else {
                    finish();
                }
            });
        });
    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.remarkEt);
    }
}
