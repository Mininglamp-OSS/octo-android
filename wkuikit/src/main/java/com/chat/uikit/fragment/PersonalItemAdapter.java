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

package com.chat.uikit.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.endpoint.entity.PersonalInfoMenu;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.SwitchView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.uikit.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PersonalItemAdapter extends BaseQuickAdapter<PersonalInfoMenu, BaseViewHolder> {

    static final String SID_DARK_MODE = "dark_mode_toggle";

    PersonalItemAdapter(List<PersonalInfoMenu> list) {
        super(R.layout.item_frag_me_layout, list);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder holder, PersonalInfoMenu menu) {
        holder.setText(R.id.nameTv, menu.text);
        holder.setVisible(R.id.newVersionIv, menu.isNewVersionIv);

        boolean isDarkModeItem = SID_DARK_MODE.equals(menu.sid);
        String webLoginText = getContext().getString(R.string.web_login);
        boolean isWebLogin = menu.text.equals(webLoginText);

        // detail text (网页端 shows "已连接")
        TextView detailTv = holder.getView(R.id.detailTv);
        if (isWebLogin) {
            detailTv.setVisibility(View.VISIBLE);
            detailTv.setText(getContext().getString(R.string.str_connected));
        } else {
            detailTv.setVisibility(View.GONE);
        }

        SwitchView darkSwitch = holder.getView(R.id.darkModeSwitch);
        View arrowIv = holder.getView(R.id.arrowIv);
        if (isDarkModeItem) {
            darkSwitch.setVisibility(View.VISIBLE);
            arrowIv.setVisibility(View.GONE);
            darkSwitch.setOnCheckedChangeListener(null);
            darkSwitch.setChecked(Theme.getTheme().equals(Theme.DARK_MODE));
            darkSwitch.setOnCheckedChangeListener((view, checked) ->
                    Theme.setTheme(checked ? Theme.DARK_MODE : Theme.LIGHT_MODE));
        } else {
            darkSwitch.setVisibility(View.GONE);
            arrowIv.setVisibility(View.VISIBLE);
        }

        // grouped card corners
        int position = holder.getBindingAdapterPosition();
        int dataSize = getData().size();

        boolean isGroupEnd = isLastInGroup(position);
        boolean isGroupStart = isFirstInGroup(position);
        boolean isSingle = isGroupStart && isGroupEnd;

        float radius = AndroidUtilities.dp(12);
        float[] corners;
        if (isSingle) {
            corners = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
        } else if (isGroupStart) {
            corners = new float[]{radius, radius, radius, radius, 0, 0, 0, 0};
        } else if (isGroupEnd) {
            corners = new float[]{0, 0, 0, 0, radius, radius, radius, radius};
        } else {
            corners = new float[]{0, 0, 0, 0, 0, 0, 0, 0};
        }

        boolean isDark = Theme.isDark();

        View contentRow = holder.getView(R.id.contentRow);
        contentRow.setBackground(createPressedDrawable(corners, isDark));

        View dividerView = holder.getView(R.id.dividerView);
        dividerView.setVisibility(isGroupEnd ? View.GONE : View.VISIBLE);
        dividerView.setBackgroundColor(isDark ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5E5"));

        View bottomView = holder.getView(R.id.bottomView);
        bottomView.setVisibility(isGroupEnd && position < dataSize - 1 ? View.VISIBLE : View.GONE);
    }

    private boolean isLastInGroup(int position) {
        PersonalInfoMenu current = getItem(position);
        if (current == null) return true;
        if (SID_DARK_MODE.equals(current.sid)) return true;
        String webLoginText = getContext().getString(R.string.web_login);
        if (current.text.equals(webLoginText)) return true;
        int dataSize = getData().size();
        return position >= dataSize - 1;
    }

    private boolean isFirstInGroup(int position) {
        if (position <= 0) return true;
        return isLastInGroup(position - 1);
    }

    private StateListDrawable createPressedDrawable(float[] corners, boolean isDark) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(isDark ? Color.parseColor("#1C1C1E") : Color.WHITE);
        normal.setCornerRadii(corners);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(isDark ? Color.parseColor("#2C2C2E") : Color.parseColor("#F3F3F3"));
        pressed.setCornerRadii(corners);

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateList.addState(new int[]{}, normal);
        return stateList;
    }
}
