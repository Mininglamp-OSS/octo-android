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

package com.chat.uikit.robot;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;
import com.chat.uikit.robot.entity.WKRobotMenuEntity;
import com.xinbida.wukongim.entity.WKChannelType;

public class RobotMenuAdapter extends BaseQuickAdapter<WKRobotMenuEntity, BaseViewHolder> {

    public RobotMenuAdapter() {
        super(R.layout.item_robot_menu_layout);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, WKRobotMenuEntity entity) {
        baseViewHolder.setText(R.id.cmdTv, entity.cmd);
        baseViewHolder.setText(R.id.remarkTv, entity.remark);
        AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
        avatarView.setSize(30);
        avatarView.showAvatar(entity.robot_id, WKChannelType.PERSONAL);
    }
}
