package com.xinbida.wukongim.message;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKSyncMsgMode;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IReceivedMsgListener;
import com.xinbida.wukongim.manager.ConnectionManager;
import com.xinbida.wukongim.message.timer.HeartbeatManager;
import com.xinbida.wukongim.message.timer.NetworkChecker;
import com.xinbida.wukongim.message.timer.TimerManager;
import com.xinbida.wukongim.message.type.WKConnectReason;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.message.type.WKSendingMsg;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKConnectMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.DispatchQueuePool;
import com.xinbida.wukongim.utils.FileUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONObject;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 5/21/21 10:51 AM
 * IM connect
 */
public class WKConnection {
    private final String TAG = "WKConnection";

    private WKConnection() {
    }

    private static class ConnectHandleBinder {
        private static final WKConnection CONNECT = new WKConnection();
    }

    public static WKConnection getInstance() {
        return ConnectHandleBinder.CONNECT;
    }

    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);
    // 正在发送的消息
    private final ConcurrentHashMap<Integer, WKSendingMsg> sendingMsgHashMap = new ConcurrentHashMap<>();
    // 正在重连中
    public boolean isReConnecting = false;
    // 连接状态
    private int connectStatus;
    private long lastMsgTime = 0;
    private String ip;
    private int port;
    public volatile INonBlockingConnection connection;
    volatile ConnectionClient connectionClient;
    private long requestIPTime;
    private long connAckTime;
    private final long requestIPTimeoutTime = 6;
    private final long connAckTimeoutTime = 10;
    public String socketSingleID;
    private String lastRequestId;
    public volatile Handler reconnectionHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    Runnable reconnectionRunnable = this::reconnection;
    private int connCount = 0;
    private HeartbeatManager heartbeatManager;
    private NetworkChecker networkChecker;

    private final Handler checkRequestAddressHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkRequestAddressRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime >= requestIPTimeoutTime) {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    WKLoggerUtils.getInstance().e(TAG, "获取连接地址超时");
                    isReConnecting = false;
                    reconnection();
                }
            } else {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    WKLoggerUtils.getInstance().e(TAG, "请求连接地址--->" + (nowTime - requestIPTime));
                    // 继续检查
                    checkRequestAddressHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    private final Handler checkConnAckHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkConnAckRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - connAckTime > connAckTimeoutTime && connectStatus != WKConnectStatus.success && connectStatus != WKConnectStatus.syncMsg) {
                WKLoggerUtils.getInstance().e(TAG, "连接确认超时");
                isReConnecting = false;
                closeConnect();
                reconnection();
            } else {
                if (connectStatus == WKConnectStatus.success || connectStatus == WKConnectStatus.syncMsg) {
                    WKLoggerUtils.getInstance().e(TAG, "连接确认成功");
                } else {
                    WKLoggerUtils.getInstance().e(TAG, "等待连接确认--->" + (nowTime - connAckTime));
                    // 继续检查
                    checkConnAckHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    // 替换原有的 Object 锁
    public final ReentrantLock connectionLock = new ReentrantLock(true); // 使用公平锁
    private static final long LOCK_TIMEOUT = 3000; // 3秒超时
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long CONNECTION_CLOSE_TIMEOUT = 5000; // 5 seconds timeout

    public final AtomicBoolean isClosing = new AtomicBoolean(false);

    private final int maxReconnectAttempts = 5;
    private final long baseReconnectDelay = 500;

    private final Object connectionStateLock = new Object();
    private volatile boolean isConnecting = false;

    private final Object reconnectLock = new Object();
    private volatile boolean isReconnectScheduled = false;
    private final Object executorLock = new Object();
    private volatile ExecutorService connectionExecutor;

    private ExecutorService getOrCreateExecutor() {
        synchronized (executorLock) {
            if (connectionExecutor == null || connectionExecutor.isShutdown() || connectionExecutor.isTerminated()) {
                connectionExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "WKConnection-Worker");
                    thread.setDaemon(true);
                    return thread;
                });
                WKLoggerUtils.getInstance().i(TAG, "创建新的连接线程池");
            }
            return connectionExecutor;
        }
    }

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private void shutdownExecutor() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            WKLoggerUtils.getInstance().w(TAG, "Executor is already shutting down");
            return;
        }

        ExecutorService executorToShutdown;
        synchronized (executorLock) {
            executorToShutdown = connectionExecutor;
            connectionExecutor = null;
        }

        if (executorToShutdown != null && !executorToShutdown.isShutdown()) {
            dispatchQueuePool.execute(() -> {
                try {
                    WKLoggerUtils.getInstance().i(TAG, "Starting executor shutdown");
                    executorToShutdown.shutdown();

                    if (!executorToShutdown.awaitTermination(3, TimeUnit.SECONDS)) {
                        WKLoggerUtils.getInstance().w(TAG, "Executor did not terminate in time, forcing shutdown");
                        executorToShutdown.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    WKLoggerUtils.getInstance().e(TAG, "Executor shutdown interrupted: " + e.getMessage());
                    executorToShutdown.shutdownNow();
                    Thread.currentThread().interrupt();
                } finally {
                    isShuttingDown.set(false);
                    WKLoggerUtils.getInstance().i(TAG, "Executor shutdown completed");
                }
            });
        }
    }

    private void startAll() {
        heartbeatManager = new HeartbeatManager();
        networkChecker = new NetworkChecker();
        heartbeatManager.startHeartbeat();
        networkChecker.startNetworkCheck();
    }

    public synchronized void forcedReconnection() {
        synchronized (reconnectLock) {
            if (isReconnectScheduled) {
                WKLoggerUtils.getInstance().w(TAG, "已经在重连计划中，忽略重复请求");
                return;
            }

            // 检查线程池状态
            ExecutorService executor = getOrCreateExecutor();
            if (executor.isShutdown() || executor.isTerminated()) {
                WKLoggerUtils.getInstance().e(TAG, "线程池已关闭，无法执行重连");
                return;
            }

            connCount++;
            if (connCount > maxReconnectAttempts) {
                WKLoggerUtils.getInstance().e(TAG, "达到最大重连次数，停止重连");
                stopAll();
                return;
            }

            isReconnectScheduled = true;
            isReConnecting = false;
            requestIPTime = 0;

            // 使用指数退避延迟，最大延迟改为8秒
            long delay = Math.min(baseReconnectDelay * (1L << (connCount - 1)), 8000);
            WKLoggerUtils.getInstance().e(TAG, "重连延迟: " + delay + "ms");

            try {
                // 使用单独的线程池处理重连
                executor.execute(() -> {
                    try {
                        Thread.sleep(delay);
                        if (WKIMApplication.getInstance().isCanConnect &&
                                !executor.isShutdown()) {
                            reconnection();
                        }
                    } catch (InterruptedException e) {
                        WKLoggerUtils.getInstance().e(TAG, "重连等待被中断");
                        Thread.currentThread().interrupt();
                    } finally {
                        isReconnectScheduled = false;
                    }
                });
            } catch (RejectedExecutionException e) {
                WKLoggerUtils.getInstance().e(TAG, "重连任务被拒绝执行: " + e.getMessage());
                isReconnectScheduled = false;
            }
        }
    }

    public synchronized void reconnection() {
        // 如果正在关闭连接，等待关闭完成
        if (isClosing.get()) {
            WKLoggerUtils.getInstance().e(TAG, "等待连接关闭完成后再重连");
            mainHandler.postDelayed(this::reconnection, 500);
            return;
        }

        if (!WKIMApplication.getInstance().isCanConnect) {
            WKLoggerUtils.getInstance().e(TAG, "断开");
            stopAll();
            return;
        }

        ip = "";
        port = 0;
        if (isReConnecting) {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime > requestIPTimeoutTime) {
                WKLoggerUtils.getInstance().e("重置了正在连接");
                isReConnecting = false;
            }
            return;
        }

        connectStatus = WKConnectStatus.fail;
        reconnectionHandler.removeCallbacks(reconnectionRunnable);
        boolean isHaveNetwork = WKIMApplication.getInstance().isNetworkConnected();
        if (isHaveNetwork) {
            closeConnect();
            isReConnecting = true;
            requestIPTime = DateUtils.getInstance().getCurrentSeconds();
            getConnAddress();
        } else {
            if (networkChecker != null && networkChecker.checkNetWorkTimerIsRunning) {
                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
                forcedReconnection();
            }
        }
    }

    private void getConnAddress() {
        ExecutorService executor = getOrCreateExecutor();
        if (executor.isShutdown()) {
            WKLoggerUtils.getInstance().e(TAG, "线程池已关闭，重新初始化后重试");
            executor = getOrCreateExecutor();
        }

        try {
            executor.execute(() -> {
                try {
                    if (!WKIMApplication.getInstance().isCanConnect) {
                        WKLoggerUtils.getInstance().e(TAG, "不允许连接");
                        return;
                    }

                    final long startTime = System.currentTimeMillis();
                    final long ADDRESS_TIMEOUT = 10000; // 10秒超时

                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.connecting, WKConnectReason.Connecting);
                    String currentRequestId = UUID.randomUUID().toString().replace("-", "");
                    lastRequestId = currentRequestId;

                    CountDownLatch addressLatch = new CountDownLatch(1);
                    AtomicReference<String> receivedIp = new AtomicReference<>();
                    AtomicInteger receivedPort = new AtomicInteger();

                    ConnectionManager.getInstance().getIpAndPort(currentRequestId, (requestId, ip, port) -> {
                        if (!currentRequestId.equals(requestId)) {
                            WKLoggerUtils.getInstance().w(TAG, "收到过期的地址响应");
                            addressLatch.countDown();
                            return;
                        }

                        receivedIp.set(ip);
                        receivedPort.set(port);
                        addressLatch.countDown();
                    });

                    // 等待地址响应或超时
                    boolean gotAddress = addressLatch.await(ADDRESS_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (!gotAddress) {
                        WKLoggerUtils.getInstance().e(TAG, "获取连接地址超时");
                        isReConnecting = false;
                        forcedReconnection();
                        return;
                    }

                    String ip = receivedIp.get();
                    int port = receivedPort.get();

                    if (TextUtils.isEmpty(ip) || port == 0) {
                        WKLoggerUtils.getInstance().e(TAG, "无效的连接地址");
                        isReConnecting = false;
                        forcedReconnection();
                        return;
                    }

                    WKConnection.this.ip = ip;
                    WKConnection.this.port = port;
                    if (connectionIsNull()) {
                        connSocket();
                    }
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e(TAG, "获取地址异常: " + e.getMessage());
                    isReConnecting = false;
                    forcedReconnection();
                }
            });
        } catch (RejectedExecutionException e) {
            WKLoggerUtils.getInstance().e(TAG, "任务提交被拒绝，重试: " + e.getMessage());
            isReConnecting = false;
            // 短暂延迟后重试
            mainHandler.postDelayed(this::reconnection, 1000);
        }
    }

    private void connSocket() {
        // 检查线程池状态
        ExecutorService executor = getOrCreateExecutor();
        if (executor.isShutdown() || executor.isTerminated()) {
            WKLoggerUtils.getInstance().e(TAG, "线程池已关闭，无法执行连接");
            return;
        }

        // 使用CAS操作检查连接状态
        if (!setConnectingState(true)) {
            WKLoggerUtils.getInstance().e(TAG, "已经在连接中，忽略重复连接请求");
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    // 关闭现有连接
                    closeConnect();

                    // 生成新的连接ID
                    String newSocketId = UUID.randomUUID().toString().replace("-", "");

                    CountDownLatch connectLatch = new CountDownLatch(1);
                    AtomicBoolean connectSuccess = new AtomicBoolean(false);

                    ConnectionClient newClient = new ConnectionClient(iNonBlockingConnection -> {
                        INonBlockingConnection currentConn = null;
                        synchronized (connectionLock) {
                            currentConn = connection;
                        }

                        if (iNonBlockingConnection == null || currentConn == null ||
                                !currentConn.getId().equals(iNonBlockingConnection.getId())) {
                            WKLoggerUtils.getInstance().e(TAG, "无效的连接回调");
                            connectLatch.countDown();
                            return;
                        }

                        try {
                            iNonBlockingConnection.setIdleTimeoutMillis(1000 * 3);
                            iNonBlockingConnection.setConnectionTimeoutMillis(1000 * 3);
                            iNonBlockingConnection.setFlushmode(IConnection.FlushMode.ASYNC);
                            iNonBlockingConnection.setAutoflush(true);

                            connectSuccess.set(true);
                            isReConnecting = false;
                            connCount = 0;
                        } catch (Exception e) {
                            WKLoggerUtils.getInstance().e(TAG, "设置连接参数失败: " + e.getMessage());
                        } finally {
                            connectLatch.countDown();
                        }
                    });

                    // 创建新连接
                    INonBlockingConnection newConnection = new NonBlockingConnection(ip, port, newClient);
                    newConnection.setAttachment(newSocketId);

                    // 原子性地更新连接相关的字段
                    synchronized (connectionLock) {
                        connectionClient = newClient;
                        connection = newConnection;
                        socketSingleID = newSocketId;
                    }

                    // 等待连接完成或超时
                    boolean connected = connectLatch.await(5000, TimeUnit.MILLISECONDS);

                    if (!connected || !connectSuccess.get()) {
                        WKLoggerUtils.getInstance().e(TAG, "连接建立超时或失败");
                        closeConnect();
                        if (!executor.isShutdown()) {
                            forcedReconnection();
                        }
                    } else {
                        sendConnectMsg();
                    }
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e(TAG, "连接异常: " + e.getMessage() + "连接地址：" + ip + ":" + port);
                    if (!executor.isShutdown()) {
                        forcedReconnection();
                    }
                } finally {
                    setConnectingState(false);
                }
            });
        } catch (RejectedExecutionException e) {
            WKLoggerUtils.getInstance().e(TAG, "连接任务被拒绝执行: " + e.getMessage());
            setConnectingState(false);
        }
    }

    // 使用CAS操作设置连接状态
    private boolean setConnectingState(boolean connecting) {
        synchronized (connectionLock) {
            if (connecting && isConnecting) {
                return false;
            }
            isConnecting = connecting;
            return true;
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        startConnAckTimer();
        sendMessage(new WKConnectMsg());
    }

    void receivedData(byte[] data) {
        MessageHandler.getInstance().cutBytes(data,
                new IReceivedMsgListener() {

                    public void sendAckMsg(
                            WKSendAckMsg talkSendStatus) {
                        // 删除队列中正在发送的消息对象
                        WKSendingMsg object = sendingMsgHashMap.get(talkSendStatus.clientSeq);
                        if (object != null) {
                            object.isCanResend = false;
                            sendingMsgHashMap.put(talkSendStatus.clientSeq, object);
                        }
                    }


                    @Override
                    public void reconnect() {
                        WKIMApplication.getInstance().isCanConnect = true;
                        reconnection();
                    }

                    @Override
                    public void loginStatusMsg(WKConnectAckMsg connectAckMsg) {
                        handleLoginStatus(connectAckMsg);
                    }

                    @Override
                    public void pongMsg(WKPongMsg msgHeartbeat) {
                        // 心跳消息
                        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
                    }

                    @Override
                    public void kickMsg(WKDisconnectMsg disconnectMsg) {
                        WKIM.getInstance().getConnectionManager().disconnect(true);
                        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.kicked, WKConnectReason.ReasonConnectKick);
                    }

                });
    }


    //重发未发送成功的消息
    public void resendMsg() {
        removeSendingMsg();
        new Thread(() -> {
            for (Map.Entry<Integer, WKSendingMsg> entry : sendingMsgHashMap.entrySet()) {
                if (entry.getValue().isCanResend) {
                    sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(entry.getKey())).wkSendMsg);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    //将要发送的消息添加到队列
    private synchronized void addSendingMsg(WKSendMsg sendingMsg) {
        removeSendingMsg();
        sendingMsgHashMap.put(sendingMsg.clientSeq, new WKSendingMsg(1, sendingMsg, true));
    }

    //处理登录消息状态
    private void handleLoginStatus(WKConnectAckMsg connectAckMsg) {
        short status = connectAckMsg.reasonCode;
        boolean locked = false;
        WKLoggerUtils.getInstance().e(TAG, "连接状态：" + status + "，连接节点：" + connectAckMsg.nodeId);
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，handleLoginStatus失败");
                return;
            }

            WKLoggerUtils.getInstance().e(TAG, "Connection state transition: " + connectStatus + " -> " + status);
            String reason = WKConnectReason.ConnectSuccess;
            if (status == WKConnectStatus.kicked) {
                reason = WKConnectReason.ReasonAuthFail;
            }

            if (!isValidStateTransition(connectStatus, status)) {
                WKLoggerUtils.getInstance().e(TAG, "Invalid state transition attempted: " + connectStatus + " -> " + status);
                return;
            }

            connectStatus = status;
            WKIM.getInstance().getConnectionManager().setConnectionStatus(status, reason);

            if (status == WKConnectStatus.success) {
                connCount = 0;
                isReConnecting = false;
                connectStatus = WKConnectStatus.syncMsg;
                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.syncMsg, WKConnectReason.SyncMsg);
                startAll();

                if (WKIMApplication.getInstance().getSyncMsgMode() == WKSyncMsgMode.WRITE) {
                    WKIM.getInstance().getMsgManager().setSyncOfflineMsg((isEnd, list) -> {
                        if (isEnd) {
                            boolean innerLocked = false;
                            try {
                                innerLocked = tryLockWithTimeout();
                                if (!innerLocked) {
                                    WKLoggerUtils.getInstance().e(TAG, "获取锁超时，setSyncOfflineMsg回调处理失败");
                                    return;
                                }
                                if (connection != null && !isClosing.get()) {
                                    connectStatus = WKConnectStatus.success;
                                    MessageHandler.getInstance().saveReceiveMsg();
                                    WKIMApplication.getInstance().isCanConnect = true;
                                    MessageHandler.getInstance().sendAck();
                                    resendMsg();
                                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                                }
                            } finally {
                                if (innerLocked) {
                                    connectionLock.unlock();
                                }
                            }
                        }
                    });
                } else {
                    WKIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                        boolean innerLocked = false;
                        try {
                            innerLocked = tryLockWithTimeout();
                            if (!innerLocked) {
                                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，setSyncConversationListener回调处理失败");
                                return;
                            }
                            if (connection != null && !isClosing.get()) {
                                connectStatus = WKConnectStatus.success;
                                WKIMApplication.getInstance().isCanConnect = true;
                                MessageHandler.getInstance().sendAck();
                                resendMsg();
                                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                            }
                        } finally {
                            if (innerLocked) {
                                connectionLock.unlock();
                            }
                        }
                    });
                }
            } else if (status == WKConnectStatus.kicked) {
                WKLoggerUtils.getInstance().e(TAG, "Received kick message");
                MessageHandler.getInstance().updateLastSendingMsgFail();
                WKIMApplication.getInstance().isCanConnect = false;
                stopAll();
            } else {
                if (WKIMApplication.getInstance().isCanConnect) {
                    reconnection();
                }
                WKLoggerUtils.getInstance().e(TAG, "Login status: " + status);
                stopAll();
            }
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private boolean isValidStateTransition(int currentState, int newState) {
        // Define valid state transitions
        return switch (currentState) {
            case WKConnectStatus.fail ->
                // From fail state, can move to connecting or success
                    newState == WKConnectStatus.connecting ||
                            newState == WKConnectStatus.success;
            case WKConnectStatus.connecting ->
                // From connecting, can move to success, fail, or no network
                    newState == WKConnectStatus.success ||
                            newState == WKConnectStatus.fail ||
                            newState == WKConnectStatus.noNetwork;
            case WKConnectStatus.success ->
                // From success, can move to syncMsg, kicked, or fail
                    newState == WKConnectStatus.syncMsg ||
                            newState == WKConnectStatus.kicked ||
                            newState == WKConnectStatus.fail;
            case WKConnectStatus.syncMsg ->
                // From syncMsg, can move to success or fail
                    newState == WKConnectStatus.success ||
                            newState == WKConnectStatus.fail;
            case WKConnectStatus.noNetwork ->
                // From noNetwork, can move to connecting or fail
                    newState == WKConnectStatus.connecting ||
                            newState == WKConnectStatus.fail;
            default ->
                // For any other state, allow transition to fail state
                    newState == WKConnectStatus.fail;
        };
    }

    public void sendMessage(WKBaseMsg mBaseMsg) {
        if (mBaseMsg == null) {
            WKLoggerUtils.getInstance().w(TAG, "sendMessage called with null mBaseMsg.");
            return;
        }

        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，sendMessage失败");
                return;
            }

            if (mBaseMsg.packetType != WKMsgType.CONNECT) {
                if (connectStatus == WKConnectStatus.syncMsg) {
                    WKLoggerUtils.getInstance().i(TAG, " sendMessage: In syncMsg status, message not sent: " + mBaseMsg.packetType);
                    return;
                }
                if (connectStatus != WKConnectStatus.success) {
                    WKLoggerUtils.getInstance().w(TAG, " sendMessage: Not in success status (is " + connectStatus + "), attempting reconnection for: " + mBaseMsg.packetType);
                    reconnection();
                    return;
                }
            }

            INonBlockingConnection currentConnection = this.connection;
            if (currentConnection == null || !currentConnection.isOpen()) {
                WKLoggerUtils.getInstance().w(TAG, " sendMessage: Connection is null or not open, attempting reconnection for: " + mBaseMsg.packetType);
                reconnection();
                return;
            }

            int status = MessageHandler.getInstance().sendMessage(currentConnection, mBaseMsg);
            if (status == 0) {
                WKLoggerUtils.getInstance().e(TAG, "发消息失败 (status 0 from MessageHandler), attempting reconnection for: " + mBaseMsg.packetType);
                reconnection();
            }
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private void removeSendingMsg() {
        if (!sendingMsgHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, WKSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, WKSendingMsg> entry = it.next();
                if (!entry.getValue().isCanResend) {
                    it.remove();
                }
            }
        }
    }

    //检测正在发送的消息
    public synchronized void checkSendingMsg() {
        removeSendingMsg();
        if (!sendingMsgHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, WKSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, WKSendingMsg> item = it.next();
                WKSendingMsg wkSendingMsg = sendingMsgHashMap.get(item.getKey());
                if (wkSendingMsg != null) {
                    if (wkSendingMsg.sendCount == 5 && wkSendingMsg.isCanResend) {
                        //标示消息发送失败
                        MsgDbManager.getInstance().updateMsgStatus(item.getKey(), WKSendMsgResult.send_fail);
                        it.remove();
                        wkSendingMsg.isCanResend = false;
                    } else {
                        long nowTime = DateUtils.getInstance().getCurrentSeconds();
                        if (nowTime - wkSendingMsg.sendTime > 10) {
                            wkSendingMsg.sendTime = DateUtils.getInstance().getCurrentSeconds();
                            sendingMsgHashMap.put(item.getKey(), wkSendingMsg);
                            wkSendingMsg.sendCount++;
                            sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(item.getKey())).wkSendMsg);
                        }
                    }
                }
            }
        }
    }


    public void sendMessage(WKMsg msg) {
        if (TextUtils.isEmpty(msg.fromUID)) {
            msg.fromUID = WKIMApplication.getInstance().getUid();
        }
        if (msg.expireTime > 0) {
            msg.expireTimestamp = DateUtils.getInstance().getCurrentSeconds() + msg.expireTime;
        }
        boolean hasAttached = false;
        //如果是图片消息
        if (msg.baseContentMsgModel instanceof WKImageContent imageContent) {
            if (!TextUtils.isEmpty(imageContent.localPath)) {
//                try {
//                    File file = new File(imageContent.localPath);
//                    if (file.exists() && file.length() > 0) {
//                        hasAttached = true;
//                        Bitmap bitmap = BitmapFactory.decodeFile(imageContent.localPath);
//                        if (bitmap != null) {
//                            imageContent.width = bitmap.getWidth();
//                            imageContent.height = bitmap.getHeight();
//                            msg.baseContentMsgModel = imageContent;
//                        }
//                    }
//                } catch (Exception ignored) {
//                }

                try {
                    File file = new File(imageContent.localPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
                        // 使用 Options 只解码尺寸信息
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true; // 只获取图片信息,不加载到内存
                        BitmapFactory.decodeFile(imageContent.localPath, options);

                        imageContent.width = options.outWidth;
                        imageContent.height = options.outHeight;
                        msg.baseContentMsgModel = imageContent;
                    }
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e("WKConnection", "Get image size failed: " + e.getMessage());
                }
            }
        }
        //视频消息
        if (msg.baseContentMsgModel instanceof WKVideoContent videoContent) {
            if (!TextUtils.isEmpty(videoContent.localPath)) {
                try {
                    File file = new File(videoContent.coverLocalPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
//                        Bitmap bitmap = BitmapFactory.decodeFile(videoContent.coverLocalPath);
//                        if (bitmap != null) {
//                            videoContent.width = bitmap.getWidth();
//                            videoContent.height = bitmap.getHeight();
//                            msg.baseContentMsgModel = videoContent;
//                        }

                        // 使用 Options 只解码尺寸信息
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true; // 只获取图片信息,不加载到内存
                        BitmapFactory.decodeFile(videoContent.coverLocalPath, options);

                        videoContent.width = options.outWidth;
                        videoContent.height = options.outHeight;
                        msg.baseContentMsgModel = videoContent;
                    }
                } catch (Exception ignored) {

                }
            }

        }
        saveSendMsg(msg);
        WKSendMsg sendMsg = WKProto.getInstance().getSendBaseMsg(msg);
        if (WKMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //如果是多媒体消息类型说明存在附件
            String url = ((WKMediaMessageContent) msg.baseContentMsgModel).url;
            if (TextUtils.isEmpty(url)) {
                String localPath = ((WKMediaMessageContent) msg.baseContentMsgModel).localPath;
                if (!TextUtils.isEmpty(localPath)) {
                    hasAttached = true;
                    ((WKMediaMessageContent) msg.baseContentMsgModel).localPath = FileUtils.getInstance().saveFile(localPath, msg.channelID, msg.channelType, msg.clientSeq + "");
                }
            }
            if (msg.baseContentMsgModel instanceof WKVideoContent) {
                String coverLocalPath = ((WKVideoContent) msg.baseContentMsgModel).coverLocalPath;
                if (!TextUtils.isEmpty(coverLocalPath)) {
                    ((WKVideoContent) msg.baseContentMsgModel).coverLocalPath = FileUtils.getInstance().saveFile(coverLocalPath, msg.channelID, msg.channelType, msg.clientSeq + "_1");
                    hasAttached = true;
                }
            }
            if (hasAttached) {
                JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
                if (jsonObject != null) {
                    msg.content = jsonObject.toString();
                } else {
                    msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                }
                WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
            }
        }
        //获取发送者信息
        WKChannel from = WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL);
        if (from == null) {
            WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL, channel -> WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel));
        } else {
            msg.setFrom(from);
        }
        //将消息push回UI层
        WKIM.getInstance().getMsgManager().setSendMsgCallback(msg);
        if (hasAttached) {
            //存在附件处理
            WKIM.getInstance().getMsgManager().setUploadAttachment(msg, (isSuccess, messageContent) -> {
                if (isSuccess) {
                    msg.baseContentMsgModel = messageContent;
                    JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
                    if (jsonObject != null) {
                        msg.content = jsonObject.toString();
                    } else {
                        msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                    }
                    WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
                    if (!sendingMsgHashMap.containsKey((int) msg.clientSeq)) {
                        WKSendMsg base1 = WKProto.getInstance().getSendBaseMsg(msg);
                        addSendingMsg(base1);
                        sendMessage(base1);
                    }
                } else {
                    MsgDbManager.getInstance().updateMsgStatus(msg.clientSeq, WKSendMsgResult.send_fail);
                }
            });
        } else {
            if (sendMsg != null) {
                if (msg.header != null && !msg.header.noPersist) {
                    addSendingMsg(sendMsg);
                }
                sendMessage(sendMsg);
            }
        }
    }

    public boolean connectionIsNull() {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，connectionIsNull检查失败");
                return true; // 保守起见，如果获取锁失败就认为连接为空
            }
            return connection == null || !connection.isOpen();
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private synchronized void startConnAckTimer() {
        // 移除之前的回调
        checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
        connAckTime = DateUtils.getInstance().getCurrentSeconds();
        // 开始新的检查
        checkConnAckHandler.postDelayed(checkConnAckRunnable, 1000);
    }

    private void saveSendMsg(WKMsg msg) {
        if (msg.setting == null) msg.setting = new WKMsgSetting();
        JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
        msg.content = jsonObject.toString();
        long tempOrderSeq = MsgDbManager.getInstance().queryMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
        msg.orderSeq = tempOrderSeq + 1;
        // 需要存储的消息入库后更改消息的clientSeq
        if (!msg.header.noPersist) {
            msg.clientSeq = (int) MsgDbManager.getInstance().insert(msg);
            if (msg.clientSeq > 0) {
                WKUIConversationMsg uiMsg = WKIM.getInstance().getConversationManager().updateWithWKMsg(msg);
                if (uiMsg != null) {
                    long browseTo = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(uiMsg.channelID, uiMsg.channelType);
                    if (uiMsg.getRemoteMsgExtra() == null) {
                        uiMsg.setRemoteMsgExtra(new WKConversationMsgExtra());
                    }
                    uiMsg.getRemoteMsgExtra().browseTo = browseTo;
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, "getSendBaseMsg");
                }
            }
        }
    }

    public void stopAll() {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，stopAll失败");
                return;
            }

            // 先设置连接状态为失败
            WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.fail, "");
            // 清理连接相关资源
            closeConnect();
            // 关闭定时器管理器
            TimerManager.getInstance().shutdown();
            MessageHandler.getInstance().clearCacheData();
            // 移除所有Handler回调
            if (checkRequestAddressHandler != null) {
                checkRequestAddressHandler.removeCallbacks(checkRequestAddressRunnable);
            }
            if (checkConnAckHandler != null) {
                checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
            }
            if (reconnectionHandler != null) {
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
            }

            // 重置所有状态
            connectStatus = WKConnectStatus.fail;
            isReConnecting = false;
            isConnecting = false;
            ip = "";
            port = 0;
            requestIPTime = 0;
            connAckTime = 0;
            lastMsgTime = 0;
            connCount = 0;

            // 清空发送消息队列
            if (sendingMsgHashMap != null) {
                sendingMsgHashMap.clear();
            }
            // 清理连接客户端
            connectionClient = null;

            // 关闭线程池
            shutdownExecutor();

            System.gc();
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private void closeConnect() {
        final INonBlockingConnection connectionToCloseActual;

        if (!isClosing.compareAndSet(false, true)) {
            WKLoggerUtils.getInstance().i(TAG, " Close operation already in progress");
            return;
        }

        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，closeConnect失败");
                isClosing.set(false);
                return;
            }

            if (connection == null) {
                isClosing.set(false);
                WKLoggerUtils.getInstance().i(TAG, " closeConnect called but connection is already null.");
                return;
            }
            connectionToCloseActual = connection;
            String connId = connectionToCloseActual.getId();

            try {
                connectionToCloseActual.setAttachment("closing_" + System.currentTimeMillis() + "_" + connId);
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "Failed to set closing attachment: " + e.getMessage());
            }

            connection = null;
            connectionClient = null;
            WKLoggerUtils.getInstance().i(TAG, " Connection object nulled, preparing for async close of: " + connId);
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }

        // Create a timeout handler to force close after timeout
        final Runnable timeoutRunnable = () -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    WKLoggerUtils.getInstance().w(TAG, " Connection close timeout reached for: " + connId);
                    connectionToCloseActual.close();
                }
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "Force close connection exception: " + e.getMessage());
            } finally {
                isClosing.set(false);
            }
        };

        // Schedule the timeout
        mainHandler.postDelayed(timeoutRunnable, CONNECTION_CLOSE_TIMEOUT);

        // Execute the close operation on a background thread
        Thread closeThread = new Thread(() -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    WKLoggerUtils.getInstance().i(TAG, " Attempting to close connection: " + connId);
                    connectionToCloseActual.close();
                    // Remove the timeout handler since we closed successfully
                    mainHandler.removeCallbacks(timeoutRunnable);
                    WKLoggerUtils.getInstance().i(TAG, " Successfully closed connection: " + connId);
                } else {
                    WKLoggerUtils.getInstance().i(TAG, " Connection was already closed or not open when async close executed: " + connectionToCloseActual.getId());
                }
            } catch (IOException e) {
                WKLoggerUtils.getInstance().e(TAG, "IOException during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "Exception during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } finally {
                synchronized (connectionLock) {
                    isClosing.set(false);
                    // Only trigger reconnection if we're still supposed to be connected
                    if (WKIMApplication.getInstance().isCanConnect && connectStatus != WKConnectStatus.kicked) {
                        mainHandler.postDelayed(() -> {
                            if (connectionIsNull() && !isClosing.get()) {
                                reconnection();
                            }
                        }, 1000);
                    }
                }
            }
        }, "ConnectionCloser");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    private boolean tryLockWithTimeout() {
        try {
            return connectionLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            WKLoggerUtils.getInstance().e(TAG, "获取锁被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
}