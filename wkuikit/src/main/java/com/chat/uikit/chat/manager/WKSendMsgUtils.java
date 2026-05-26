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
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.ud.WKUploader;
import com.xinbida.wukongim.WKIM;
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

/**
 * 2019-11-20 13:20
 * 发送消息管理
 */
public class WKSendMsgUtils {
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
