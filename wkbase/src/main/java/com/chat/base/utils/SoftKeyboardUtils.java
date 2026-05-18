/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 2019-11-13 10:13
 * 输入法管理
 */
public class SoftKeyboardUtils {
    private SoftKeyboardUtils() {
    }

    private static class SoftKeyboardUtilsBinder {
        private final static SoftKeyboardUtils softKeyboardUtils = new SoftKeyboardUtils();
    }

    public static SoftKeyboardUtils getInstance() {
        return SoftKeyboardUtilsBinder.softKeyboardUtils;
    }

    //隐藏输入面板
    public void hideInput(Context context, EditText editText) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }


    //显示输入面板
    public void showInput(Context context, EditText editText) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
    }

    /**
     * 隐藏软键盘(只适用于Activity，不适用于Fragment)
     */
    public void hideSoftKeyboard(Activity activity) {
        View view = activity.getCurrentFocus();
        if (view == null) view = new View(activity);
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void showSoftKeyBoard(Context context, EditText editText) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            editText.requestFocus();
            editText.setFocusableInTouchMode(true);
            showInput(context, editText);
        }, 200);
    }

    public void requestFocus(View view){
        if (view != null){
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.requestFocus();
        }
    }
    public void loseFocus(View view){
        ViewGroup viewParent = (ViewGroup) view.getParent();
        viewParent.setFocusable(true);
        viewParent.setFocusableInTouchMode(true);
        viewParent.requestFocus();
    }
}
