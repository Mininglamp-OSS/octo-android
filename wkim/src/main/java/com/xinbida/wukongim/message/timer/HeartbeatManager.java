package com.xinbida.wukongim.message.timer;

import android.util.Log;

import com.xinbida.wukongim.BuildConfig;
import com.xinbida.wukongim.message.WKConnection;
import com.xinbida.wukongim.protocol.WKPingMsg;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import java.util.concurrent.locks.ReentrantLock;

public class HeartbeatManager {
    private static final String TAG = "HeartbeatManager";
    private static final long HEARTBEAT_INTERVAL_SEC = 60;

    private final ReentrantLock heartbeatLock = new ReentrantLock();

    public void startHeartbeat() {
        TimerManager.getInstance().addTask(
                TimerTasks.HEARTBEAT,
                () -> {
                    heartbeatLock.lock();
                    try {
                        checkAndSendHeartbeat();
                    } finally {
                        heartbeatLock.unlock();
                    }
                },
                0,
                1000 * HEARTBEAT_INTERVAL_SEC
        );
    }

    private void checkAndSendHeartbeat() {
        long lastMsgTime = WKConnection.getInstance().getLastMsgTime();
        long now = DateUtils.getInstance().getCurrentSeconds();
        long elapsed = lastMsgTime > 0 ? (now - lastMsgTime) : -1;
        int connStatus = WKConnection.getInstance().getConnectStatus();

        if (lastMsgTime <= 0) {
            if (BuildConfig.DEBUG) Log.d("MsgDebug", "[Heartbeat] lastMsgTime=0, sending PING, connStatus=" + connStatus);
            WKConnection.getInstance().sendMessage(new WKPingMsg());
            return;
        }
        if (elapsed > HEARTBEAT_INTERVAL_SEC + 1) {
            if (BuildConfig.DEBUG) Log.d("MsgDebug", "[Heartbeat] PONG TIMEOUT: elapsed=" + elapsed + "s, reconnecting. connStatus=" + connStatus);
            WKLoggerUtils.getInstance().e(TAG, "Pong timeout: " + elapsed + "s since last data, reconnecting");
            WKConnection.getInstance().reconnection();
            return;
        }
        if (BuildConfig.DEBUG) Log.d("MsgDebug", "[Heartbeat] OK: elapsed=" + elapsed + "s, sending PING, connStatus=" + connStatus);
        WKConnection.getInstance().sendMessage(new WKPingMsg());
    }
}
