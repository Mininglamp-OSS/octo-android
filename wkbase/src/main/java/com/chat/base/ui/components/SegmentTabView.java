package com.chat.base.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
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

/**
 * Web 风格下划线 Tab — 类似 Discord/Slack 频道切换。
 * 底部有一条全宽基准线分隔导航和内容区，选中 tab 的指示条压在基准线上。
 */
public class SegmentTabView extends LinearLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final LinearLayout[] tabContainers = new LinearLayout[2];
    private final TextView[] tabs = new TextView[2];
    private final TextView[] badges = new TextView[2];
    private int selectedIndex = 0;
    private OnTabSelectedListener listener;

    // 底部全宽基准线
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float baselineHeight = AndroidUtilities.dp(0.5f);

    // 选中指示条
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF indicatorRect = new RectF();
    private float indicatorLeft;
    private float indicatorRight;
    private final int indicatorHeight = AndroidUtilities.dp(2.5f);
    private final int indicatorRadius = AndroidUtilities.dp(1.5f);

    public SegmentTabView(@NonNull Context context, String[] tabTitles) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        init(context, tabTitles);
    }

    private void init(Context context, String[] tabTitles) {
        indicatorPaint.setColor(Theme.colorAccount);
        indicatorPaint.setStyle(Paint.Style.FILL);

        baselinePaint.setStyle(Paint.Style.FILL);
        baselinePaint.setColor(ContextCompat.getColor(context, R.color.color999));
        baselinePaint.setAlpha(60);

        for (int i = 0; i < 2; i++) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(HORIZONTAL);
            container.setGravity(Gravity.CENTER);
            container.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(12));

            TextView tv = new TextView(context);
            tv.setText(tabTitles[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setGravity(Gravity.CENTER);
            container.addView(tv, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            // badge — 圆形红底白字，和子区未读气泡一致
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
            badgeLp.leftMargin = AndroidUtilities.dp(6);
            badgeLp.gravity = Gravity.CENTER_VERTICAL;
            container.addView(badge, badgeLp);

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

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 设置指定 tab 的未读数角标。
     * @param tabIndex 0 或 1
     * @param count 未读数，0 则隐藏
     */
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

    private void updateTabStyles() {
        for (int i = 0; i < 2; i++) {
            if (i == selectedIndex) {
                tabs[i].setTextColor(Theme.colorAccount);
                tabs[i].getPaint().setFakeBoldText(true);
            } else {
                tabs[i].setTextColor(ContextCompat.getColor(getContext(), R.color.popupTextColor));
                tabs[i].getPaint().setFakeBoldText(false);
            }
        }
    }

    private void animateIndicator() {
        LinearLayout target = tabContainers[selectedIndex];
        float newLeft = target.getLeft();
        float newRight = target.getRight();

        if (indicatorLeft == 0 && indicatorRight == 0) {
            indicatorLeft = newLeft;
            indicatorRight = newRight;
            invalidate();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        float startLeft = indicatorLeft;
        float startRight = indicatorRight;
        animator.addUpdateListener(a -> {
            float fraction = (float) a.getAnimatedValue();
            indicatorLeft = startLeft + (newLeft - startLeft) * fraction;
            indicatorRight = startRight + (newRight - startRight) * fraction;
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        LinearLayout target = tabContainers[selectedIndex];
        indicatorLeft = target.getLeft();
        indicatorRight = target.getRight();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float bottom = getHeight();

        // 1. 全宽基准线 — 分隔导航区和内容区
        canvas.drawRect(0, bottom - baselineHeight, getWidth(), bottom, baselinePaint);

        // 2. 选中指示条 — 压在基准线上方，比 tab 略窄
        float top = bottom - indicatorHeight;
        float inset = (indicatorRight - indicatorLeft) * 0.15f;
        indicatorRect.set(indicatorLeft + inset, top, indicatorRight - inset, bottom);
        indicatorPaint.setColor(Theme.colorAccount);
        canvas.drawRoundRect(indicatorRect, indicatorRadius, indicatorRadius, indicatorPaint);
    }
}
