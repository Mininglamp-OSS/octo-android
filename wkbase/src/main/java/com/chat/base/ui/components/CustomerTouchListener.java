package com.chat.base.ui.components;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.chat.base.views.BubbleLayout;

public class CustomerTouchListener implements View.OnTouchListener {
    public CustomerTouchListener(ICustomerTouchListener iCustomerTouchListener) {
        this.iCustomerTouchListener = iCustomerTouchListener;
        mHandler = new Handler(Looper.getMainLooper());
        longClickHandle = new Handler(Looper.getMainLooper());
        isBack = false;
        mLastDownTime = 0;
    }

    private final ICustomerTouchListener iCustomerTouchListener;
    private int mClickCount;
    private int mDownX;
    private int mDownY;
    private long mLastDownTime;
    private long mFirstClickTime;
    private final Handler mHandler;
    private final Handler longClickHandle;
    private boolean isBack;
    private boolean isUp;
    Runnable runnable;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        long longClickTime = 400L;
        int MAX_MOVE = 10;
        if (!view.hasOnClickListeners()) {
            view.setOnClickListener(view1 -> {
            });
        }
        if (runnable != null) {
            longClickHandle.removeCallbacks(runnable);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isUp = false;
                isBack = false;
                mLastDownTime = System.currentTimeMillis();
                mDownX = (int) event.getX();
                mDownY = (int) event.getY();
                mClickCount++;
                runnable = () -> {
                    if (!isUp) {
                        isBack = true;
                        iCustomerTouchListener.onLongClick(view, new float[]{event.getRawX(), event.getRawY()});
                    }
                };
                longClickHandle.postDelayed(runnable, longClickTime);
                break;
            case MotionEvent.ACTION_MOVE: {
                int mMoveX = (int) event.getX();
                int mMoveY = (int) event.getY();
                int offsetX = Math.abs(mMoveX - mDownX);
                int offsetY = Math.abs(mMoveY - mDownY);
                // 触摸点变化较大，不会判断为双击
                if (offsetX > MAX_MOVE || offsetY > MAX_MOVE) {
                    mClickCount = 0;
                    isBack = false;
                } else {
                    long mLastUpTime = System.currentTimeMillis();
                    if (mLastUpTime - mLastDownTime >= longClickTime && !isBack) {
                        isBack = true;
                        iCustomerTouchListener.onLongClick(view, new float[]{event.getRawX(), event.getRawY()});
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                isUp = true;
                long mLastUpTime = System.currentTimeMillis();
                int mUpX = (int) event.getX();
                int mUpY = (int) event.getY();
                int offsetX = Math.abs(mUpX - mDownX);
                int offsetY = Math.abs(mUpY - mDownY);

                // 触摸点变化较大，不会判断为双击或长按
                int MAX_TIME = 250;
                if (offsetX > MAX_MOVE && offsetY > MAX_MOVE) {
                    mClickCount = 0;
                } else {
                    // 判断为长按
                    if ((mLastUpTime - mLastDownTime) > MAX_TIME) {
                        mClickCount = 0;
                    }
                }

                if (mClickCount == 1) {
                    mFirstClickTime = System.currentTimeMillis();
                    mHandler.postDelayed(() -> {
                        // 判断为单击
                        if (mClickCount == 1) {
                            iCustomerTouchListener.onClick(view, new float[]{event.getRawX(), event.getRawY()});
                        }
                        mClickCount = 0;
                    }, 150);
                } else if (mClickCount == 2) {
                    long mSecondClickTime = System.currentTimeMillis();
                    // 判断为双击
                    if (mSecondClickTime - mFirstClickTime < MAX_TIME) {
                        iCustomerTouchListener.onDoubleClick(view, new float[]{event.getRawX(), event.getRawY()});
                    }
                    mClickCount = 0;
                }
                break;
            }
        }
        if (view instanceof BubbleLayout && isBack) {
            BubbleLayout bubbleLayout = (BubbleLayout) view;
            bubbleLayout.isSelected = false;
            bubbleLayout.invalidate();
        }
        return isBack;
    }

    public interface ICustomerTouchListener {
        void onClick(View view, float[] coordinate);

        void onLongClick(View view, float[] coordinate);

        void onDoubleClick(View view, float[] coordinate);
    }
}
