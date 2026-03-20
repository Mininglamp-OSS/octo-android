package com.chat.base.views.swipeback;

import android.app.Activity;
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
                if (state == SwipeBackLayout.STATE_IDLE && isTranslucent) {
                    restoreOpaque();
                }
            }

            @Override
            public void onEdgeTouch(int edgeFlag) {
                if (!isTranslucent) {
                    isTranslucent = true;
                    Utils.convertActivityToTranslucent(mActivity);
                }
            }

            @Override
            public void onScrollOverThreshold() {

            }
        });
    }

    private void restoreOpaque() {
        isTranslucent = false;
        Utils.convertActivityFromTranslucent(mActivity);
    }

    /**
     * 在 onResume 时调用，强制恢复系统级不透明状态
     * 防止 convertFromTranslucent 反射失败导致的累积透明
     */
    public void ensureOpaque() {
        if (isTranslucent) {
            restoreOpaque();
        }
    }

    public void onPostCreate() {
        mSwipeBackLayout.attachToActivity(mActivity);
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
