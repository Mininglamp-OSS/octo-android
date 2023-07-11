package com.chat.base.views.sidebar.listener;

/**
 * Created by Sai on 16/3/25.
 */
public interface OnQuickSideBarTouchListener {
    void onLetterChanged(String letter,int position,float y);
    void onLetterTouching(boolean touching);
}
