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

package com.chat.uikit.chat.manager;

import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.WKSendMsgMenu;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgcontent.WKFileContent;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.WKDBHelper;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.interfaces.IUploadAttacResultListener;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2019-11-20 13:20
 * 发送消息管理
 */
public class WKSendMsgUtils {
    interface FileUploadTransport {
        void getCredentials(String channelID, byte channelType, String path, WKUploader.IGetUploadCredentials callback);
        void put(String uploadUrl, String path, String contentType, String contentDisposition, Object tag, WKUploader.IUploadBack callback);
    }

    private static volatile FileUploadTransport fileUploadTransport;

    static void setFileUploadTransportForTests(FileUploadTransport transport) { fileUploadTransport = transport; }

    private static FileUploadTransport getFileUploadTransport() {
        FileUploadTransport injected = fileUploadTransport;
        if (injected != null) return injected;
        return new FileUploadTransport() {
            @Override public void getCredentials(String channelID, byte channelType, String path, WKUploader.IGetUploadCredentials callback) {
                WKUploader.getInstance().getUploadCredentials(channelID, channelType, path, callback);
            }
            @Override public void put(String uploadUrl, String path, String contentType, String contentDisposition, Object tag, WKUploader.IUploadBack callback) {
                WKUploader.getInstance().putUpload(uploadUrl, path, contentType, contentDisposition, tag, callback);
            }
        };
    }
    private WKSendMsgUtils() {

    }

    private static class SendMsgUtilsBinder {
        private static final WKSendMsgUtils utils = new WKSendMsgUtils();
    }

    public static WKSendMsgUtils getInstance() {
        return SendMsgUtilsBinder.utils;
    }

    public void sendMessage(WKMsg wkMsg) {
        WKSendOptions options = new WKSendOptions();
        options.robotID = wkMsg.robotID;
        WKChannel channel = wkMsg.getChannelInfo();
        if (channel == null) {
            channel = new WKChannel(wkMsg.channelID, wkMsg.channelType);
        }
        EndpointManager.getInstance().invokes(EndpointSID.sendMessage, new WKSendMsgMenu(channel, options));
        WKIM.getInstance().getMsgManager().sendWithOptions(wkMsg.baseContentMsgModel, channel, options);
    }

    public void sendMessages(List<SendMsgEntity> list) {
        final Timer[] timer = {new Timer()};
        final int[] i = {0};
        timer[0].schedule(new TimerTask() {
            @Override
            public void run() {
                if (i[0] == list.size() - 1) {
                    timer[0].cancel();
                    timer[0] = null;
                }
                WKMsg wkMsg = new WKMsg();
                wkMsg.channelID = list.get(i[0]).wkChannel.channelID;
                wkMsg.channelType = list.get(i[0]).wkChannel.channelType;
                wkMsg.type = list.get(i[0]).messageContent.type;
                wkMsg.baseContentMsgModel = list.get(i[0]).messageContent;
                sendMessage(wkMsg);
                i[0]++;
            }
        }, 0, 150);
    }

