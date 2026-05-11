package com.chat.base.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

public class SegmentTabView extends LinearLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final LinearLayout[] tabContainers = new LinearLayout[2];
    private final TextView[] tabs = new TextView[2];
    private final TextView[] badges = new TextView[2];
    private final TextView[] mentionBadges = new TextView[2];
    private int selectedIndex = 0;
    private OnTabSelectedListener listener;

    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();
    private float pillLeft, pillRight, pillTop, pillBottom;
    private final int pillRadius = AndroidUtilities.dp(20);
    private final int pillMargin = AndroidUtilities.dp(2);

    public SegmentTabView(@NonNull Context context, String[] tabTitles) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        int hPad = AndroidUtilities.dp(3);
        int vPad = AndroidUtilities.dp(3);
        setPadding(hPad, vPad, hPad, vPad);
        init(context, tabTitles);
    }

    private void init(Context context, String[] tabTitles) {
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x18000000);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(22));
        bg.setColor(Theme.isDark() ? 0xFF2C2C2E : 0xFFF0F0F0);
        setBackground(bg);

        for (int i = 0; i < 2; i++) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(HORIZONTAL);
            container.setGravity(Gravity.CENTER);
            container.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));

            TextView tv = new TextView(context);
            tv.setText(tabTitles[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setGravity(Gravity.CENTER);
            container.addView(tv, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            TextView badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTextColor(ContextCompat.getColor(context, R.color.white));
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            int badgeSize = AndroidUtilities.dp(18);
            badge.setMinWidth(badgeSize);
            badge.setMinHeight(badgeSize);
            badge.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(AndroidUtilities.dp(9));
            badgeBg.setColor(ContextCompat.getColor(context, R.color.reminderColor));
            badge.setBackground(badgeBg);
            badge.setVisibility(GONE);
            LayoutParams badgeLp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, badgeSize);
            badgeLp.leftMargin = AndroidUtilities.dp(5);
            badgeLp.gravity = Gravity.CENTER_VERTICAL;
            container.addView(badge, badgeLp);

            TextView mentionBadge = new TextView(context);
            mentionBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            mentionBadge.setTextColor(0xFFFFFFFF);
            mentionBadge.setGravity(Gravity.CENTER);
            mentionBadge.setIncludeFontPadding(false);
            mentionBadge.getPaint().setFakeBoldText(true);
            int mentionHeight = AndroidUtilities.dp(14);
            mentionBadge.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            GradientDrawable mentionBg = new GradientDrawable();
            mentionBg.setShape(GradientDrawable.RECTANGLE);
            mentionBg.setCornerRadius(AndroidUtilities.dp(7));
            mentionBg.setColor(0xFFFF9500);
            mentionBadge.setBackground(mentionBg);
            mentionBadge.setVisibility(GONE);
            LayoutParams mentionLp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, mentionHeight);
            mentionLp.leftMargin = AndroidUtilities.dp(4);
            mentionLp.gravity = Gravity.CENTER_VERTICAL;
            container.addView(mentionBadge, mentionLp);
            mentionBadges[i] = mentionBadge;

            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            container.setLayoutParams(lp);

            final int index = i;
            container.setOnClickListener(v -> selectTab(index));

            tabContainers[i] = container;
            tabs[i] = tv;
            badges[i] = badge;
            addView(container);
        }

        updateTabStyles();
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void selectTab(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;
        updateTabStyles();
        animateIndicator();
        if (listener != null) {
            listener.onTabSelected(index);
        }
    }

    public void selectTabWithoutCallback(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;
        updateTabStyles();
        animateIndicator();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setBadge(int tabIndex, int count) {
        if (tabIndex < 0 || tabIndex >= 2) return;
        TextView badge = badges[tabIndex];
        if (count > 0) {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisibility(VISIBLE);
        } else {
            badge.setVisibility(GONE);
        }
    }

    public void setMentionBadge(int tabIndex, boolean hasMention, String text) {
        if (tabIndex < 0 || tabIndex >= 2) return;
        TextView badge = mentionBadges[tabIndex];
        if (hasMention) {
            String displayText = text != null ? text.replace("[", "").replace("]", "") : text;
            badge.setText(displayText);
            badge.setVisibility(VISIBLE);
        } else {
            badge.setVisibility(GONE);
        }
    }

    public void setMentionBadge(int tabIndex, boolean hasMention) {
        setMentionBadge(tabIndex, hasMention, "");
    }

    private void updateTabStyles() {
        for (int i = 0; i < 2; i++) {
            if (i == selectedIndex) {
                tabs[i].setTextColor(ContextCompat.getColor(getContext(), R.color.colorDark));
                tabs[i].getPaint().setFakeBoldText(true);
            } else {
                tabs[i].setTextColor(ContextCompat.getColor(getContext(), R.color.color999));
                tabs[i].getPaint().setFakeBoldText(false);
            }
        }
    }

    private void animateIndicator() {
        LinearLayout target = tabContainers[selectedIndex];
        float newLeft = target.getLeft() + pillMargin;
        float newRight = target.getRight() - pillMargin;

        if (pillLeft == 0 && pillRight == 0) {
            pillLeft = newLeft;
            pillRight = newRight;
            invalidate();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        float startLeft = pillLeft;
        float startRight = pillRight;
        animator.addUpdateListener(a -> {
            float fraction = (float) a.getAnimatedValue();
            pillLeft = startLeft + (newLeft - startLeft) * fraction;
            pillRight = startRight + (newRight - startRight) * fraction;
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        LinearLayout target = tabContainers[selectedIndex];
        pillLeft = target.getLeft() + pillMargin;
        pillRight = target.getRight() - pillMargin;
        pillTop = pillMargin;
        pillBottom = getHeight() - pillMargin;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        pillPaint.setColor(Theme.isDark() ? 0xFF3A3A3C : Color.WHITE);
        pillRect.set(pillLeft, pillMargin, pillRight, getHeight() - pillMargin);
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint);
        super.dispatchDraw(canvas);
    }
}
