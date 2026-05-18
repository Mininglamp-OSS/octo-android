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

package com.chat.uikit.contacts;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 联系人列表吸顶字母头 ItemDecoration
 * 根据拼音首字母绘制吸顶分组头，并为每个分组首项预留顶部空间
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

    private final int headerHeight;
    private final Paint bgPaint;
    private final Paint textPaint;
    private final int headerOffset; // adapter header count offset
    private final boolean addItemOffset; // whether to add top offset for section first items

    private StickyHeaderDecoration(int headerHeightPx, int headerOffset, boolean addItemOffset) {
        this.headerHeight = headerHeightPx;
        this.headerOffset = headerOffset;
        this.addItemOffset = addItemOffset;

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#F7F7F7"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#999999"));
        textPaint.setTextSize(headerHeightPx * 0.4f);
        textPaint.setFakeBoldText(true);
    }

    private interface LetterProvider {
        String getLetterAt(int dataIndex);
        int getDataSize();
    }

    private LetterProvider provider;

    /**
     * 用于 FriendAdapter 的便捷构造（item 布局已有内嵌 pyTv，不需要额外偏移）
     */
    public static StickyHeaderDecoration forFriendAdapter(
            int headerHeightPx, int headerOffset,
            java.util.function.IntFunction<String> letterGetter,
            java.util.function.IntSupplier sizeGetter) {
        return create(headerHeightPx, headerOffset, false, letterGetter, sizeGetter);
    }

    /**
     * 用于没有内嵌分组头的列表（如群组列表），需要额外顶部偏移
     */
    public static StickyHeaderDecoration forGenericAdapter(
            int headerHeightPx, int headerOffset,
            java.util.function.IntFunction<String> letterGetter,
            java.util.function.IntSupplier sizeGetter) {
        return create(headerHeightPx, headerOffset, true, letterGetter, sizeGetter);
    }

    private static StickyHeaderDecoration create(
            int headerHeightPx, int headerOffset, boolean addItemOffset,
            java.util.function.IntFunction<String> letterGetter,
            java.util.function.IntSupplier sizeGetter) {
        StickyHeaderDecoration d = new StickyHeaderDecoration(headerHeightPx, headerOffset, addItemOffset);
        d.provider = new LetterProvider() {
            @Override
            public String getLetterAt(int dataIndex) {
                return letterGetter.apply(dataIndex);
            }
            @Override
            public int getDataSize() {
                return sizeGetter.getAsInt();
            }
        };
        return d;
    }

    private String getLetterForPosition(int adapterPos) {
        if (provider == null) return null;
        int dataIndex = adapterPos - headerOffset;
        if (dataIndex < 0 || dataIndex >= provider.getDataSize()) return null;
        String letter = provider.getLetterAt(dataIndex);
        if (letter == null || letter.isEmpty()) return "#";
        return letter.substring(0, 1).toUpperCase();
    }

    private boolean isFirstInSection(int adapterPos) {
        String current = getLetterForPosition(adapterPos);
        if (current == null) return false;
        String prev = getLetterForPosition(adapterPos - 1);
        return prev == null || !prev.equals(current);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!addItemOffset) return;
        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;
        if (isFirstInSection(pos)) {
            outRect.top = headerHeight;
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (provider == null || provider.getDataSize() == 0) return;

        LinearLayoutManager lm = (LinearLayoutManager) parent.getLayoutManager();
        if (lm == null) return;

        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();

        if (addItemOffset) {
            // Draw inline section headers for each visible first-in-section item
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (isFirstInSection(pos)) {
                    String letter = getLetterForPosition(pos);
                    if (letter == null) continue;
                    float top = child.getTop() - headerHeight;
                    drawHeader(c, left, right, top, letter);
                }
            }
        }

        // Draw sticky header at top
        int firstVisible = lm.findFirstVisibleItemPosition();
        if (firstVisible == RecyclerView.NO_POSITION) return;

        String currentLetter = getLetterForPosition(firstVisible);
        if (currentLetter == null) return;

        float top = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            String letter = getLetterForPosition(pos);
            if (letter != null && !letter.equals(currentLetter)) {
                float childTop = child.getTop();
                if (addItemOffset) {
                    childTop -= headerHeight;
                }
                if (childTop < headerHeight) {
                    top = childTop - headerHeight;
                }
                break;
            }
        }

        drawHeader(c, left, right, top, currentLetter);
    }

    private void drawHeader(Canvas c, int left, int right, float top, String letter) {
        c.save();
        c.translate(0, top);
        c.drawRect(left, 0, right, headerHeight, bgPaint);
        Rect textBounds = new Rect();
        textPaint.getTextBounds(letter, 0, letter.length(), textBounds);
        float x = left + dp(15);
        float y = (headerHeight + textBounds.height()) * 0.5f;
        c.drawText(letter, x, y, textPaint);
        c.restore();
    }

    private int dp(int value) {
        return (int) (value * android.content.res.Resources.getSystem().getDisplayMetrics().density + 0.5f);
    }
}
