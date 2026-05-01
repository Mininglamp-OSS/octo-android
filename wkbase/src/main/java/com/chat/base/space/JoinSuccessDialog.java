package com.chat.base.space;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.R;
import com.chat.base.utils.WKToastUtils;
import com.google.android.material.snackbar.Snackbar;

/**
 * 跨 Space 加群成功 — Snackbar 风格 Toast + 紫色「切换到 XX 查看」按钮。
 *
 * <p>YUJ-200 Path B / YUJ-212（对齐 dmwork-web PR#1102 · YUJ-170）：
 * 将原 YUJ-140 两行 Dialog 改为 Toast（Snackbar）+ 单 action 按钮，三端 UX 对齐。
 * 文案来自统一 i18n key {@code group_join_cross_space_notice}（正文）和
 * {@code group_join_cross_space_action}（按钮）。同 Space 场景仍走系统 Toast。
 */
public final class JoinSuccessDialog {

    /** 点击「切换到 XX 查看」按钮时的回调。*/
    public interface OnSwitchListener {
        /**
         * 用户明确点击了切换按钮。调用方负责切 Space 并进入目标群。
         *
         * @param notice 原始通知（含 groupNo / targetSpaceId / targetSpaceName）
         */
        void onSwitch(@NonNull JoinSuccessHelper.JoinNotice notice);
    }

    private JoinSuccessDialog() {
    }

    /**
     * 根据 {@code notice.crossSpace} 决定弹普通 Toast 还是跨 Space Snackbar。
     *
     * <p>硬约束：Snackbar 超时或滑动消失 <b>不自动切换 Space</b>，必须用户点按钮。
     *
     * @param context  Activity / Application context（Snackbar 需要 Activity）
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
            showCrossSpaceToast(context, notice, listener);
        } catch (Throwable t) {
            // Snackbar 弹失败时 fail-open 成普通 toast，避免静默
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

    /**
     * 跨 Space 加群：Snackbar「已加入【{spaceName}】的群组「{groupName}」」+
     * 「切换到{spaceName}查看」action。
     *
     * <p>Snackbar 依附于 Activity 根 View；action 点击后立即 dismiss 并回调。
     */
    private static void showCrossSpaceToast(@NonNull Context context,
                                            @NonNull JoinSuccessHelper.JoinNotice notice,
                                            @Nullable OnSwitchListener listener) {
        View anchor = resolveAnchor(context);
        if (anchor == null) {
            // 无法找到 Activity 根视图：退回普通 toast，保证提示不丢
            showSameSpaceToast(context, notice.groupName);
            return;
        }

        String body = context.getString(R.string.group_join_cross_space_notice,
                notice.targetSpaceName, notice.groupName);
        String action = context.getString(R.string.group_join_cross_space_action,
                notice.targetSpaceName);

        Snackbar snackbar = Snackbar.make(anchor, body, Snackbar.LENGTH_LONG);
        // 放长一点，给用户时间点按钮；对齐 web Toast 5s 双行的观察时间
        snackbar.setDuration(5000);
        snackbar.setAction(action, v -> {
            if (listener != null) {
                listener.onSwitch(notice);
            }
            snackbar.dismiss();
        });
        // 紫色按钮（对齐 web「切换过去 →」风格 / 原 YUJ-140 Dialog 紫色切换按钮）
        try {
            snackbar.setActionTextColor(
                    androidx.core.content.ContextCompat.getColor(context,
                            R.color.join_success_switch_purple));
        } catch (Throwable ignored) {
        }
        // 允许长文案（Space 名 + 群名可能较长），避免被 Snackbar 默认单行裁剪
        try {
            View sbView = snackbar.getView();
            TextView tv = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
            if (tv != null) {
                tv.setMaxLines(3);
            }
        } catch (Throwable ignored) {
        }
        snackbar.show();
    }

    /** 找 Activity 根 ViewGroup 作为 Snackbar anchor。*/
    @Nullable
    private static View resolveAnchor(@NonNull Context context) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing()) return null;
        View root = activity.findViewById(android.R.id.content);
        if (root instanceof ViewGroup) return root;
        return root != null ? root : activity.getWindow().getDecorView();
    }

    @Nullable
    private static Activity findActivity(@NonNull Context ctx) {
        if (ctx instanceof Activity) return (Activity) ctx;
        if (ctx instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) ctx).getBaseContext();
            if (base != null && base != ctx) return findActivity(base);
        }
        return null;
    }
}
