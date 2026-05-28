package com.xinbida.wukongim.message;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.xinbida.wukongim.BuildConfig;
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
import com.xinbida.wukongim.sync.SyncGate;
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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;

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

    public int getConnectStatus() {
        return connectStatus;
    }

    private volatile long lastMsgTime = 0;

    public long getLastMsgTime() {
        return lastMsgTime;
    }
    private String ip;
    private int port;
    public volatile INonBlockingConnection connection;
    volatile ConnectionClient connectionClient;

    /**
     * YUJ-2226: WebSocket 传输层（OkHttp）。与 {@link #connection}（xSocket TCP）互斥使用：
     * 同一时刻只有一条活跃链路。{@link #usingWebSocket} 标记当前激活的传输方式。
     *
     * <p>OkHttp listener 回调由内部 dispatcher 单线程串行投递，因此 WS 路径不再需要
     * 像 xSocket 那样在 onConnect/onData 上加 connectionLock 防御并发——锁仍保留，
     * 但 WS 路径不依赖它做 ordering。</p>
     */
    public volatile WebSocket webSocket;
    private volatile WebSocketConnectionClient webSocketClient;
    /** 当前是否走 OkHttp WebSocket（true）还是 xSocket TCP（false）。 */
    private volatile boolean usingWebSocket = false;
    /**
     * 共享的 OkHttpClient 实例。pingInterval=60s 提供 WS 控制帧心跳作为 wkproto Ping 的额外保活；
     * readTimeout=0 表示长连接不超时（消息间隔可能远大于默认 10s）。
     */
    private volatile OkHttpClient sharedHttpClient;
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
        if (BuildConfig.DEBUG) {
            Log.d("MsgDebug", "[Reconnect] triggered, caller=" + Thread.currentThread().getStackTrace()[3].getMethodName()
                    + " isClosing=" + isClosing.get() + " isReConnecting=" + isReConnecting
                    + " connStatus=" + connectStatus);
        }
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

        // YUJ-2226: 根据 MsgModel.getChatIp 返回的 ip 字段前缀分发到 WS / TCP 路径。
        // wss:// 或 ws:// 前缀 → OkHttp WebSocket；否则走原 xSocket TCP。
        // useWSS 灰度开关在 MsgModel 那一层已经决定不返回 ws/wss URL，所以此处无需额外判断。
        final boolean wantWebSocket = isWebSocketUrl(ip)
                && WKIMApplication.getInstance().isUseWSS();

        if (wantWebSocket) {
            connectViaWebSocket(executor);
        } else {
            connectViaTcp(executor);
        }
    }

    private static boolean isWebSocketUrl(String addr) {
        if (TextUtils.isEmpty(addr)) return false;
        String lower = addr.toLowerCase();
        return lower.startsWith("ws://") || lower.startsWith("wss://");
    }

    private OkHttpClient getOrCreateHttpClient() {
        OkHttpClient cached = sharedHttpClient;
        if (cached != null) return cached;
        synchronized (this) {
            if (sharedHttpClient == null) {
                sharedHttpClient = new OkHttpClient.Builder()
                        // YUJ-2226: WS 控制帧心跳，与上层 60s wkproto Ping 双层保活，更早感知断连
                        .pingInterval(60, TimeUnit.SECONDS)
                        // 长连接禁用读超时（消息间隔可能远大于默认值，受 wkproto Ping 兜底即可）
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(false)
                        .build();
            }
            return sharedHttpClient;
        }
    }

    /**
     * YUJ-2226: WebSocket 路径。等价于原 xSocket connSocket() 的功能，但用 OkHttp 实现。
     *
     * 关键差异：
     * - 不再用 CountDownLatch 等 onConnect。OkHttp listener 回调由 dispatcher 串行投递，
     *   onOpen 触发后我们直接发 ConnectPacket，让上层基于 connAck 推进状态机。
     * - 不再设置 idleTimeoutMillis / connectionTimeoutMillis（OkHttpClient 已配 connectTimeout
     *   + pingInterval，等价覆盖）。
     */
    private void connectViaWebSocket(ExecutorService executor) {
        try {
            executor.execute(() -> {
                try {
                    // 关闭现有连接
                    closeConnect();

                    final String newSocketId = UUID.randomUUID().toString().replace("-", "");
                    final String url = ip; // ip 字段此处为完整 wss:// / ws:// URL（见 MsgModel.getChatIp）

                    Request req;
                    try {
                        req = new Request.Builder().url(url).build();
                    } catch (IllegalArgumentException badUrl) {
                        WKLoggerUtils.getInstance().e(TAG, "无效的 WebSocket URL: " + url);
                        forcedReconnection();
                        return;
                    }

                    WebSocketConnectionClient newClient = new WebSocketConnectionClient(
                            newSocketId,
                            ws -> {
                                // onOpen 回调。此时已成功 HTTP Upgrade，发出 wkproto ConnectPacket。
                                isReConnecting = false;
                                connCount = 0;
                                sendConnectMsg();
                            });

                    OkHttpClient client = getOrCreateHttpClient();
                    if (BuildConfig.DEBUG) Log.d("MsgDebug", "[WKConnection] connecting to " + url);

                    // YUJ-2236 P0#2: 修复 onOpen 与字段赋值的 race。
                    // 旧实现先 newWebSocket() 异步建连，再赋值 usingWebSocket / webSocket。
                    // 在低延迟环境下 onOpen 可能抢先到达，sendConnectMsg → sendMessage 读到
                    // usingWebSocket=false 走 TCP 分支看到 connection==null，触发 reconnection 把
                    // CONNECT 包丢掉。修复：先在锁内把 usingWebSocket 翻 true、清 TCP 字段，再
                    // 调 newWebSocket，最后把返回的 WebSocket 引用写回——同样在锁内。onOpen 中的
                    // sendMessage 走 tryLockWithTimeout 会被该锁阻塞直到字段全部就绪。
                    boolean locked = false;
                    WebSocket newWs = null;
                    try {
                        locked = tryLockWithTimeout();
                        if (!locked) {
                            WKLoggerUtils.getInstance().e(TAG, "连接锁获取失败，放弃 WS 连接");
                            forcedReconnection();
                            return;
                        }
                        // 1) 先把 transport 标志和 TCP 字段就位
                        usingWebSocket = true;
                        connection = null;
                        connectionClient = null;
                        webSocketClient = newClient;
                        socketSingleID = newSocketId;

                        // 2) 在持锁状态下创建 WebSocket。即使 onOpen 在 dispatcher 线程立即触发，
                        //    其 sendConnectMsg → sendMessage 会因 tryLockWithTimeout 阻塞，
                        //    直到本块结束 webSocket 引用已写入。
                        newWs = client.newWebSocket(req, newClient);
                        webSocket = newWs;
                    } catch (Exception e) {
                        if (newWs != null) {
                            try { newWs.cancel(); } catch (Exception ignored) {}
                        }
                        throw e;
                    } finally {
                        if (locked) connectionLock.unlock();
                    }
                    // OkHttp 内部异步建连。后续状态推进由 onOpen / onFailure / onClosed 回调驱动；
                    // 这里不再 await——避免阻塞 WKConnection-Worker 线程；连接超时由 OkHttp connectTimeout 兜底。
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e(TAG, "WS 连接异常: " + e.getMessage() + " 地址：" + ip);
                    if (!executor.isShutdown()) {
                        forcedReconnection();
                    }
                } finally {
                    setConnectingState(false);
                }
            });
        } catch (RejectedExecutionException e) {
            WKLoggerUtils.getInstance().e(TAG, "WS 连接任务被拒绝执行: " + e.getMessage());
            setConnectingState(false);
        }
    }

    /**
     * 原 xSocket TCP 连接路径。useWSS=false 或服务端未下发 ws/wss 地址时仍然可用。
     */
    private void connectViaTcp(ExecutorService executor) {
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
                        // YUJ-2236 P1: 与 connectViaWebSocket / closeConnect / handleLoginStatus
                        // 等路径统一使用 ReentrantLock（tryLockWithTimeout）。原 synchronized
                        // (connectionLock) 走的是 Java 内置 monitor，与 ReentrantLock 互不互斥，
                        // transport 切换时存在「字段半发布」窗口。
                        INonBlockingConnection currentConn = null;
                        boolean cbLocked = false;
                        try {
                            cbLocked = tryLockWithTimeout();
                            if (!cbLocked) {
                                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，TCP onConnect 回调读取 connection 失败");
                                connectLatch.countDown();
                                return;
                            }
                            currentConn = connection;
                        } finally {
                            if (cbLocked) connectionLock.unlock();
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
                    if (BuildConfig.DEBUG) Log.d("MsgDebug", "[WKConnection] connecting to tcp://" + ip + ":" + port);
                    INonBlockingConnection newConnection = new NonBlockingConnection(ip, port, newClient);
                    newConnection.setAttachment(newSocketId);

                    // 原子性地更新连接相关的字段（YUJ-2236 P1: 与 WS 路径统一使用 ReentrantLock）
                    boolean tcpLocked = false;
                    try {
                        tcpLocked = tryLockWithTimeout();
                        if (!tcpLocked) {
                            WKLoggerUtils.getInstance().e(TAG, "获取锁超时，TCP 字段更新失败");
                            try { newConnection.close(); } catch (Exception ignored) {}
                            if (!executor.isShutdown()) {
                                forcedReconnection();
                            }
                            return;
                        }
                        connectionClient = newClient;
                        connection = newConnection;
                        webSocket = null;
                        webSocketClient = null;
                        usingWebSocket = false;
                        socketSingleID = newSocketId;
                    } finally {
                        if (tcpLocked) connectionLock.unlock();
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

    // 使用CAS操作设置连接状态（YUJ-2236 P1: 与其它路径统一走 ReentrantLock）
    private boolean setConnectingState(boolean connecting) {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，setConnectingState 失败");
                return false;
            }
            if (connecting && isConnecting) {
                return false;
            }
            isConnecting = connecting;
            return true;
        } finally {
            if (locked) connectionLock.unlock();
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        startConnAckTimer();
        sendMessage(new WKConnectMsg());
    }

    void receivedData(byte[] data) {
        if (BuildConfig.DEBUG) Log.d("MsgDebug", "[WKConnection.receivedData] dataLen=" + (data != null ? data.length : 0));
        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
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
                        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
                        if (BuildConfig.DEBUG) Log.d("MsgDebug", "[Heartbeat] PONG received, lastMsgTime=" + lastMsgTime);
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
                                // YUJ-2236 P0#1: WSS 路径下 connection 永远是 null，必须用 !connectionIsNull()
                                // 才能同时覆盖 WS（webSocket != null）和 TCP（connection != null）两条传输。
                                if (!connectionIsNull() && !isClosing.get()) {
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
                    //  (fixing  ReviewBot P1-#3) · 这里是 PR#217 原 body
                    // 宣称「5 条 sync 路径」里漏掉的第 5 条：连接成功 + READ 模式会在此触发
                    // conversation sync。走统一的 SyncGate，让 SpaceSyncCoordinator 能
                    // debounce 与 performSpaceSwitch / spaceResync 并发的冗余 sync。
                    // 注意：即使守卫拒绝 sync，仍要执行连接状态更新（connectStatus /
                    // sendAck / resendMsg），否则连接会「语义上没有成功」。
                    final Runnable markConnected = () -> {
                        boolean innerLocked = false;
                        try {
                            innerLocked = tryLockWithTimeout();
                            if (!innerLocked) {
                                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，setSyncConversationListener 连接态更新失败");
                                return;
                            }
                            // YUJ-2236 P0#1: WSS 路径下 connection 永远是 null，必须用 !connectionIsNull()
                            // 才能同时覆盖 WS 和 TCP 两条传输，避免登录后永久卡在 syncMsg。
                            if (!connectionIsNull() && !isClosing.get()) {
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
                    };

                    if (!SyncGate.allow("wkConnectionSync")) {
                        // 守卫已被上层注册且拒绝本次 sync（已有 sync 进行中 / 500ms 内
                        // 重复触发）。不调 setSyncConversationListener，避免 saveSyncChat 与
                        // 在途 sync 并发写 DB；但连接态必须立即推进，否则 UI 永远停在「连接中」。
                        markConnected.run();
                    } else {
                        WKIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                            try {
                                markConnected.run();
                            } finally {
                                SyncGate.done();
                            }
                        });
                    }
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

            // YUJ-2226: 根据当前激活的传输层分发写路径。两条路径返回值约定一致。
            int status;
            if (usingWebSocket) {
                WebSocket currentWs = this.webSocket;
                if (currentWs == null) {
                    WKLoggerUtils.getInstance().w(TAG, " sendMessage(WS): WebSocket is null, attempting reconnection for: " + mBaseMsg.packetType);
                    reconnection();
                    return;
                }
                status = MessageHandler.getInstance().sendMessage(currentWs, mBaseMsg);
                if (BuildConfig.DEBUG && mBaseMsg.packetType == WKMsgType.PING) {
                    Log.d("MsgDebug", "[Heartbeat] PING sent (WS), writeStatus=" + status);
                }
            } else {
                INonBlockingConnection currentConnection = this.connection;
                if (currentConnection == null || !currentConnection.isOpen()) {
                    WKLoggerUtils.getInstance().w(TAG, " sendMessage: Connection is null or not open, attempting reconnection for: " + mBaseMsg.packetType);
                    reconnection();
                    return;
                }
                status = MessageHandler.getInstance().sendMessage(currentConnection, mBaseMsg);
                if (BuildConfig.DEBUG && mBaseMsg.packetType == WKMsgType.PING) {
                    Log.d("MsgDebug", "[Heartbeat] PING sent, writeStatus=" + status
                            + " connOpen=" + currentConnection.isOpen());
                }
            }
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
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(imageContent.localPath, options);

                        imageContent.width = options.outWidth;
                        imageContent.height = options.outHeight;

                        // 修正文件扩展名：AI 生成图片可能保存为 .png 但实际是 jpeg/webp
                        imageContent.localPath = fixImageExtension(imageContent.localPath, options.outMimeType);

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

    /**
     * 检测图片文件的真实 MIME 类型，如果与文件扩展名不匹配则重命名文件。
     * 解决 AI 生成图片（如豆包）保存为 .png 但实际内容为 jpeg/webp 导致服务器拒绝上传的问题。
     */
    private String fixImageExtension(String filePath, String actualMimeType) {
        if (TextUtils.isEmpty(actualMimeType) || TextUtils.isEmpty(filePath)) return filePath;

        String correctExt;
        switch (actualMimeType) {
            case "image/jpeg": correctExt = "jpg"; break;
            case "image/png": correctExt = "png"; break;
            case "image/webp": correctExt = "webp"; break;
            case "image/gif": correctExt = "gif"; break;
            default: return filePath;
        }

        int dotIndex = filePath.lastIndexOf(".");
        if (dotIndex == -1) return filePath;

        String currentExt = filePath.substring(dotIndex + 1).toLowerCase();
        if (correctExt.equals(currentExt)) return filePath;

        // 扩展名不匹配，重命名文件
        String newPath = filePath.substring(0, dotIndex + 1) + correctExt;
        File oldFile = new File(filePath);
        File newFile = new File(newPath);
        if (oldFile.renameTo(newFile)) {
            return newPath;
        }
        return filePath;
    }

    public boolean connectionIsNull() {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，connectionIsNull检查失败");
                return true; // 保守起见，如果获取锁失败就认为连接为空
            }
            // YUJ-2226: 两条路径任一活跃即视为非空。
            if (usingWebSocket) {
                return webSocket == null;
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
            // 清理连接客户端（TCP + WS 两路）
            connectionClient = null;
            // YUJ-2226: 同步清理 WebSocket 状态，避免 stopAll 后残留引用阻止下次重连。
            webSocketClient = null;
            usingWebSocket = false;

            // 关闭线程池
            shutdownExecutor();

            System.gc();
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    /**
     * YUJ-2226: WebSocketConnectionClient 在 onClosed / onFailure 时回调到这里，触发重连。
     *
     * @param ws      触发回调的 WebSocket
     * @param planned true=本端主动关闭（onClosed code=1000），false=异常断开
     */
    void handleWebSocketDisconnected(WebSocket ws, boolean planned) {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，handleWebSocketDisconnected 失败");
                return;
            }
            // 仅处理当前 ws 的断开事件
            if (ws != null && webSocket != null && ws != webSocket) {
                return;
            }
            // 把当前 ws 引用清空，让 connectionIsNull() 立刻返回 true
            webSocket = null;
            webSocketClient = null;
        } finally {
            if (locked) connectionLock.unlock();
        }

        if (planned) {
            WKLoggerUtils.getInstance().i(TAG, "WebSocket 主动断开，不触发重连");
            return;
        }
        if (!WKIMApplication.getInstance().isCanConnect || isClosing.get()) {
            return;
        }
        // 与 ConnectionClient.onDisconnect 的语义一致：异常断开 → 重连
        forcedReconnection();
    }

    private void closeConnect() {
        if (!isClosing.compareAndSet(false, true)) {
            WKLoggerUtils.getInstance().i(TAG, " Close operation already in progress");
            return;
        }

        // YUJ-2226: WebSocket 路径走独立的关闭流程。OkHttp WebSocket 自带异步关闭语义，
        // 无需像 xSocket 那样起单独的 ConnectionCloser 线程 + timeout 兜底。
        WebSocket wsToClose = null;
        boolean closeWs = false;
        boolean lockedWs = false;
        try {
            lockedWs = tryLockWithTimeout();
            if (!lockedWs) {
                WKLoggerUtils.getInstance().e(TAG, "获取锁超时，closeConnect(WS) 失败");
                isClosing.set(false);
                return;
            }
            if (usingWebSocket) {
                closeWs = true;
                wsToClose = webSocket;
                webSocket = null;
                webSocketClient = null;
                // 不立即把 usingWebSocket 翻 false——避免在重连过程中被误判为 TCP 路径走错分支。
                // 下一次 connSocket() 会按地址前缀重新设置 usingWebSocket。
            }
        } finally {
            if (lockedWs) connectionLock.unlock();
        }

        if (closeWs) {
            try {
                if (wsToClose != null) {
                    // 1000=normal closure。OkHttp 会异步完成 close handshake。
                    boolean accepted = wsToClose.close(1000, "client close");
                    if (!accepted) {
                        // close 已在进行中或 socket 已关闭——直接 cancel 避免泄漏。
                        wsToClose.cancel();
                    }
                }
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "关闭 WebSocket 异常: " + e.getMessage());
            } finally {
                isClosing.set(false);
            }
            return;
        }

        // 以下为原 xSocket TCP 关闭路径（保留作为 useWSS=false 时的回退）。
        final INonBlockingConnection connectionToCloseActual;

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
                // YUJ-2236 P1: 与其它路径统一使用 ReentrantLock，避免与 synchronized 互不互斥导致
                // 字段半发布。
                boolean closeLocked = false;
                try {
                    closeLocked = tryLockWithTimeout();
                    if (!closeLocked) {
                        WKLoggerUtils.getInstance().e(TAG, "获取锁超时，close finally 路径退化为无锁更新");
                    }
                    isClosing.set(false);
                    // Only trigger reconnection if we're still supposed to be connected
                    if (WKIMApplication.getInstance().isCanConnect && connectStatus != WKConnectStatus.kicked) {
                        mainHandler.postDelayed(() -> {
                            if (connectionIsNull() && !isClosing.get()) {
                                reconnection();
                            }
                        }, 1000);
                    }
                } finally {
                    if (closeLocked) connectionLock.unlock();
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