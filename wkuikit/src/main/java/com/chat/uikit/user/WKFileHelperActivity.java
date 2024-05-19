package com.chat.uikit.user;

import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActFileHelperLayoutBinding;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 1/29/21 11:30 AM
 * 系统文件助手详情
 */
public class WKFileHelperActivity extends WKBaseActivity<ActFileHelperLayoutBinding> {
    @Override
    protected ActFileHelperLayoutBinding getViewBinding() {
        return ActFileHelperLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText("");
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        wkVBinding.appIdNumLeftTv.setText(String.format(getString(R.string.app_idnum), getString(R.string.app_name)));
        wkVBinding.appIdNumTv.setText(WKSystemAccount.system_file_helper_short_no);
        wkVBinding.avatarView.setSize(70);
        wkVBinding.avatarView.showAvatar(WKSystemAccount.system_file_helper, WKChannelType.PERSONAL);
        SingleClickUtil.onSingleClick(wkVBinding.sendMsgBtn, v -> WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, WKSystemAccount.system_file_helper, WKChannelType.PERSONAL, 0, true)));
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarView.setOnClickListener(v -> showImg());
    }

    private void showImg() {
        String uri = WKApiConfig.getAvatarUrl(WKSystemAccount.system_file_helper) + "?key=" + WKTimeUtils.getInstance().getCurrentMills();
        List<Object> tempImgList = new ArrayList<>();
        List<ImageView> imageViewList = new ArrayList<>();
        imageViewList.add(wkVBinding.avatarView.imageView);
        tempImgList.add(WKApiConfig.getShowUrl(uri));
        int index = 0;
        WKDialogUtils.getInstance().showImagePopup(this, tempImgList, imageViewList, wkVBinding.avatarView.imageView, index, new ArrayList<>(), null, null);

    }
}