    /**
     * 上传聊天附件
     *
     * @param msg      消息
     * @param listener 上传返回
     */
    void uploadChatAttachment(WKMsg msg, IUploadAttacResultListener listener) {
        if (msg.type == WKContentType.WK_IMAGE || msg.type == WKContentType.WK_GIF || msg.type == WKContentType.WK_VOICE || msg.type == WKContentType.WK_LOCATION || msg.type == WKContentType.WK_FILE) {
            WKMediaMessageContent contentMsgModel = (WKMediaMessageContent) msg.baseContentMsgModel;
            if (msg.type == WKContentType.WK_FILE) {
                uploadFileAttachment(msg, (WKFileContent) contentMsgModel, listener);
                return;
            }
            if (!TextUtils.isEmpty(contentMsgModel.url)) {
                listener.onUploadResult(true, contentMsgModel);
                return;
            }
            if (TextUtils.isEmpty(contentMsgModel.localPath)) {
                listener.onUploadResult(false, msg.baseContentMsgModel);
                return;
            }
            WKUploader.getInstance().getUploadCredentials(msg.channelID, msg.channelType, contentMsgModel.localPath,
                    (uploadUrl, downloadUrl, contentType, contentDisposition) -> {
                        if (TextUtils.isEmpty(uploadUrl) || TextUtils.isEmpty(downloadUrl)) {
                            listener.onUploadResult(false, contentMsgModel);
                            return;
                        }
                        WKUploader.getInstance().putUpload(uploadUrl, contentMsgModel.localPath, contentType, contentDisposition, msg.clientSeq,
                                new WKUploader.IUploadBack() {
                                    @Override
                                    public void onSuccess(String url) {
                                        contentMsgModel.url = downloadUrl;
                                        listener.onUploadResult(true, contentMsgModel);
                                    }

                                    @Override
                                    public void onError() {
                                        listener.onUploadResult(false, contentMsgModel);
                                    }
                                });
                    });
        } else if (msg.type == WKContentType.WK_VIDEO) {
            WKVideoContent videoMsgModel = (WKVideoContent) msg.baseContentMsgModel;
            if (!TextUtils.isEmpty(videoMsgModel.cover) && !TextUtils.isEmpty(videoMsgModel.url)) {
                listener.onUploadResult(true, msg.baseContentMsgModel);
                return;
            }
            if (TextUtils.isEmpty(videoMsgModel.cover)) {
                uploadVideoCover(msg, videoMsgModel, listener);
            } else {
                uploadVideoFile(msg, videoMsgModel, listener);
            }
        }
    }

    private void uploadFileAttachment(WKMsg msg, WKFileContent content, IUploadAttacResultListener listener) {
        WKIMApplication.SessionContext capturedContext = msg.uploadSessionContext;
        if (capturedContext == null || !WKIMApplication.getInstance().isSessionCurrent(capturedContext)
                || !TextUtils.equals(capturedContext.clientMsgNo, msg.clientMsgNO)
                || capturedContext.clientSeq != msg.clientSeq) {
            if (capturedContext != null) MsgDbManager.getInstance().updateCapturedMsgSendFail(capturedContext.helper, msg.clientMsgNO, msg.clientSeq);
            return;
        }
        WKConfig.SessionSnapshot capturedSession = WKConfig.getInstance().getSessionSnapshot();
        WKIMApplication app = WKIMApplication.getInstance();
        String capturedUid = capturedContext.uid;
        WKDBHelper capturedHelper = capturedContext.helper;
        final String clientMsgNo = msg.clientMsgNO;
        final long clientSeq = msg.clientSeq;
        final String channelId = msg.channelID;
        final byte channelType = msg.channelType;
        WKProgressManager.FileUploadProgressKey progressKey = new WKProgressManager.FileUploadProgressKey(capturedSession.uid, capturedSession.version, clientMsgNo, clientSeq);
        FileUploadTerminal terminal = new FileUploadTerminal();
        FileUploadTransport transport = getFileUploadTransport();
        final WKConfig.SessionListener[] sessionListener = new WKConfig.SessionListener[1];
        Runnable cancel = () -> {
            if (!terminal.finish(() -> WKConfig.getInstance().removeSessionListener(sessionListener[0]))) return;
            MsgDbManager.getInstance().updateCapturedMsgSendFail(capturedHelper, clientMsgNo, clientSeq);
            WKProgressManager.Companion.getInstance().completeUploadFailure(progressKey, "attachment upload failed");
        };
        sessionListener[0] = snapshot -> {
            if (snapshot.version != capturedSession.version || !TextUtils.equals(snapshot.uid, capturedSession.uid)
                    || !TextUtils.equals(snapshot.token, capturedSession.token)) cancel.run();
        };
        WKConfig.getInstance().addSessionListener(sessionListener[0]);
        if (!isCurrent(capturedSession, capturedUid, capturedHelper, capturedContext)) { cancel.run(); return; }
        IUploadAttacResultListener result = (success, value) -> completeFileUpload(terminal, sessionListener[0], progressKey,
                clientMsgNo, clientSeq, listener, capturedSession, capturedUid, capturedHelper, capturedContext, success, value);

        if (!TextUtils.isEmpty(content.url)) { result.onUploadResult(true, content); return; }
        if (TextUtils.isEmpty(content.localPath)) { result.onUploadResult(false, content); return; }
        try {
            transport.getCredentials(channelId, channelType, content.localPath, (uploadUrl, downloadUrl, contentType, contentDisposition) -> {
                if (terminal.isTerminal()) return;
                if (!isCurrent(capturedSession, capturedUid, capturedHelper, capturedContext)) { cancel.run(); return; }
                if (TextUtils.isEmpty(uploadUrl) || TextUtils.isEmpty(downloadUrl)) { result.onUploadResult(false, content); return; }
                try {
                    terminal.runIfActive(() -> transport.put(uploadUrl, content.localPath, contentType, contentDisposition, progressKey, new WKUploader.IUploadBack() {
                        @Override public void onSuccess(String url) {
                            if (terminal.isTerminal()) return;
                            if (!isCurrent(capturedSession, capturedUid, capturedHelper, capturedContext)) { cancel.run(); return; }
                            completeFileUpload(terminal, sessionListener[0], progressKey, clientMsgNo, clientSeq,
                                    listener, capturedSession, capturedUid, capturedHelper, capturedContext, true, content,
                                    () -> content.url = downloadUrl);
                        }
                        @Override public void onError() { result.onUploadResult(false, content); }
                    }));
                } catch (RuntimeException error) { result.onUploadResult(false, content); }
            });
        } catch (RuntimeException error) { result.onUploadResult(false, content); }
    }

