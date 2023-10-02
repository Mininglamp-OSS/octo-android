package com.chat.uikit.group;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupQrLayoutBinding;
import com.chat.uikit.group.service.GroupContract;
import com.chat.uikit.group.service.GroupPresenter;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-04-06 22:30
 * 群聊二维码名片
 */
public class GroupQrActivity extends WKBaseActivity<ActGroupQrLayoutBinding> implements GroupContract.GroupView {

    private GroupPresenter presenter;
    String groupId;

    @Override
    protected ActGroupQrLayoutBinding getViewBinding() {
        return ActGroupQrLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_qr);
    }

    @Override
    protected void initPresenter() {
        presenter = new GroupPresenter(this);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_other;
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.save_img), R.mipmap.msg_download, () -> {
            Bitmap bitmap = ImageUtils.getInstance().loadBitmapFromView(wkVBinding.shadowLayout);
            ImageUtils.getInstance().saveBitmap(this, bitmap, true, path -> showToast(R.string.saved_album));
        }));
        ImageView rightIV = findViewById(R.id.titleRightIv);
        WKDialogUtils.getInstance().showScreenPopup(rightIV,  list);
    }

    @Override
    protected void initView() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.avatarView.setSize(45);
    }

    @Override
    protected void initListener() {
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("group_qr_channel_refresh", (channel, isEnd) -> getGroupInfo());
    }

    @Override
    protected void initData() {
        super.initData();
        groupId = getIntent().getStringExtra("groupId");
        getGroupInfo();
    }

    private void getGroupInfo() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
        if (channel != null) {
            wkVBinding.nameTv.setText(channel.channelName);
            wkVBinding.avatarView.showAvatar(groupId, channel.channelType, channel.avatarCacheKey);
            if (channel.invite == 1) {
                wkVBinding.qrTv.setVisibility(View.INVISIBLE);
                wkVBinding.unusedTv.setVisibility(View.VISIBLE);
                wkVBinding.qrIv.setImageResource(R.mipmap.icon_no_qr);
            } else {
                wkVBinding.unusedTv.setVisibility(View.INVISIBLE);
                wkVBinding.qrTv.setVisibility(View.VISIBLE);
                presenter.getQrData(groupId);
            }
        } else {
            presenter.getQrData(groupId);
        }
    }

    @Override
    public void onGroupInfo(ChannelInfoEntity groupEntity) {

    }

    @Override
    public void onRefreshGroupSetting(String key, int value) {

    }

    @Override
    public void setQrData(int day, String qrcode, String expire) {
        if (TextUtils.isEmpty(qrcode)) {
            wkVBinding.qrIv.setImageResource(R.mipmap.icon_no_qr);
        } else {
            Bitmap mBitmap = (Bitmap) EndpointManager.getInstance().invoke("create_qrcode", qrcode);
//            Bitmap mBitmap = CodeUtils.createQRCode(qrcode, 400, null);
            wkVBinding.qrIv.setImageBitmap(mBitmap);
            String content = String.format(getString(R.string.group_qr_desc), day, expire);
            wkVBinding.qrTv.setText(content);
        }
    }

    @Override
    public void setMyGroups(List<GroupEntity> list) {

    }

    @Override
    public void showError(String msg) {

    }

    @Override
    public void hideLoading() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("group_qr_channel_refresh");
    }
}
