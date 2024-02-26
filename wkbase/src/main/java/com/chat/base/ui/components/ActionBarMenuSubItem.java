package com.chat.base.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.config.WKConstants;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;

import org.telegram.ui.Components.RLottieImageView;

@SuppressLint("ViewConstructor")
public class ActionBarMenuSubItem extends FrameLayout {

    private final TextView textView;
    private TextView subtextView;
    private final RLottieImageView imageView;
    private CheckBox checkView;
    private ImageView rightIcon;

    private int textColor;
    private int iconColor;
    private int selectorColor;

    boolean top;
    boolean bottom;

    private int itemHeight = 48;
    Runnable openSwipeBackLayout;

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom) {
        super(context);

        this.top = top;
        this.bottom = bottom;

        textColor = ContextCompat.getColor(context, R.color.colorDark);
        iconColor = ContextCompat.getColor(context, R.color.popupTextColor);
        selectorColor = ContextCompat.getColor(context, R.color.layoutColorSelected);

        updateBackground();
        setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), 0);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (AndroidUtilities.isRTL ? Gravity.END : Gravity.START)));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.START);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
//        int dimension = getResources().getDimensionPixelSize(R.dimen.font_size_14);
//        //根据position 获取字体倍数
//        float fontSizeScale = WKConstants.getFontScale();
//        //放大后的sp单位
//        double v = fontSizeScale * (int) AndroidUtilities.px2sp(dimension);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
//        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (int) v); //22SP

        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL));

        if (needCheck) {
            checkView = new CheckBox(context, 26);
            checkView.setColor(R.color.colorAccentUn, R.color.colorAccentUn);
            addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (AndroidUtilities.isRTL ? Gravity.END : Gravity.START)));
        }
    }

    public void setHeight(int height) {
        getLayoutParams().height = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(itemHeight), MeasureSpec.EXACTLY));
    }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    public void setChecked(boolean checked) {
        if (checkView == null) {
            return;
        }
        checkView.setChecked(checked, true);
    }

    public void setCheckColor(String colorKey) {
        checkView.setColor(R.color.color999, R.color.color999);
    }

    public void setRightIcon(int icon) {
        if (rightIcon == null) {
            rightIcon = new ImageView(getContext());
            rightIcon.setScaleType(ImageView.ScaleType.CENTER);
            rightIcon.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
            if (AndroidUtilities.isRTL) {
                rightIcon.setScaleX(-1);
            }
            addView(rightIcon, LayoutHelper.createFrame(24, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (AndroidUtilities.isRTL ? Gravity.START : Gravity.END)));
        }
        setPadding(AndroidUtilities.dp(AndroidUtilities.isRTL ? 8 : 15), 0, AndroidUtilities.dp(AndroidUtilities.isRTL ? 15 : 8), 0);
        rightIcon.setImageResource(icon);
    }

    public void setTextAndIcon(CharSequence text, int icon) {
        setTextAndIcon(text, icon, null);
    }

    public void setMultiline() {
        textView.setLines(2);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(false);
        textView.setGravity(Gravity.CENTER_VERTICAL);
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (icon != 0 || iconDrawable != null || checkView != null) {
            if (iconDrawable != null) {
                imageView.setImageDrawable(iconDrawable);
            } else {
                imageView.setImageResource(icon);
            }
            imageView.setVisibility(VISIBLE);
            textView.setPadding(AndroidUtilities.isRTL ? 0 : AndroidUtilities.dp(43), 0, AndroidUtilities.isRTL ? AndroidUtilities.dp(43) : 0, 0);
        } else {
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            textView.setTextColor(this.textColor = textColor);
        }
    }

    public void setIconColor(int iconColor) {
        if (this.iconColor != iconColor) {
            imageView.setColorFilter(new PorterDuffColorFilter(this.iconColor = iconColor, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setIcon(int resId) {
        imageView.setImageResource(resId);
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        subtextView.setTextColor(color);
    }

    public void setSubtext(String text) {
        if (subtextView == null) {
            subtextView = new TextView(getContext());
            subtextView.setLines(1);
            subtextView.setSingleLine(true);
            subtextView.setGravity(Gravity.START);
            subtextView.setEllipsize(TextUtils.TruncateAt.END);
            subtextView.setTextColor(0xff7C8286);
            subtextView.setVisibility(GONE);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtextView.setPadding(AndroidUtilities.isRTL ? 0 : AndroidUtilities.dp(43), 0, AndroidUtilities.isRTL ? AndroidUtilities.dp(43) : 0, 0);
            addView(subtextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL, 0, 10, 0, 0));
        }
        boolean visible = !TextUtils.isEmpty(text);
        boolean oldVisible = subtextView.getVisibility() == VISIBLE;
        if (visible != oldVisible) {
            subtextView.setVisibility(visible ? VISIBLE : GONE);
            LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
            layoutParams.bottomMargin = visible ? AndroidUtilities.dp(10) : 0;
            textView.setLayoutParams(layoutParams);
        }
        subtextView.setText(text);
    }

    public void setSubOnlyText(String text) {
        if (subtextView != null) {
            subtextView.setText(text);
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setSelectorColor(int selectorColor) {
        if (this.selectorColor != selectorColor) {
            this.selectorColor = selectorColor;
            updateBackground();
        }
    }

    public void updateSelectorBackground(boolean top, boolean bottom) {
        if (this.top == top && this.bottom == bottom) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        updateBackground();
    }

    void updateBackground() {
        int topBackgroundRadius = top ? 6 : 0;
        int bottomBackgroundRadius = bottom ? 6 : 0;
        setBackground(Theme.createRadSelectorDrawable(selectorColor, topBackgroundRadius, bottomBackgroundRadius));
    }

    public CheckBox getCheckView() {
        return checkView;
    }

    public void openSwipeBack() {
        if (openSwipeBackLayout != null) {
            openSwipeBackLayout.run();
        }
    }

    public void onItemShown() {
        if (imageView.getAnimatedDrawable() != null) {
            imageView.getAnimatedDrawable().start();
        }
    }

    public ImageView getRightIcon() {
        return rightIcon;
    }
}
