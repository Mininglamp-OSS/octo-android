package com.chat.uikit.search;

import android.text.SpannableString;
import android.text.TextUtils;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
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
        baseViewHolder.setText(R.id.fromNameTv, TextUtils.isEmpty(msg.getFrom().channelRemark) ? msg.getFrom().channelName : msg.getFrom().channelRemark);
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0,getItemCount());
    }
}
