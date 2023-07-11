package com.chat.base.views;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.R;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 2020-12-02 15:54
 * 普通单按钮弹框
 */
public class NormalSingleBtnDialogView extends CenterPopupView {
    public String content;
    public String title;
    public String btnStr;
    public IClick iClick;

    public NormalSingleBtnDialogView(@NonNull Context context, String title, String content, String btnStr, final IClick iClick) {
        super(context);
        this.iClick = iClick;
        this.title = title;
        this.content = content;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView titleTv = findViewById(R.id.titleCenterTv);
        TextView btnTv = findViewById(R.id.btnTv);
        if (!TextUtils.isEmpty(btnStr))
            btnTv.setText(btnStr);
        if (!TextUtils.isEmpty(title)) titleTv.setText(title);
        TextView contentTv = findViewById(R.id.contentTv);
        contentTv.setText(content);
        findViewById(R.id.btnTv).setOnClickListener(view -> {
            dismiss();
            if (iClick != null)
                iClick.onClick(0);
        });

    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.wk_normal_dialog_single_btn_view;
    }

    public interface IClick {
        void onClick(int index);
    }
}