    private static void completeFileUpload(FileUploadTerminal terminal, WKConfig.SessionListener sessionListener,
                                           WKProgressManager.FileUploadProgressKey progressKey,
                                           String clientMsgNo, long clientSeq, IUploadAttacResultListener listener,
                                           WKConfig.SessionSnapshot session, String uid, WKDBHelper helper,
                                           WKIMApplication.SessionContext context,
                                           boolean success, com.xinbida.wukongim.msgmodel.WKMessageContent value) {
        completeFileUpload(terminal, sessionListener, progressKey, clientMsgNo, clientSeq, listener,
                session, uid, helper, context, success, value, null);
    }

    private static void completeFileUpload(FileUploadTerminal terminal, WKConfig.SessionListener sessionListener,
                                           WKProgressManager.FileUploadProgressKey progressKey,
                                           String clientMsgNo, long clientSeq, IUploadAttacResultListener listener,
                                           WKConfig.SessionSnapshot session, String uid, WKDBHelper helper,
                                           WKIMApplication.SessionContext context,
                                           boolean success, com.xinbida.wukongim.msgmodel.WKMessageContent value,
                                           Runnable successMutation) {
        if (!terminal.finish(() -> WKConfig.getInstance().removeSessionListener(sessionListener))) return;
        if (!isCurrent(session, uid, helper, context)) {
            MsgDbManager.getInstance().updateCapturedMsgSendFail(helper, clientMsgNo, clientSeq);
            WKProgressManager.Companion.getInstance().completeUploadFailure(progressKey, "attachment upload failed");
            return;
        }
        if (success && successMutation != null) successMutation.run();
        boolean persistedSuccess = false;
        try {
            boolean accepted = listener.onUploadResult(context, success, value);
            persistedSuccess = accepted && success;
            if (!isCurrent(session, uid, helper, context)) {
                MsgDbManager.getInstance().updateCapturedMsgSendFail(helper, clientMsgNo, clientSeq);
                persistedSuccess = false;
            }
        } catch (RuntimeException error) {
            MsgDbManager.getInstance().updateCapturedMsgSendFail(helper, clientMsgNo, clientSeq);
        } finally {
            try {
                if (persistedSuccess) WKProgressManager.Companion.getInstance().completeUploadSuccess(progressKey, null);
                else WKProgressManager.Companion.getInstance().completeUploadFailure(progressKey, "attachment upload failed");
            } catch (RuntimeException ignored) { }
        }
    }

