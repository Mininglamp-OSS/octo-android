package com.chat.base.emoji;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.R;
import com.chat.base.ui.components.FilterImageView;
import com.chat.base.utils.AndroidUtilities;

import java.util.List;

/**
 * 2019-11-13 15:51
 * emoji表情适配器
 */
public class EmojiAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private final int width;

    public EmojiAdapter(@Nullable List<String> data, int _width) {
        super(R.layout.item_emoji_layout, data);
        this.width = _width;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, String item) {
        FilterImageView imageView = helper.getView(R.id.emojiIv);
        imageView.setStrokeWidth(0);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(30), AndroidUtilities.dp( 30));
        layoutParams.setMargins(width / 14, width / 14, width / 14, width / 14);
        imageView.setLayoutParams(layoutParams);
        imageView.setImageDrawable(EmojiManager.getInstance().getDrawable(getContext(), item));
    }
}
