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
package com.chat.base.act;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * WKPdfViewActivity 缩放几何中可脱离 View 计算的部分。
 * <p>
 * ScrollView 的真实滚动范围和手势响应依赖 View 测量，模块未接 Robolectric，
 * plain JUnit 覆盖不到，那部分只能实机验证。
 */
public class WKPdfViewActivityZoomTest {

    private static final int VIEWPORT_HEIGHT = 1920;
    private static final int CONTENT_HEIGHT = 8400;

    /**
     * 放大后底部要滑得到 —— 本次修复的核心。ScrollView 的滚动范围只认子 View 的
     * 测量高度（getScrollRange 与 scrollTo 都按 child.getHeight() 夹取），撑高后
     * 滚到底时，内容底部应正好落在视口底部。
     */
    @Test public void scrollingToBottomLandsContentBottomAtViewportBottom() {
        for (float scale : new float[]{1f, 1.5f, 2f, 3f, 4f}) {
            int measuredHeight = CONTENT_HEIGHT
                    + WKPdfViewActivity.scaleCompensationPadding(CONTENT_HEIGHT, scale);
            int scrollRange = measuredHeight - VIEWPORT_HEIGHT;
            float contentBottomOnScreen = CONTENT_HEIGHT * scale - scrollRange;
            assertEquals("scale=" + scale, VIEWPORT_HEIGHT, contentBottomOnScreen, 1f);
        }
    }

    @Test public void unscaledContentNeedsNoCompensationPadding() {
        assertEquals(0, WKPdfViewActivity.scaleCompensationPadding(CONTENT_HEIGHT, 1f));
    }

    @Test public void compensationPaddingIsNeverNegative() {
        assertEquals(0, WKPdfViewActivity.scaleCompensationPadding(CONTENT_HEIGHT, 0.5f));
    }

    @Test public void unscaledContentKeepsItsLayoutHeight() {
        assertEquals(CONTENT_HEIGHT, WKPdfViewActivity.scaledContentHeight(CONTENT_HEIGHT, 1f));
    }

    @Test public void layoutHeightNeverShrinksBelowContentHeight() {
        assertEquals(CONTENT_HEIGHT, WKPdfViewActivity.scaledContentHeight(CONTENT_HEIGHT, 0.5f));
    }

    /** 缩放前后，焦点处的同一个内容点应停在屏幕的同一位置。 */
    @Test public void anchoredOffsetKeepsFocusedContentInPlace() {
        float previousScale = 1.5f;
        float nextScale = 3f;
        float focus = 640f;
        float previousOffset = 900f;
        float contentAtFocus = (focus + previousOffset) / previousScale;

        float offset = WKPdfViewActivity.anchoredOffset(
                focus, previousOffset, nextScale / previousScale);

        assertEquals(focus, contentAtFocus * nextScale - offset, 0.001f);
    }

    @Test public void anchoredOffsetIsUnchangedWhenScaleDoesNotChange() {
        assertEquals(720f, WKPdfViewActivity.anchoredOffset(300f, 720f, 1f), 0.001f);
    }

    @Test public void clampBoundsValueToRange() {
        assertEquals(5f, WKPdfViewActivity.clamp(-3f, 5f, 10f), 0f);
        assertEquals(10f, WKPdfViewActivity.clamp(42f, 5f, 10f), 0f);
        assertEquals(7f, WKPdfViewActivity.clamp(7f, 5f, 10f), 0f);
    }
}