    static final class FileUploadTerminal {
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        synchronized boolean claim() { return terminal.compareAndSet(false, true); }
        synchronized boolean finish(Runnable action) {
            if (!claim()) return false;
            action.run();
            return true;
        }
        synchronized boolean runIfActive(Runnable action) {
            if (terminal.get()) return false;
            action.run();
            return true;
        }
        boolean isTerminal() { return terminal.get(); }
    }

    private static boolean isCurrent(WKConfig.SessionSnapshot captured, String uid, WKDBHelper helper,
                                     WKIMApplication.SessionContext context) {
        WKConfig.SessionSnapshot current = WKConfig.getInstance().getSessionSnapshot();
        WKIMApplication app = WKIMApplication.getInstance();
        return captured.version == current.version && TextUtils.equals(captured.uid, current.uid)
                && TextUtils.equals(captured.token, current.token) && TextUtils.equals(uid, app.getUid())
                && TextUtils.equals(captured.uid, uid) && app.getDbHelper() == helper
                && app.isSessionCurrent(context);
    }

    private void uploadVideoCover(WKMsg msg, WKVideoContent videoMsgModel, IUploadAttacResultListener listener) {
        WKUploader.getInstance().getUploadCredentialsWithMime(msg.channelID, msg.channelType,
                videoMsgModel.coverLocalPath, ".jpg", "image/jpeg",
                (coverUploadUrl, coverDownloadUrl, coverContentType, coverContentDisposition) -> {
                    if (TextUtils.isEmpty(coverUploadUrl) || TextUtils.isEmpty(coverDownloadUrl)) {
                        listener.onUploadResult(false, msg.baseContentMsgModel);
                        return;
                    }
                    WKUploader.getInstance().putUpload(coverUploadUrl, videoMsgModel.coverLocalPath,
                            coverContentType, coverContentDisposition,
                            UUID.randomUUID().toString().replaceAll("-", ""),
                            new WKUploader.IUploadBack() {
                                @Override
                                public void onSuccess(String url) {
                                    videoMsgModel.cover = coverDownloadUrl;
                                    uploadVideoFile(msg, videoMsgModel, listener);
                                }

                                @Override
                                public void onError() {
                                    listener.onUploadResult(false, msg.baseContentMsgModel);
                                }
                            });
                });
    }

    private void uploadVideoFile(WKMsg msg, WKVideoContent videoMsgModel, IUploadAttacResultListener listener) {
        WKUploader.getInstance().getUploadCredentials(msg.channelID, msg.channelType, videoMsgModel.localPath,
                (videoUploadUrl, videoDownloadUrl, videoContentType, videoContentDisposition) -> {
                    if (TextUtils.isEmpty(videoUploadUrl) || TextUtils.isEmpty(videoDownloadUrl)) {
                        listener.onUploadResult(false, videoMsgModel);
                        return;
                    }
                    WKUploader.getInstance().putUpload(videoUploadUrl, videoMsgModel.localPath,
                            videoContentType, videoContentDisposition, msg.clientSeq,
                            new WKUploader.IUploadBack() {
                                @Override
                                public void onSuccess(String url) {
                                    videoMsgModel.url = videoDownloadUrl;
                                    listener.onUploadResult(true, videoMsgModel);
                                }

                                @Override
                                public void onError() {
                                    listener.onUploadResult(false, videoMsgModel);
                                }
                            });
                });
    }
}
