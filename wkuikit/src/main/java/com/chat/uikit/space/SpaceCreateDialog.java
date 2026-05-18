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

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;

public class SpaceCreateDialog extends Dialog {

    public interface OnSpaceCreatedListener {
        void onCreated(SpaceEntity space);
    }

    private OnSpaceCreatedListener listener;

    public SpaceCreateDialog(@NonNull Context context) {
        super(context);
    }

    public void setOnSpaceCreatedListener(OnSpaceCreatedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_space_create);

        // 设置透明背景让圆角可见
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.horizontalMargin = 0.08f;
            getWindow().setAttributes(params);
        }

        EditText nameEt = findViewById(R.id.nameEt);
        EditText descEt = findViewById(R.id.descEt);
        TextView charCount = findViewById(R.id.charCount);
        View closeBtn = findViewById(R.id.closeBtn);
        View cancelBtn = findViewById(R.id.cancelBtn);
        View createBtn = findViewById(R.id.createBtn);

        // 字数统计
        descEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                charCount.setText(s.length() + "/200");
            }
        });

        closeBtn.setOnClickListener(v -> dismiss());
        cancelBtn.setOnClickListener(v -> dismiss());
        createBtn.setOnClickListener(v -> {
            String name = nameEt.getText().toString().trim();
            String desc = descEt.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                WKToastUtils.getInstance().showToastNormal(getContext().getString(R.string.space_name_empty));
                return;
            }
            createBtn.setEnabled(false);
            SpaceModel.getInstance().createSpace(name, desc, new SpaceModel.ISpaceListener() {
                @Override
                public void onResult(SpaceEntity space) {
                    createBtn.setEnabled(true);
                    dismiss();
                    if (listener != null) {
                        listener.onCreated(space);
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    createBtn.setEnabled(true);
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        });
    }
}
