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

package com.chat.uikit.space;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;

public class SpaceListAdapter extends BaseQuickAdapter<SpaceEntity, BaseViewHolder> {

    private String currentSpaceId = "";

    public SpaceListAdapter() {
        super(R.layout.item_space_list);
    }

    public void setCurrentSpaceId(String spaceId) {
        this.currentSpaceId = spaceId != null ? spaceId : "";
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, SpaceEntity entity) {
        TextView avatarTv = holder.getView(R.id.avatarTv);
        String initial = entity.name != null && !entity.name.isEmpty()
                ? entity.name.substring(0, 1).toUpperCase() : "S";
        avatarTv.setText(initial);
        holder.setText(R.id.nameTv, entity.name);

        // 当前选中的 Space 显示勾选标记
        boolean isSelected = entity.space_id != null && entity.space_id.equals(currentSpaceId);
        holder.setVisible(R.id.checkTv, isSelected);

        // 链接图标点击：直接复制 Space 列表返回的邀请码
        ImageView linkIv = holder.getView(R.id.linkIv);
        linkIv.setOnClickListener(v -> {
            Context ctx = getContext();
            if (entity.invite_code != null && !entity.invite_code.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("invite", entity.invite_code));
                WKToastUtils.getInstance().showToastNormal(
                        ctx.getString(R.string.space_invite_code_copied));
            }
        });
    }
}
