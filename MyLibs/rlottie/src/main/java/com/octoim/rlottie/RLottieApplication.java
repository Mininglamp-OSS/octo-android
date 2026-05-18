/*
 * Copyright 2026-present OctoIM contributors
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.octoim.rlottie;

import android.app.Application;

public class RLottieApplication {

    private static volatile RLottieApplication instance;
    private boolean initialized;

    public static RLottieApplication getInstance() {
        if (instance == null) {
            synchronized (RLottieApplication.class) {
                if (instance == null) {
                    instance = new RLottieApplication();
                }
            }
        }
        return instance;
    }

    public void init(Application application) {
        if (!initialized) {
            System.loadLibrary("octoim_rlottie");
            initialized = true;
        }
    }
}
