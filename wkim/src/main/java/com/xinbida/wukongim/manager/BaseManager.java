package com.xinbida.wukongim.manager;

import android.os.Handler;
import android.os.Looper;

/**
 * 2020-09-21 13:48
 * 管理者
 */
public class BaseManager {

    private boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    private Handler mainHandler;

    synchronized void runOnMainThread(ICheckThreadBack iCheckThreadBack) {
        if (iCheckThreadBack == null) {
            return;
        }
        if (!isMainThread()) {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(iCheckThreadBack::onMainThread);
        } else iCheckThreadBack.onMainThread();
    }

    protected interface ICheckThreadBack {
        void onMainThread();
    }
}
