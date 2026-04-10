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
