package com.chat.base.utils;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import com.chat.base.WKBaseApplication;

import java.lang.reflect.Field;

/**
 * 2019-10-10 13:36
 * 获取软键盘弹起收起状态及软键盘高度
 */
public class KeyboardOnGlobalChangeListener implements ViewTreeObserver.OnGlobalLayoutListener {
    private boolean mIsSoftKeyBoardShowing;
    private final View decorView;
    private final ISoftKeyBoardStatus iSoftKeyBoardStatus;

    public KeyboardOnGlobalChangeListener(View decorView, ISoftKeyBoardStatus iSoftKeyBoardStatus) {
        this.decorView = decorView;
        this.iSoftKeyBoardStatus = iSoftKeyBoardStatus;
    }

    @Override
    public void onGlobalLayout() {
        Rect rect = new Rect();
        decorView.getWindowVisibleDisplayFrame(rect);
        //计算出可见屏幕的高度
        int displayHight = rect.bottom - rect.top;
        //获得屏幕整体的高度
        int hight = AndroidUtilities.getScreenHeight();// decorView.getHeight();
        boolean visible = (double) displayHight / hight < 0.8;
        int statusBarHeight;
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            statusBarHeight = WKBaseApplication.getInstance().getContext().getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
            statusBarHeight = 30;
        }
        boolean preShowing = mIsSoftKeyBoardShowing;
        if (visible) {
            mIsSoftKeyBoardShowing = true;
            //获得键盘高度
            int keyboardHeight = hight - displayHight - statusBarHeight;
            iSoftKeyBoardStatus.onStatus(1);
            iSoftKeyBoardStatus.onKeyboardHeight(keyboardHeight);
            //showKeyboardTopPopupWindow(getScreenWidth() / 2, keyboardHeight);
        } else {
            if (preShowing) {
                iSoftKeyBoardStatus.onStatus(0);
            }
            mIsSoftKeyBoardShowing = false;
        }
//            Rect rect = new Rect();
//            // 获取当前页面窗口的显示范围
//            getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
//            int screenHeight = getScreenHeight();
//            int keyboardHeight = screenHeight - rect.bottom + rect.top; // 输入法的高度
//            Log.e("软键盘高度：", keyboardHeight + "|" + screenHeight);
//            boolean preShowing = mIsSoftKeyBoardShowing;
//            if (Math.abs(keyboardHeight) > screenHeight / 5) {
//                mIsSoftKeyBoardShowing = true; // 超过屏幕五分之一则表示弹出了输入法
//                showKeyboardTopPopupWindow(getScreenWidth() / 2, keyboardHeight);
//            } else {
//                if (preShowing) {
//                    closePopupWindow();
//                }
//                mIsSoftKeyBoardShowing = false;
//            }
    }

    public interface ISoftKeyBoardStatus {
        void onStatus(int status);

        void onKeyboardHeight(int keyboardHeight);
    }
}
