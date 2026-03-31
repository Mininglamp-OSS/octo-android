//package com.xinbida.wukongim.message;
//
//import com.xinbida.wukongim.WKIM;
//import com.xinbida.wukongim.WKIMApplication;
//import com.xinbida.wukongim.message.type.WKConnectReason;
//import com.xinbida.wukongim.message.type.WKConnectStatus;
//import com.xinbida.wukongim.protocol.WKPingMsg;
//import com.xinbida.wukongim.utils.WKLoggerUtils;
//
//import java.util.Timer;
//import java.util.TimerTask;
//
///**
// * 5/21/21 11:19 AM
// */
//class WKTimers {
//    private WKTimers() {
//    }
//
//    private static class ConnectionTimerHandlerBinder {
//        static final WKTimers timeHandle = new WKTimers();
//    }
//
//    public static WKTimers getInstance() {
//        return ConnectionTimerHandlerBinder.timeHandle;
//    }
//
//
//    // 发送心跳定时器
//    private Timer heartBeatTimer;
//    // 检查心跳定时器
//    private Timer checkHeartTimer;
//    // 检查网络状态定时器
//    private Timer checkNetWorkTimer;
//    boolean checkNetWorkTimerIsRunning = false;
//
//    //关闭所有定时器
//    void stopAll() {
//        stopHeartBeatTimer();
//        stopCheckHeartTimer();
//        stopCheckNetWorkTimer();
//    }
//
//    //开启所有定时器
//    void startAll() {
//        startHeartBeatTimer();
//        startCheckHeartTimer();
//        startCheckNetWorkTimer();
//    }
//
//    //检测网络
//    private void stopCheckNetWorkTimer() {
//        if (checkNetWorkTimer != null) {
//            checkNetWorkTimer.cancel();
//            checkNetWorkTimer.purge();
//            checkNetWorkTimer = null;
//            checkNetWorkTimerIsRunning = false;
//        }
//    }
//
//    //检测心跳
//    private void stopCheckHeartTimer() {
//        if (checkHeartTimer != null) {
//            checkHeartTimer.cancel();
//            checkHeartTimer.purge();
//            checkHeartTimer = null;
//        }
//    }
//
//    //停止心跳Timer
//    private void stopHeartBeatTimer() {
//        if (heartBeatTimer != null) {
//            heartBeatTimer.cancel();
//            heartBeatTimer.purge();
//            heartBeatTimer = null;
//        }
//    }
//
//    //开始心跳
//    private void startHeartBeatTimer() {
//        stopHeartBeatTimer();
//        heartBeatTimer = new Timer();
//        // 心跳时间
//        int heart_time = 60 * 2;
//        heartBeatTimer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                //发送心跳
//                WKConnection.getInstance().sendMessage(new WKPingMsg());
//            }
//        }, 0, heart_time * 1000);
//    }
//
//    //开始检查心跳Timer
//    private void startCheckHeartTimer() {
//        stopCheckHeartTimer();
//        checkHeartTimer = new Timer();
//        checkHeartTimer.schedule(new TimerTask() {
//
//            @Override
//            public void run() {
//                if (WKConnection.getInstance().connection == null || heartBeatTimer == null) {
//                    WKConnection.getInstance().reconnection();
//                }
//                WKConnection.getInstance().checkHeartIsTimeOut();
//            }
//        }, 1000 * 7, 1000 * 7);
//    }
//
//    boolean isForcedReconnect;
//
//    //开启检测网络定时器
//    void startCheckNetWorkTimer() {
//        stopCheckNetWorkTimer();
//        checkNetWorkTimer = new Timer();
//        checkNetWorkTimer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                boolean is_have_network = WKIMApplication.getInstance().isNetworkConnected();
//                if (!is_have_network) {
//                    isForcedReconnect = true;
//                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
//                    WKLoggerUtils.getInstance().e("No network connection...");
//                    WKConnection.getInstance().checkSendingMsg();
//                } else {
//                    //有网络
//                    if (WKConnection.getInstance().connectionIsNull() || isForcedReconnect  ) {
//                        WKConnection.getInstance().reconnection();
//                        isForcedReconnect = false;
//                    }
//                }
//                if (WKConnection.getInstance().connection == null || !WKConnection.getInstance().connection.isOpen()) {
//                    WKConnection.getInstance().reconnection();
//                }
//                checkNetWorkTimerIsRunning = true;
//            }
//        }, 0, 1000);
//    }
//}
