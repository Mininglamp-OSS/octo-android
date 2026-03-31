package com.xinbida.wukongim.message.timer;

import android.util.Log;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.message.WKConnection;
import com.xinbida.wukongim.message.type.WKConnectReason;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.utils.WKLoggerUtils;

public class NetworkChecker {
    private final Object lock = new Object(); // 添加锁对象
    public boolean isForcedReconnect;
    public boolean checkNetWorkTimerIsRunning = false;

    public void startNetworkCheck() {
        TimerManager.getInstance().addTask(
                TimerTasks.NETWORK_CHECK,
                () -> {
                    synchronized (lock) {
                        checkNetworkStatus();
                    }
                },
                0,
                1000
        );
    }

    private void checkNetworkStatus() {
        boolean is_have_network = WKIMApplication.getInstance().isNetworkConnected();
        if (!is_have_network) {
            isForcedReconnect = true;
            WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
            WKLoggerUtils.getInstance().e("无网络连接...");
            WKConnection.getInstance().checkSendingMsg();
        } else {
            //有网络
            if (WKConnection.getInstance().connectionIsNull() || isForcedReconnect) {
                WKConnection.getInstance().reconnection();
                isForcedReconnect = false;
            }
        }
//        if (WKConnection.getInstance().connection == null || !WKConnection.getInstance().connection.isOpen()) {
//            WKConnection.getInstance().reconnection("网络2");
//        }
        checkNetWorkTimerIsRunning = true;
    }
}
