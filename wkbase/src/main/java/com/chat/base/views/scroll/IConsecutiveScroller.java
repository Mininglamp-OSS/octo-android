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

package com.chat.base.views.scroll;

import android.view.View;

import java.util.List;

/**
 * @Author donkingliang
 * @Description ConsecutiveScrollerLayout默认只会处理它的直接子view的滑动事件，
 * 为了让ConsecutiveScrollerLayout能支持滑动子view的下级view，提供了IConsecutiveScroller接口。
 *
 * 子view实现IConsecutiveScroller接口，并通过实现接口方法告诉ConsecutiveScrollerLayout需要滑动的下级view,
 * ConsecutiveScrollerLayout就能正确地处理它的滑动事件。
 * @Date 2020/4/18
 */
public interface IConsecutiveScroller {

    /**
     * 返回当前需要滑动的下级view。在一个时间点里只能有一个view可以滑动。
     * @return
     */
    View getCurrentScrollerView();

    /**
     * 返回所有可以滑动的子view。由于ConsecutiveScrollerLayout允许它的子view包含多个可滑动的子view，所以返回一个view列表。
      * @return
     */
    List<View> getScrolledViews();
}
