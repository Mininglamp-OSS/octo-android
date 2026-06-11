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

package com.chat.uikit.contacts;

import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActContactsLayoutBinding;
import com.chat.uikit.fragment.ContactsFragment;

public class ContactsActivity extends WKBaseActivity<ActContactsLayoutBinding> {

    @Override
    protected ActContactsLayoutBinding getViewBinding() {
        return ActContactsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.contacts_title);
    }

    @Override
    protected void initView() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new ContactsFragment())
                .commit();
    }

    @Override
    protected void initListener() {
    }
}
