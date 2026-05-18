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
public class EmojiAdapter extends BaseQuickAdapter<EmojiEntry, BaseViewHolder> {
    // : mutable so EmojiFragment can refresh on unfold while panel is visible.
    // Was `final int width`; made writable through setWidth() + convert() reads live value.
    private int width;

    public EmojiAdapter(@Nullable List<EmojiEntry> data, int _width) {
        super(R.layout.item_emoji_layout, data);
        this.width = _width;
    }

    /**
     *  · refresh the per-item margin to match the new pane width on unfold/fold.
     * Caller must invoke {@link #notifyDataSetChanged()} afterwards to rebind visible cells.
     */
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, EmojiEntry item) {
        FilterImageView imageView = helper.getView(R.id.emojiIv);
        imageView.setStrokeWidth(0);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(30), AndroidUtilities.dp( 30));
        layoutParams.setMargins(width / 14, width / 14, width / 14, width / 14);
        imageView.setLayoutParams(layoutParams);
        imageView.setImageDrawable(EmojiManager.getInstance().getDrawable(getContext(), item.getText()));
    }
}
