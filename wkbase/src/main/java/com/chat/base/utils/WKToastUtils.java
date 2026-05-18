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

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;


/**
 * 2019-11-19 17:32
 * toast
 */
public class WKToastUtils {
    private WKToastUtils() {
    }

    private static class ToastUtilsBinder {
        private static final WKToastUtils utils = new WKToastUtils();
    }

    public static WKToastUtils getInstance() {
        return ToastUtilsBinder.utils;
    }


    public void showToastSuccess(String msg) {
        showToast(msg, 1);
    }

    public void showToastFail(String msg) {
        showToast(msg, 2);
    }

    public void showToastNormal(String msg) {
        showToast(msg, 3);
    }

    /**
     * 显示一个toast
     *
     * @param msg  显示内容
     * @param type 1：成功，2：失败，3：普通
     */
    private void showToast(String msg, int type) {
        Toast toast = Toast.makeText(WKBaseApplication.getInstance().getContext(), msg, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 400);
        toast.show();
    }

    public void showToast(String msg) {
        Toast toast = Toast.makeText(WKBaseApplication.getInstance().getContext(), msg, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 400);
        toast.show();
    }

    private View getToastView(String content, int type) {
        View view = LayoutInflater.from(WKBaseApplication.getInstance().getContext()).inflate(R.layout.wk_toast_layout, null);
        TextView toastTv = view.findViewById(R.id.toastTv);
        toastTv.setText(content);
        ImageView toastIv = view.findViewById(R.id.toastIv);

        toastIv.setImageResource(R.mipmap.icon_info);
        view.setBackgroundResource(R.drawable.toast_bg_normal);
        return view;
    }
}
