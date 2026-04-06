package com.chat.base.views.swipeback;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;

import com.chat.base.R;

/**
 * @author Yrom
 */
public class SwipeBackActivityHelper {
    private final Activity mActivity;

    private SwipeBackLayout mSwipeBackLayout;
    private boolean isTranslucent = false;

    public SwipeBackActivityHelper(Activity activity) {
        mActivity = activity;
    }

    @SuppressWarnings("deprecation")
    public void onActivityCreate() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mActivity.getWindow().getDecorView().setBackgroundDrawable(null);
        mSwipeBackLayout = (SwipeBackLayout) LayoutInflater.from(mActivity).inflate(R.layout.wk_swipeback_layout, null);
        mSwipeBackLayout.addSwipeListener(new SwipeBackLayout.SwipeListener() {
            @Override
            public void onScrollStateChange(int state, float scrollPercent) {
            }

            @Override
            public void onEdgeTouch(int edgeFlag) {
            }

            @Override
            public void onScrollOverThreshold() {

            }
        });
    }

    public void ensureOpaque() {
    }

    public void onPostCreate() {
        mSwipeBackLayout.attachToActivity(mActivity);

        // Set background AFTER attachToActivity so it's applied inside decor.post(),
        // after decorChild is moved into SwipeBackLayout (avoids one-frame cover on enter)
        mSwipeBackLayout.post(() -> {
            int bgColor = 0xFFFFFFFF;
            TypedArray a = mActivity.getTheme().obtainStyledAttributes(
                    new int[]{android.R.attr.colorBackground});
            bgColor = a.getColor(0, bgColor);
            a.recycle();
            mSwipeBackLayout.setBackgroundColor(bgColor);
        });
    }

    public <T extends View> T findViewById(int id) {
        if (mSwipeBackLayout != null) {
            return mSwipeBackLayout.findViewById(id);
        }
        return null;
    }

    public SwipeBackLayout getSwipeBackLayout() {
        return mSwipeBackLayout;
    }
}
