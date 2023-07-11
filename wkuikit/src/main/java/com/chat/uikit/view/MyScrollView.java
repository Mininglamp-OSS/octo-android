package com.chat.uikit.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.core.widget.NestedScrollView;

public class MyScrollView extends NestedScrollView {

    private boolean scroll = true;  //默认可以滑动

    public MyScrollView(Context context) {
        super(context);
    }

    public MyScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    //传入true可滑动，传入false不可滑动
    public void setScroll(boolean scroll) {
        this.scroll = scroll;

    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (scroll) {
            return super.onTouchEvent(ev);
        } else {
            return true;
        }
    }
}
