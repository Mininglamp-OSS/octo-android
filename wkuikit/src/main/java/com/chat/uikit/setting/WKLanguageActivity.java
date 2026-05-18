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
import android.widget.Button;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.language.WKLanguageType;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActLanguageLayoutBinding;

/**
 * 2020-12-09 15:31
 * 多语言
 */
public class WKLanguageActivity extends WKBaseActivity<ActLanguageLayoutBinding> {

    int selectedLanguage = 0;

    @Override
    protected ActLanguageLayoutBinding getViewBinding() {
        return ActLanguageLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.language);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        return getString(R.string.str_save);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();
        WKMultiLanguageUtil.getInstance().updateLanguage(selectedLanguage);
        EndpointManager.getInstance().invoke("main_show_home_view",0);
        finish();
    }

    @Override
    protected void initView() {
        selectedLanguage = WKMultiLanguageUtil.getInstance().getLanguageType();
        setSelectedLanguage();
    }

    @Override
    protected void initListener() {
        wkVBinding.autoLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_FOLLOW_SYSTEM;
            setSelectedLanguage();
        });
        wkVBinding.simplifiedChineseLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED;
            setSelectedLanguage();
        });
        wkVBinding.englishLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_EN;
            setSelectedLanguage();
        });
    }


    private void setSelectedLanguage() {
        if (selectedLanguage == WKLanguageType.LANGUAGE_FOLLOW_SYSTEM) {
            wkVBinding.autoIv.setVisibility(View.VISIBLE);
            wkVBinding.englishIv.setVisibility(View.INVISIBLE);
            wkVBinding.simplifiedChineseIv.setVisibility(View.INVISIBLE);
        } else if (selectedLanguage == WKLanguageType.LANGUAGE_EN) {
            wkVBinding.autoIv.setVisibility(View.INVISIBLE);
            wkVBinding.englishIv.setVisibility(View.VISIBLE);
            wkVBinding.simplifiedChineseIv.setVisibility(View.INVISIBLE);
        } else if (selectedLanguage == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED) {
            wkVBinding.autoIv.setVisibility(View.INVISIBLE);
            wkVBinding.englishIv.setVisibility(View.INVISIBLE);
            wkVBinding.simplifiedChineseIv.setVisibility(View.VISIBLE);
        }
    }
}
