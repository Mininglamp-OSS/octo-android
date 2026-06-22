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

package com.chat.uikit.group.webhook;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.uikit.R;

import java.util.List;

/**
 * Webhook 列表项长按弹出的浮层菜单 - 与 iOS WKFloatingMenu / SummaryItemActionPopup 风格一致：
 * 白底圆角 8dp、行高 44dp、字号 15sp，destructive 项红字。
 */
public final class WebhookActionMenu {

    public static class Item {
        public final String title;
        public final boolean destructive;
        public final Runnable action;

        public Item(String title, boolean destructive, Runnable action) {
            this.title = title;
            this.destructive = destructive;
            this.action = action;
        }
    }

    private WebhookActionMenu() {
    }

    public static void show(View anchor, List<Item> items) {
        if (anchor == null || items == null || items.isEmpty()) return;
        Context ctx = anchor.getContext();

        int popupWidthPx = dp(ctx, 160);
        int rowHeightPx = dp(ctx, 44);
        int padH = dp(ctx, 16);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_webhook_action_popup));
        container.setElevation(dp(ctx, 8));
        container.setLayoutParams(new ViewGroup.LayoutParams(
                popupWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT));

        final PopupWindow popup = new PopupWindow(container,
                popupWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(ctx, 8));

        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            TextView row = new TextView(ctx);
            row.setText(it.title);
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setPadding(padH, 0, padH, 0);
            row.setTextColor(ContextCompat.getColor(ctx,
                    it.destructive ? R.color.red : R.color.colorDark));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setClickable(true);
            row.setFocusable(true);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx));
            row.setOnClickListener(v -> {
                popup.dismiss();
                if (it.action != null) it.action.run();
            });
            container.addView(row);

            if (i < items.size() - 1) {
                View sep = new View(ctx);
                sep.setBackgroundColor(ContextCompat.getColor(ctx, R.color.dividerColor));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(ctx, 0.5f)));
                lp.leftMargin = padH;
                lp.rightMargin = padH;
                sep.setLayoutParams(lp);
                container.addView(sep);
            }
        }

        // 测量并定位：anchor 中心为锚，优先在上方；上方剩余 < 60dp 时落到下方
        container.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeightPx = container.getMeasuredHeight();

        int[] pos = new int[2];
        anchor.getLocationOnScreen(pos);
        int anchorCenterX = pos[0] + anchor.getWidth() / 2;
        int anchorCenterY = pos[1] + anchor.getHeight() / 2;
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int edgeInset = dp(ctx, 10);

        boolean showAbove = (anchorCenterY - popupHeightPx - dp(ctx, 12)) > dp(ctx, 60);
        int y = showAbove ? (anchorCenterY - popupHeightPx - dp(ctx, 10))
                : (anchorCenterY + dp(ctx, 10));

        int x = anchorCenterX - popupWidthPx / 2;
        if (x < edgeInset) x = edgeInset;
        if (x + popupWidthPx > screenWidth - edgeInset) x = screenWidth - popupWidthPx - edgeInset;

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
