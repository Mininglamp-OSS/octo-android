package com.chat.base.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputFilter;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.chat.base.R;
import com.chat.base.utils.StringUtils;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 2020-09-18 19:43
 * 输入提示框
 */
@SuppressLint("ViewConstructor")
public class InputDialogView extends CenterPopupView {
    private final IClick iClick;
    private final String oldStr;
    private final String hideStr;
    private final int maxLength;

    public InputDialogView(@NonNull Context context, String oldStr, String hideStr, int maxLength, IClick iClick) {
        super(context);
        this.oldStr = oldStr;
        this.hideStr = hideStr;
        this.maxLength = maxLength;
        this.iClick = iClick;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        EditText remarkEt = findViewById(R.id.remarkEt);
        remarkEt.setFilters(new InputFilter[]{StringUtils.getInputFilter(maxLength)});
        remarkEt.setHint(hideStr);
        remarkEt.setText(oldStr);
        if (!TextUtils.isEmpty(oldStr)) {
            remarkEt.setSelection(oldStr.length());
        }
        findViewById(R.id.cancelTv).setOnClickListener(view -> dismiss());
        findViewById(R.id.sureTv).setOnClickListener(view -> {
            String remark = remarkEt.getText().toString();
            iClick.onResult(remark);
            dismiss();
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.input_dialog_layout;
    }

    public interface IClick {
        void onResult(String content);
    }

}
