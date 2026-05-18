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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.external.ExternalSourceResolver;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.xinbida.wukongim.entity.WKMsg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 2020-08-30 18:46
 * 搜索消息结果
 */
public class SearchMsgResultAdapter extends BaseQuickAdapter<WKMsg, BaseViewHolder> {
    /**
     * 外部 Space 后缀灰紫色（ / 对齐  消息气泡 &
     * WKChatBaseProvider.appendExternalSpaceSuffix 的染色规范）。
     */
    private static final int EXTERNAL_SPACE_SUFFIX_COLOR = 0xFF8B5CF6;

    private String searchKey;

    public SearchMsgResultAdapter(String searchKey, @Nullable List<WKMsg> data) {
        super(R.layout.item_search_msg_result_layout, data);
        this.searchKey = searchKey;
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, WKMsg msg) {
        if (!TextUtils.isEmpty(msg.baseContentMsgModel.getDisplayContent())) {
            SpannableString key = StringUtils.findSearch(Theme.colorAccount, msg.baseContentMsgModel.getDisplayContent(), searchKey);
            baseViewHolder.setText(R.id.contentTv, key);
        } else {
            baseViewHolder.setText(R.id.contentTv, "");
        }
        AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
        avatarView.showAvatar(msg.getFrom());
        //消息时间
        baseViewHolder.setText(R.id.timeTv, WKTimeUtils.getInstance().getTimeString(msg.timestamp * 1000));

        // : 会话内搜索消息的 fromNameTv 也要外部标识，避免「在群里搜消息」时
        // 只看得到昵称却看不出发送者跨 Space。复用消息气泡已做好的
        // ExternalSourceResolver 优先级链 (msg-level home_space_id → legacy
        // is_external → channel-level fallback)，viewer 从 current_space_id sp 取。
        String baseName = TextUtils.isEmpty(msg.getFrom().channelRemark)
                ? msg.getFrom().channelName : msg.getFrom().channelRemark;
        String viewerSpaceId = WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id");
        String externalSpaceSuffix = ExternalSourceResolver.resolveSourceSpaceName(msg, viewerSpaceId);
        if (TextUtils.isEmpty(externalSpaceSuffix)) {
            baseViewHolder.setText(R.id.fromNameTv, baseName == null ? "" : baseName);
        } else {
            SpannableStringBuilder builder = new SpannableStringBuilder(baseName == null ? "" : baseName);
            appendExternalSpaceSuffix(builder, externalSpaceSuffix);
            baseViewHolder.setText(R.id.fromNameTv, builder);
        }
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0,getItemCount());
    }

    private static void appendExternalSpaceSuffix(SpannableStringBuilder builder, String spaceName) {
        int start = builder.length();
        builder.append(" @").append(spaceName);
        builder.setSpan(
                new ForegroundColorSpan(EXTERNAL_SPACE_SUFFIX_COLOR),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }
}
