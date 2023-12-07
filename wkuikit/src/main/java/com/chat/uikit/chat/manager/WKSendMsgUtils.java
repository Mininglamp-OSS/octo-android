package com.chat.uikit.chat.manager;

import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.ud.WKUploader;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.interfaces.IUploadAttacResultListener;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
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

    final String AutoDeleteMessage = "msg_auto_delete";

    public void sendMessage(WKMsg wkMsg) {
        EndpointManager.getInstance().invoke("send_message", wkMsg);
        WKIM.getInstance().getMsgManager().sendMessage(wkMsg);
    }

    /**
     * 发送消息
     *
     * @param messageContent 消息model
     */
    public void sendMessage(WKMessageContent messageContent, WKMsgSetting msgSetting, String channelID, byte channelType) {
        WKIM.getInstance().getMsgManager().sendMessage(messageContent, msgSetting, channelID, channelType);
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
                WKIM.getInstance().getMsgManager().sendMessage(list.get(i[0]).messageContent, list.get(i[0]).wkChannel.channelID, list.get(i[0]).wkChannel.channelType);
                i[0]++;
            }
        }, 0, 100);
    }

    /**
     * 上传聊天附件
     *
     * @param msg      消息
     * @param listener 上传返回
     */
    void uploadChatAttachment(WKMsg msg, IUploadAttacResultListener listener) {
        //存在附件待上传
        if (msg.type == WKContentType.WK_IMAGE || msg.type == WKContentType.WK_GIF || msg.type == WKContentType.WK_VOICE || msg.type == WKContentType.WK_LOCATION || msg.type == WKContentType.WK_FILE) {
            WKMediaMessageContent contentMsgModel = (WKMediaMessageContent) msg.baseContentMsgModel;
            //已经有网络地址无需在上传
            if (!TextUtils.isEmpty(contentMsgModel.url)) {
                listener.onUploadResult(true, contentMsgModel);
            } else {
                if (!TextUtils.isEmpty(contentMsgModel.localPath)) {
                    WKUploader.getInstance().getUploadFileUrl(msg.channelID, msg.channelType, contentMsgModel.localPath, (url, filePath) -> {
                        if (!TextUtils.isEmpty(url)) {
                            WKUploader.getInstance().upload(url, contentMsgModel.localPath, msg.clientSeq, new WKUploader.IUploadBack() {
                                @Override
                                public void onSuccess(String url) {
                                    contentMsgModel.url = url;
                                    listener.onUploadResult(true, contentMsgModel);
                                }

                                @Override
                                public void onError() {
                                    listener.onUploadResult(false, contentMsgModel);
                                }
                            });
                        } else {
                            listener.onUploadResult(false, contentMsgModel);
                        }
                    });
                } else {
                    listener.onUploadResult(false, msg.baseContentMsgModel);
                }
            }

        } else if (msg.type == WKContentType.WK_VIDEO) {
            //视频
            WKVideoContent videoMsgModel = (WKVideoContent) msg.baseContentMsgModel;
            if (!TextUtils.isEmpty(videoMsgModel.cover) && !TextUtils.isEmpty(videoMsgModel.url)) {
                listener.onUploadResult(true, msg.baseContentMsgModel);
            } else {
                if (TextUtils.isEmpty(videoMsgModel.cover)) {
                    WKUploader.getInstance().getUploadFileUrl(msg.channelID, msg.channelType, videoMsgModel.coverLocalPath, (url, filePath) -> {
                        if (!TextUtils.isEmpty(url)) {
                            WKUploader.getInstance().upload(url, videoMsgModel.coverLocalPath, UUID.randomUUID().toString().replaceAll("-", ""),
                                    new WKUploader.IUploadBack() {
                                        @Override
                                        public void onSuccess(String url) {
                                            videoMsgModel.cover = url;
                                            WKUploader.getInstance().getUploadFileUrl(msg.channelID, msg.channelType, videoMsgModel.localPath, new WKUploader.IGetUploadFileUrl() {
                                                @Override
                                                public void onResult(String url, String fileUrl) {
                                                    WKUploader.getInstance().upload(url, videoMsgModel.localPath, msg.clientSeq, new WKUploader.IUploadBack() {
                                                        @Override
                                                        public void onSuccess(String url) {
                                                            videoMsgModel.url = url;
                                                            listener.onUploadResult(true, videoMsgModel);
                                                        }

                                                        @Override
                                                        public void onError() {
                                                            listener.onUploadResult(false, videoMsgModel);
                                                        }
                                                    });
                                                }
                                            });
                                        }

                                        @Override
                                        public void onError() {
                                            listener.onUploadResult(false, msg.baseContentMsgModel);
                                        }
                                    });
                        } else {
                            listener.onUploadResult(false, msg.baseContentMsgModel);
                        }
                    });
                }
            }
        }

    }
}
