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

package com.chat.scan;

import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.scan.databinding.ActScanOtherResultLayoutBinding;

/**
 * 2020-04-19 18:25
 * 扫描其他内容
 */
public class WKScanOtherResultActivity extends WKBaseActivity<ActScanOtherResultLayoutBinding> {
    @Override
    protected ActScanOtherResultLayoutBinding getViewBinding() {
        return ActScanOtherResultLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.wk_scan_module_other_result);
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        String result = getIntent().getStringExtra("result");
        wkVBinding.resultTv.setText(result);
    }

    @Override
    protected void initListener() {

    }
}
