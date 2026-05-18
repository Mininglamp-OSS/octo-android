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
