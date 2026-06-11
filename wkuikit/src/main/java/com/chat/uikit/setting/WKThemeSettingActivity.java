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

package com.chat.uikit.setting;

import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.ui.Theme;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActDarkSettingLayoutBinding;

public class WKThemeSettingActivity extends WKBaseActivity<ActDarkSettingLayoutBinding> {

    private String selectedTheme;

    @Override
    protected ActDarkSettingLayoutBinding getViewBinding() {
        return ActDarkSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.appearance);
    }

    @Override
    protected void initView() {
        selectedTheme = Theme.getTheme();
        updateRadioState();
    }

    @Override
    protected void initListener() {
        wkVBinding.systemLayout.setOnClickListener(v -> {
            selectedTheme = Theme.DEFAULT_MODE;
            updateRadioState();
            Theme.setTheme(selectedTheme);
        });
        wkVBinding.lightLayout.setOnClickListener(v -> {
            selectedTheme = Theme.LIGHT_MODE;
            updateRadioState();
            Theme.setTheme(selectedTheme);
        });
        wkVBinding.darkLayout.setOnClickListener(v -> {
            selectedTheme = Theme.DARK_MODE;
            updateRadioState();
            Theme.setTheme(selectedTheme);
        });
    }

    private void updateRadioState() {
        wkVBinding.systemRadio.setBackgroundResource(
                Theme.DEFAULT_MODE.equals(selectedTheme) ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
        wkVBinding.lightRadio.setBackgroundResource(
                Theme.LIGHT_MODE.equals(selectedTheme) ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
        wkVBinding.darkRadio.setBackgroundResource(
                Theme.DARK_MODE.equals(selectedTheme) ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
    }
}
