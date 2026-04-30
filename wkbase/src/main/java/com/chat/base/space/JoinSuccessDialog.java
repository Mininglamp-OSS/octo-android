package com.chat.base.space;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.R;
import com.chat.base.utils.WKToastUtils;

/**
 * 跨 Space 加群成功 — 两行 Dialog + 紫色「切换过去」按钮。
 *
 * <p>对齐 dmwork-web {@code showJoinSuccessToast}（PR#1068，YUJ-100/YUJ-106）的跨 Space 分支。
 * 同 Space 场景直接走系统 {@link WKToastUtils}，不弹 Dialog。
 */
public final class JoinSuccessDialog {

    /** 点击「切换过去」按钮时的回调。*/
    public interface OnSwitchListener {
        /**
         * 用户明确点击了「切换过去」。调用方需要执行 Space 切换并进入目标群。
         *
         * @param notice 原始通知（含 groupNo / targetSpaceId / targetSpaceName）
         */
        void onSwitch(@NonNull JoinSuccessHelper.JoinNotice notice);
    }

    private JoinSuccessDialog() {
    }

    /**
     * 根据 {@code notice.crossSpace} 决定弹普通 Toast 还是跨 Space Dialog。
     *
     * <p>硬约束：Toast/Dialog 超时或取消 <b>不自动切换 Space</b>，必须用户点「切换过去」。
     *
     * @param context  Activity / Application context（Dialog 需要 Activity context）
     * @param notice   通知数据（通常来自 {@link JoinSuccessHelper#consumeNotice()}）
     * @param listener 切换回调；{@code null} 时按钮只 dismiss
     */
    public static void showFromNotice(@Nullable Context context,
                                      @Nullable JoinSuccessHelper.JoinNotice notice,
                                      @Nullable OnSwitchListener listener) {
        if (context == null || notice == null) return;

        if (!notice.crossSpace) {
            // 同 Space 或 Space 模式缺失：走常规 toast
            showSameSpaceToast(context, notice.groupName);
            return;
        }
        try {
            showCrossSpaceDialog(context, notice, listener);
        } catch (Throwable t) {
            // Dialog 弹失败时 fail-open 成普通 toast，避免静默
            showSameSpaceToast(context, notice.groupName);
        }
    }

    /** 同 Space 加群：显示「已加入「群名」」常规 toast。*/
    public static void showSameSpaceToast(@NonNull Context context, @Nullable String groupName) {
        String msg;
        if (groupName == null || groupName.isEmpty()) {
            msg = context.getString(R.string.join_success_same_space, "");
        } else {
            msg = context.getString(R.string.join_success_same_space, groupName);
        }
        WKToastUtils.getInstance().showToastNormal(msg);
    }

    /** 跨 Space 加群：两行文本 + 紫色切换按钮 Dialog。*/
    private static void showCrossSpaceDialog(@NonNull Context context,
                                             @NonNull JoinSuccessHelper.JoinNotice notice,
                                             @Nullable OnSwitchListener listener) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_join_success_cross_space, null, false);
        TextView line1 = view.findViewById(R.id.joinSuccessLine1);
        TextView line2 = view.findViewById(R.id.joinSuccessLine2);
        TextView cancelBtn = view.findViewById(R.id.joinSuccessCancelBtn);
        TextView switchBtn = view.findViewById(R.id.joinSuccessSwitchBtn);

        line1.setText(context.getString(R.string.join_success_cross_line1, notice.groupName));
        line2.setText(context.getString(R.string.join_success_cross_line2, notice.targetSpaceName));

        Dialog dialog = new Dialog(context, R.style.TransparentDialog);
        dialog.setContentView(view);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.82f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        switchBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onSwitch(notice);
            }
        });

        dialog.show();
    }
}
