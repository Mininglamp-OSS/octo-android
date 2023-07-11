package com.chat.base.views;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.R;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 2020-12-02 15:54
 * 普通弹框
 */
public class NormalDialogView extends CenterPopupView {
    public String content;
    public String cancelStr;
    public String sureStr;
    public String title;
    public IClick iClick;

    public NormalDialogView(@NonNull Context context, String title, String content, String sureStr, String cancelStr, final IClick iClick) {
        super(context);
        this.iClick = iClick;
        this.title = title;
        this.sureStr = sureStr;
        this.cancelStr = cancelStr;
        this.content = content;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView titleTv = findViewById(R.id.titleCenterTv);
        TextView cancelTv = findViewById(R.id.cancelTv);
        TextView sureTv = findViewById(R.id.sureTv);
        if (!TextUtils.isEmpty(title)) titleTv.setText(title);
        if (!TextUtils.isEmpty(sureStr)) sureTv.setText(sureStr);
        if (!TextUtils.isEmpty(cancelStr)) cancelTv.setText(cancelStr);
        TextView contentTv = findViewById(R.id.contentTv);
        contentTv.setText(content);
        findViewById(R.id.cancelTv).setOnClickListener(view -> {
            dismiss();
            if (iClick != null)
                iClick.onClick(0);
        });
        findViewById(R.id.sureTv).setOnClickListener(view -> {
            dismiss();
            if (iClick != null)
                iClick.onClick(1);
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.wk_normal_dialog_view;
    }

    public interface IClick {
        void onClick(int index);
    }
}
