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

package com.oushangfeng.pinnedsectionitemdecoration.entity;

import android.view.View;

/**
 * Created by Oubowu on 2016/7/27 23:52.
 * <p>点击范围实体类，用于点击标签时做点击判断</p>
 */
public class ClickBounds {

    private View mView;

    private int mLeft;
    private int mTop;
    private int mRight;
    private int mBottom;

    // 记录第一次Top和Bottom，用于后面减偏差
    private int mFirstTop;
    private int mFirstBottom;

    public ClickBounds(View view, int left, int top, int right, int bottom) {
        mView = view;
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;

        mFirstTop = top;
        mFirstBottom = bottom;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;

        mFirstTop = top;
        mFirstBottom = bottom;
    }

    public int getLeft() {
        return mLeft;
    }

    public int getTop() {
        return mTop;
    }

    public int getRight() {
        return mRight;
    }

    public int getBottom() {
        return mBottom;
    }

    public void setBottom(int bottom) {
        mBottom = bottom;
    }

    public void setLeft(int left) {
        mLeft = left;
    }

    public void setTop(int top) {
        mTop = top;
    }

    public void setRight(int right) {
        mRight = right;
    }

    public int getFirstBottom() {
        return mFirstBottom;
    }

    public int getFirstTop() {
        return mFirstTop;
    }

    public View getView() {
        return mView;
    }
}
