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

package com.chat.uikit.search;

import android.text.SpannableString;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.xinbida.wukongim.entity.WKChannelSearchResult;

import org.jetbrains.annotations.NotNull;

/**
 * 2020-05-04 17:22
 * 搜索频道
 */
public class SearchChannelAdapter extends BaseQuickAdapter<WKChannelSearchResult, BaseViewHolder> {
    private String searchKey;

    public SearchChannelAdapter() {
        super(R.layout.item_search_channel_layout);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, WKChannelSearchResult result) {
        AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
        avatarView.showAvatar(result.wkChannel);
        if (TextUtils.isEmpty(searchKey)) {
            baseViewHolder.setText(R.id.nameTv, TextUtils.isEmpty(result.wkChannel.channelRemark) ? result.wkChannel.channelName : result.wkChannel.channelRemark);
        } else {
            String name = result.wkChannel.channelRemark;
            if (TextUtils.isEmpty(name))
                name = result.wkChannel.channelName;
            SpannableString key = StringUtils.findSearch(Theme.colorAccount, name, searchKey);
            baseViewHolder.setText(R.id.nameTv, key);
        }
        if (!TextUtils.isEmpty(result.containMemberName)) {
            SpannableString key = StringUtils.findSearch(Theme.colorAccount, getContext().getString(R.string.contian) + result.containMemberName, searchKey);
            baseViewHolder.setText(R.id.contentTv, key);
        }
        baseViewHolder.setGone(R.id.contentTv, TextUtils.isEmpty(result.containMemberName));
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
       notifyItemRangeChanged(0,getItemCount());
    }
}
