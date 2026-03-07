package com.chat.uikit.space;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.chat.base.net.HttpResponseCode;
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

        EditText nameEt = findViewById(R.id.nameEt);
        EditText descEt = findViewById(R.id.descEt);
        Button cancelBtn = findViewById(R.id.cancelBtn);
        Button createBtn = findViewById(R.id.createBtn);

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
