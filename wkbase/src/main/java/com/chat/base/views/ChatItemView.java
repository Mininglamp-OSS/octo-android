package com.chat.base.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class ChatItemView extends LinearLayout {
    public ChatItemView(Context context) {
        this(context, null);
    }

    public ChatItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private IViewClick iViewClick;
    private boolean isDelivered;

    public void setTouchData(boolean isDelivered, IViewClick iViewClick) {
        this.isDelivered = isDelivered;
        this.iViewClick = iViewClick;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isDelivered) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                iViewClick.onClick();
            }
            return true;
        } else {
            return super.dispatchTouchEvent(ev);
        }
    }

//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        if (!isDelivered) {
//            if (ev.getAction() == MotionEvent.ACTION_UP) {
//                iViewClick.onClick();
//            }
//            return true;
//        } else {
//            return super.onInterceptTouchEvent(ev);
//        }
//    }

    public interface IViewClick {
        void onClick();
    }
}
