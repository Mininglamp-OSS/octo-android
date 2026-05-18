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

package com.chat.uikit.thread;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.chat.uikit.R;

public class CreateThreadDialog extends Dialog {

    private final String sourceMessageId;
    private final ICreateThreadListener listener;

    public CreateThreadDialog(Context context, String sourceMessageId, ICreateThreadListener listener) {
        super(context, com.chat.base.R.style.AlertDialog);
        this.sourceMessageId = sourceMessageId;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_create_thread);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        EditText nameEt = findViewById(R.id.threadNameEt);
        TextView cancelBtn = findViewById(R.id.cancelBtn);
        TextView confirmBtn = findViewById(R.id.confirmBtn);

        cancelBtn.setOnClickListener(v -> dismiss());
        confirmBtn.setOnClickListener(v -> {
            String name = nameEt.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                return;
            }
            dismiss();
            listener.onCreate(name, sourceMessageId);
        });
    }

    public interface ICreateThreadListener {
        void onCreate(String name, String sourceMessageId);
    }
}
