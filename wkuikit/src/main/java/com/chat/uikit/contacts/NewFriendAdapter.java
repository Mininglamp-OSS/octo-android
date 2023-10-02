package com.chat.uikit.contacts;


import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.entity.NewFriendEntity;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-30 12:11
 * 新朋友
 */
public class NewFriendAdapter extends BaseQuickAdapter<NewFriendEntity, BaseViewHolder> {
    IDelete iDelete;

    NewFriendAdapter(@Nullable List<NewFriendEntity> data, IDelete iDelete) {
        super(R.layout.item_new_friend_layout, data);
        this.iDelete = iDelete;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, NewFriendEntity item) {
        helper.setText(R.id.nameTv, item.apply_name);
        helper.setText(R.id.remarkTv, !TextUtils.isEmpty(item.remark) ? item.remark : getContext().getString(R.string.request_add_frined));
        helper.setGone(R.id.statusTv, item.status == 0);
        helper.setGone(R.id.agreeBtn, item.status == 1);
        showDialog(helper.getView(R.id.contentLayout), item);
        AvatarView avatarView = helper.getView(R.id.avatarView);
        avatarView.showAvatar(item.apply_uid, WKChannelType.PERSONAL);
        Button button = helper.getView(R.id.agreeBtn);
        button.getBackground().setTint(Theme.colorAccount);
    }


    private void showDialog(View view, NewFriendEntity item) {
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getContext().getString(R.string.base_delete), R.mipmap.msg_delete, () -> iDelete.onDelete(item)));
        WKDialogUtils.getInstance().setViewLongClickPopup(view,list);
    }

    interface IDelete {
        void onDelete(NewFriendEntity item);
    }
}
