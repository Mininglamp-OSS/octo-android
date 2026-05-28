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

package com.xinbida.wukongim.message;

import androidx.annotation.Nullable;

import com.xinbida.wukongim.BuildConfig;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * YUJ-2245: WebSocket transport listener — WebSocket-only 客户端的唯一长连接 listener。
 *
 * Responsibilities：
 * <ul>
 *   <li>{@code onOpen}     — 连接建立，触发 wkproto ConnectPacket</li>
 *   <li>{@code onMessage}  — 收 binary 帧 → wkproto 解码</li>
 *   <li>{@code onClosing} / {@code onClosed} — 对端要求关闭 / 链路落地</li>
 *   <li>{@code onFailure}  — 连接异常 / 空闲超时 → 重连</li>
 * </ul>
 *
 * OkHttp 的 listener 回调由内部 dispatcher 单线程串行投递，
 * 因此 onOpen/onMessage 不需要在外侧加锁做 ordering——竞态在源头就被消除。
 *
 * The listener does not own any wkproto 状态机；它把字节交给 {@link WKConnection#receivedData}
 * 与 {@link MessageHandler#cutBytes} 复用编解码层。这是 WSS 迁移的核心保证：
 * 协议层零改动，只换传输层。
 */
class WebSocketConnectionClient extends WebSocketListener {
    private static final String TAG = "WSConnectionClient";

    interface IConnResult {
        void onResult(WebSocket webSocket);
    }

    private final IConnResult iConnResult;
    private final String socketSingleId;
    private volatile boolean isConnectSuccess;

    WebSocketConnectionClient(String socketSingleId, IConnResult iConnResult) {
        this.iConnResult = iConnResult;
        this.socketSingleId = socketSingleId;
        this.isConnectSuccess = false;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        // 对端接受了 WS Upgrade（HTTP 101）。此时 wkproto 仍未握手，需要由 WKConnection
        // 通过 sendConnectMsg() 发出 ConnectPacket，等服务端 ConnectAck 后状态才进入 success。
        isConnectSuccess = true;
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MsgDebug", "[WS] onOpen socketSingleId=" + socketSingleId
                    + " httpCode=" + (response != null ? response.code() : -1));
        }
        WKLoggerUtils.getInstance().i(TAG, "WebSocket onOpen socketSingleId=" + socketSingleId);
        if (iConnResult != null) {
            iConnResult.onResult(webSocket);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        if (!isCurrentSocket(webSocket)) {
            WKLoggerUtils.getInstance().w(TAG, "丢弃非当前 WS 的消息 socketSingleId=" + socketSingleId);
            return;
        }
        // wkproto 二进制帧。直接交给 receivedData → cutBytes 解析。
        byte[] data = bytes.toByteArray();
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MsgDebug", "[WS] onMessage(binary) len=" + data.length);
        }
        WKConnection.getInstance().receivedData(data);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        // 移动端走二进制 wkproto，理论上不会收到 text 帧；记日志但不参与解码，避免污染 cacheData。
        WKLoggerUtils.getInstance().w(TAG, "收到非预期文本帧，丢弃 len=" + (text == null ? 0 : text.length()));
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        WKLoggerUtils.getInstance().i(TAG, "WebSocket onClosing code=" + code + " reason=" + reason);
        // 对端要求关闭。按 RFC6455 流程响应 close frame 让链路优雅落地。
        try {
            webSocket.close(1000, null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        WKLoggerUtils.getInstance().i(TAG, "WebSocket onClosed code=" + code + " reason=" + reason);
        if (!isCurrentSocket(webSocket)) {
            // 旧 socket 收到延迟的 close，不应触发重连。
            return;
        }
        WKConnection.getInstance().handleWebSocketDisconnected(webSocket, /*planned=*/ code == 1000);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
        WKLoggerUtils.getInstance().e(TAG, "WebSocket onFailure socketSingleId=" + socketSingleId
                + " err=" + (t == null ? "null" : t.getClass().getSimpleName() + ":" + t.getMessage())
                + " httpCode=" + (response == null ? -1 : response.code()));
        if (!isCurrentSocket(webSocket)) {
            return;
        }
        if (!WKIMApplication.getInstance().isCanConnect) {
            return;
        }
        WKConnection.getInstance().handleWebSocketDisconnected(webSocket, /*planned=*/ false);
    }

    private boolean isCurrentSocket(WebSocket webSocket) {
        // 非当前 socket 的回调（迟到的旧连接事件）不该触发任何状态变更。
        WebSocket cur = WKConnection.getInstance().webSocket;
        return cur != null && cur == webSocket;
    }
}
