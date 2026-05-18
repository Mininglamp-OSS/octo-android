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

package com.chat.base.ui.components;

import android.view.animation.Interpolator;

public final class Easings {

    // Sine
    public static final Interpolator easeOutSine = new CubicBezierInterpolator(0.39, 0.575, 0.565, 1);
    public static final Interpolator easeInOutSine =  new CubicBezierInterpolator(0.445, 0.05, 0.55, 0.95);

    // Quad
    public static final Interpolator easeInQuad = new CubicBezierInterpolator(0.55, 0.085, 0.68, 0.53);
    public static final Interpolator easeOutQuad = new CubicBezierInterpolator(0.25, 0.46, 0.45, 0.94);
    public static final Interpolator easeInOutQuad = new CubicBezierInterpolator(0.455, 0.03, 0.515, 0.955);

    private Easings() {
    }
}
