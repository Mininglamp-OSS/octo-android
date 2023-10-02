/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package com.chat.base.ui.components;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;

import java.util.ArrayList;

public class RadioCell extends FrameLayout {

    private TextView textView;
    private RadioButton radioButton;
    private boolean needDivider;
    private boolean isRTL = false;

    public RadioCell(Context context, boolean isRTL) {
        this(context, false, 15, isRTL);
    }

    public RadioCell(Context context, boolean dialog, int padding, boolean isRTL) {
        super(context);
        this.isRTL = isRTL;
        textView = new TextView(context);
        if (dialog) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorDark));
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorDark));
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (isRTL ? Gravity.END : Gravity.START) | Gravity.TOP, padding, 0, padding, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        if (dialog) {
            radioButton.setColor(ContextCompat.getColor(context, R.color.color999), ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            radioButton.setColor(ContextCompat.getColor(context, R.color.color999), ContextCompat.getColor(context, R.color.colorAccent));
        }
        addView(radioButton, LayoutHelper.createFrame(22, 22, (isRTL ? Gravity.START : Gravity.END) | Gravity.TOP, (isRTL ? padding + 1 : 0), 14, (isRTL ? 0 : padding + 1), 0));
        setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        radioButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
        textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(String text, boolean checked, boolean divider) {
        textView.setText(text);
        radioButton.setChecked(checked, false);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public boolean isChecked() {
        return radioButton.isChecked();
    }

    public void setChecked(boolean checked, boolean animated) {
        radioButton.setChecked(checked, animated);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        super.setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(radioButton, View.ALPHA, value ? 1.0f : 0.5f));
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            radioButton.setAlpha(value ? 1.0f : 0.5f);
        }
    }

    public void hideRadioButton() {
        radioButton.setVisibility(View.GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            Paint dividerPaint = new Paint();
            dividerPaint.setStrokeWidth(1);
            dividerPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorLine));
            canvas.drawLine(isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.RadioButton");
        info.setCheckable(true);
        info.setChecked(isChecked());
    }
}
