package com.chat.base.views.pwdview;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.chat.base.R;
import com.chat.base.ui.Theme;

/**
 * 2020-11-02 12:18
 * 数字密码弹框
 */
public class NumPwdDialog {
    private NumPwdDialog() {

    }

    private static class NumPwdDialogBinder {
        private final static NumPwdDialog dialog = new NumPwdDialog();
    }

    public static NumPwdDialog getInstance() {
        return NumPwdDialogBinder.dialog;
    }

    public void showNumPwdDialog(Context context, String title, String content, String remark, final IPwdInputResult iPwdInputResult) {
        final Dialog dialog = new Dialog(context, R.style.pop_dialog);
        dialog.setContentView(R.layout.wk_num_pwd_dialog_layout);
        Window win = dialog.getWindow();
        dialog.setCanceledOnTouchOutside(true);
        WindowManager.LayoutParams lp = win.getAttributes();
        DisplayMetrics d = context.getResources().getDisplayMetrics();
        lp.width = d.widthPixels;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        win.setGravity(Gravity.BOTTOM);
        win.setAttributes(lp);
        final NumPwdView pwdView = dialog.findViewById(R.id.numPwdLayout);
        pwdView.setOnFinishInput(() -> {
            String payPwd = pwdView.getNumPwd();
            if (!TextUtils.isEmpty(payPwd)) {
                iPwdInputResult.onResult(payPwd);
                dialog.dismiss();
            }
        });
        PwdView pwdDialog = pwdView.findViewById(R.id.pwdDialog);
        TextView titleTv = pwdDialog.findViewById(R.id.titleCenterTv);
        TextView remarkTv = pwdDialog.findViewById(R.id.remarkTv);
        TextView subtitleTv = pwdDialog.findViewById(R.id.subtitleTv);
        if (!TextUtils.isEmpty(title)) {
            titleTv.setText(title);
            titleTv.setVisibility(View.VISIBLE);
        } else titleTv.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(content)) {
            subtitleTv.setText(content);
            subtitleTv.setVisibility(View.VISIBLE);
        } else subtitleTv.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(remark)) {
            remarkTv.setText(remark);
            remarkTv.setVisibility(View.VISIBLE);
        } else remarkTv.setVisibility(View.GONE);
        pwdDialog.showPwdView();
        pwdDialog.setBottomTv(context.getString(R.string.str_forget_pwd), Theme.colorAccount, () -> {
            dialog.dismiss();
            iPwdInputResult.forgetPwd();
        });
        pwdDialog.findViewById(R.id.closeIv).setOnClickListener(view -> dialog.dismiss());

        dialog.show();
    }

    public interface IPwdInputResult {
        void onResult(String numPwd);

        void forgetPwd();
    }
}
