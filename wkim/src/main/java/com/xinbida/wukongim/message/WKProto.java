package com.xinbida.wukongim.message;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKConnectMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPingMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKReceivedAckMsg;
import com.xinbida.wukongim.protocol.WKReceivedMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.CryptoUtils;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * 5/21/21 11:28 AM
 * 收发消息转换
 */
class WKProto {
    private final String TAG = "WKProto";

    private WKProto() {
    }

    private static class MessageConvertHandlerBinder {
        static final WKProto msgConvert = new WKProto();
    }

    public static WKProto getInstance() {
        return MessageConvertHandlerBinder.msgConvert;
    }

    byte[] encodeMsg(WKBaseMsg msg) {
        byte[] bytes = null;
        if (msg.packetType == WKMsgType.CONNECT) {
            // 连接
            bytes = WKProto.getInstance().enConnectMsg((WKConnectMsg) msg);
//            String str = Arrays.toString(bytes);
//            WKLoggerUtils.getInstance().e(str);
        } else if (msg.packetType == WKMsgType.REVACK) {
            // 收到消息回执
            bytes = WKProto.getInstance().enReceivedAckMsg((WKReceivedAckMsg) msg);
        } else if (msg.packetType == WKMsgType.SEND) {
            // 发送聊天消息
            bytes = WKProto.getInstance().enSendMsg((WKSendMsg) msg);
        } else if (msg.packetType == WKMsgType.PING) {
            // 发送心跳
            bytes = WKProto.getInstance().enPingMsg((WKPingMsg) msg);
            WKLoggerUtils.getInstance().e("ping...");
        }
        return bytes;
    }

    byte[] enConnectMsg(WKConnectMsg connectMsg) {
        CryptoUtils.getInstance().initKey();
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(connectMsg.getRemainingLength());
        int totalLen = connectMsg.getTotalLen();
        WKWrite wkWrite = new WKWrite(totalLen);
        try {
            wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(connectMsg.packetType, connectMsg.flag, 0, 0));
            wkWrite.writeBytes(remainingBytes);
            wkWrite.writeByte(WKIMApplication.getInstance().protocolVersion);
            wkWrite.writeByte(connectMsg.deviceFlag);
            wkWrite.writeString(connectMsg.deviceID);
            wkWrite.writeString(WKIMApplication.getInstance().getUid());
            wkWrite.writeString(WKIMApplication.getInstance().getToken());
            wkWrite.writeLong(connectMsg.clientTimestamp);
            wkWrite.writeString(CryptoUtils.getInstance().getPublicKey());
        } catch (UnsupportedEncodingException e) {
            WKLoggerUtils.getInstance().e(TAG, "编码连接包错误");
        }
        return wkWrite.getWriteBytes();
    }

    synchronized byte[] enReceivedAckMsg(WKReceivedAckMsg receivedAckMsg) {
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(8 + 4);

        int totalLen = 1 + remainingBytes.length + 8 + 4;
        WKWrite wkWrite = new WKWrite(totalLen);
        wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(receivedAckMsg.packetType, receivedAckMsg.no_persist ? 1 : 0, receivedAckMsg.red_dot ? 1 : 0, receivedAckMsg.sync_once ? 1 : 0));
        wkWrite.writeBytes(remainingBytes);
        BigInteger bigInteger = new BigInteger(receivedAckMsg.messageID);
        wkWrite.writeLong(bigInteger.longValue());
        wkWrite.writeInt(receivedAckMsg.messageSeq);
        return wkWrite.getWriteBytes();
    }

    byte[] enPingMsg(WKPingMsg pingMsg) {
        WKWrite wkWrite = new WKWrite(1);
        wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(pingMsg.packetType, pingMsg.flag, 0, 0));
        return wkWrite.getWriteBytes();
    }

    byte[] enSendMsg(WKSendMsg sendMsg) {
        // 先加密内容
        String sendContent = sendMsg.getSendContent();
        String msgKeyContent = sendMsg.getMsgKey();
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(sendMsg.getRemainingLength());
        int totalLen = sendMsg.getTotalLength();
        WKWrite wkWrite = new WKWrite(totalLen);
        try {
            wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(sendMsg.packetType, sendMsg.no_persist ? 1 : 0, sendMsg.red_dot ? 1 : 0, sendMsg.sync_once ? 1 : 0));
            wkWrite.writeBytes(remainingBytes);
            wkWrite.writeByte(WKTypeUtils.getInstance().getMsgSetting(sendMsg.setting));
            wkWrite.writeInt(sendMsg.clientSeq);
            wkWrite.writeString(sendMsg.clientMsgNo);
            wkWrite.writeString(sendMsg.channelId);
            wkWrite.writeByte(sendMsg.channelType);
            if (WKIMApplication.getInstance().protocolVersion >= 3) {
                wkWrite.writeInt(sendMsg.expire);
            }
            wkWrite.writeString(msgKeyContent);
            if (sendMsg.setting.topic == 1) {
                wkWrite.writeString(sendMsg.topicID);
            }
            wkWrite.writePayload(sendContent);

        } catch (UnsupportedEncodingException e) {
            WKLoggerUtils.getInstance().e(TAG, "编码发送包错误");
        }
        return wkWrite.getWriteBytes();
    }

    private WKConnectAckMsg deConnectAckMsg(WKRead wkRead, int hasServerVersion) {
        WKConnectAckMsg connectAckMsg = new WKConnectAckMsg();
        try {
            if (hasServerVersion == 1) {
                connectAckMsg.serviceProtoVersion = wkRead.readByte();
               // byte serverVersion = wkRead.readByte();
                if (connectAckMsg.serviceProtoVersion != 0) {
                    WKIMApplication.getInstance().protocolVersion = (byte) Math.min(connectAckMsg.serviceProtoVersion, WKIMApplication.getInstance().protocolVersion);
                }
            }
            long time = wkRead.readLong();
            short reasonCode = wkRead.readByte();
            String serverKey = wkRead.readString();
            String salt = wkRead.readString();
            if (connectAckMsg.serviceProtoVersion >= 4){
                connectAckMsg.nodeId = (int) wkRead.readLong();
            }
            connectAckMsg.serverKey = serverKey;
            connectAckMsg.salt = salt;
            //保存公钥和安全码
            CryptoUtils.getInstance().setServerKeyAndSalt(connectAckMsg.serverKey, connectAckMsg.salt);
            connectAckMsg.timeDiff = time;
            connectAckMsg.reasonCode = reasonCode;
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "解码连接ack包错误");
        }

        return connectAckMsg;
    }

    private WKSendAckMsg deSendAckMsg(WKRead wkRead) {
        WKSendAckMsg sendAckMsg = new WKSendAckMsg();
        try {
            sendAckMsg.messageID = wkRead.readMsgID();
            sendAckMsg.clientSeq = wkRead.readInt();
            sendAckMsg.messageSeq = wkRead.readInt();
            sendAckMsg.reasonCode = wkRead.readByte();
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "解码发送ack错误");
        }
        return sendAckMsg;
    }

    private WKDisconnectMsg deDisconnectMsg(WKRead wkRead) {
        WKDisconnectMsg disconnectMsg = new WKDisconnectMsg();
        try {
            disconnectMsg.reasonCode = wkRead.readByte();
            disconnectMsg.reason = wkRead.readString();
            WKLoggerUtils.getInstance().e(TAG, "断开消息code:" + disconnectMsg.reasonCode + ",reason:" + disconnectMsg.reason);
            return disconnectMsg;
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "解码断开包错误");
        }
        return disconnectMsg;
    }

    private WKReceivedMsg deReceivedMsg(WKRead wkRead) {
        WKReceivedMsg receivedMsg = new WKReceivedMsg();
        try {
            byte settingByte = wkRead.readByte();
            receivedMsg.setting = WKTypeUtils.getInstance().getMsgSetting(settingByte);
            receivedMsg.msgKey = wkRead.readString();
            receivedMsg.fromUID = wkRead.readString();
            receivedMsg.channelID = wkRead.readString();
            receivedMsg.channelType = wkRead.readByte();
            if (WKIMApplication.getInstance().protocolVersion >= 3) {
                receivedMsg.expire = wkRead.readInt();
            }
            receivedMsg.clientMsgNo = wkRead.readString();
            if (receivedMsg.setting.stream == 1) {
                receivedMsg.streamNO = wkRead.readString();
                receivedMsg.streamSeq = wkRead.readInt();
                receivedMsg.streamFlag = wkRead.readByte();
            }
            receivedMsg.messageID = wkRead.readMsgID();
            receivedMsg.messageSeq = wkRead.readInt();
            receivedMsg.messageTimestamp = wkRead.readInt();
            if (receivedMsg.setting.topic == 1) {
                receivedMsg.topicID = wkRead.readString();
            }
            String content = wkRead.readPayload();
            String msgKey = receivedMsg.messageID
                    + receivedMsg.messageSeq
                    + receivedMsg.clientMsgNo
                    + receivedMsg.messageTimestamp
                    + receivedMsg.fromUID
                    + receivedMsg.channelID
                    + receivedMsg.channelType
                    + content;
            byte[] result = CryptoUtils.getInstance().aesEncrypt(msgKey);
            if (result == null) {
                return null;
            }
            String base64Result = CryptoUtils.getInstance().base64Encode(result);
            String localMsgKey = CryptoUtils.getInstance().digestMD5(base64Result);
            if (!localMsgKey.equals(receivedMsg.msgKey)) {
                WKLoggerUtils.getInstance().e("非法消息,本地消息key:" + localMsgKey + ",期望key:" + msgKey);
                return null;
            }
            receivedMsg.payload = CryptoUtils.getInstance().aesDecrypt(CryptoUtils.getInstance().base64Decode(content));
            WKLoggerUtils.getInstance().e(receivedMsg.toString());
            return receivedMsg;
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "解码收到的消息错误");
            return null;
        }
    }

    WKBaseMsg decodeMessage(byte[] bytes) {
        try {
            WKRead wkRead = new WKRead(bytes);
            int packetType = wkRead.readPacketType();
            wkRead.readRemainingLength();
            if (packetType == WKMsgType.CONNACK) {
                int hasServerVersion = WKTypeUtils.getInstance().getBit(bytes[0], 0);
                return deConnectAckMsg(wkRead, hasServerVersion);
            } else if (packetType == WKMsgType.SENDACK) {
                return deSendAckMsg(wkRead);
            } else if (packetType == WKMsgType.DISCONNECT) {
                return deDisconnectMsg(wkRead);
            } else if (packetType == WKMsgType.RECEIVED) {
                return deReceivedMsg(wkRead);
            } else if (packetType == WKMsgType.PONG) {
                return new WKPongMsg();
            } else {
                WKLoggerUtils.getInstance().e("解码未知消息包类型：" + packetType);
                return null;
            }
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e("解码消息错误：" + e.getMessage());
            return null;
        }
    }

    /**
     * 三态 mention：把 mention.humans=1 / mention.ais=1 写入 mention JSON。
     * 标志位单一来源：WKMessageContent.mentionHumans / mentionAis (int)
     * 兜底：WKMentionInfo.humans / ais (bool) —— 对齐 iOS PR#128 round 3 教训：
     * 上层只设 enum/info BOOL 而忘了同步 int 字段时也能正确编码。
     */
    private static void writeThreeStateMentionFlags(JSONObject mentionJson,
                                                    com.xinbida.wukongim.msgmodel.WKMessageContent content)
            throws JSONException {
        boolean humans = content.mentionHumans == 1
                || (content.mentionInfo != null && content.mentionInfo.humans);
        boolean ais = content.mentionAis == 1
                || (content.mentionInfo != null && content.mentionInfo.ais);
        if (humans) {
            mentionJson.put("humans", 1);
        }
        if (ais) {
            mentionJson.put("ais", 1);
        }
    }

    JSONObject getSendPayload(WKMsg msg) {
        JSONObject jsonObject = null;
        if (msg.baseContentMsgModel != null) {
            jsonObject = msg.baseContentMsgModel.encodeMsg();
        } else {
            msg.baseContentMsgModel = new WKMessageContent();
        }
        try {
            if (jsonObject == null) jsonObject = new JSONObject();
            jsonObject.put(WKDBColumns.WKMessageColumns.type, msg.type);
            //判断@情况
            if (msg.baseContentMsgModel.mentionInfo != null
                    && msg.baseContentMsgModel.mentionInfo.uids != null
                    && !msg.baseContentMsgModel.mentionInfo.uids.isEmpty()) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0, size = msg.baseContentMsgModel.mentionInfo.uids.size(); i < size; i++) {
                    jsonArray.put(msg.baseContentMsgModel.mentionInfo.uids.get(i));
                }
                if (!jsonObject.has("mention")) {
                    JSONObject mentionJson = new JSONObject();
                    // 三态 mention：仅当 legacy mentionAll==1 时才写 all=1；
                    // @所有人 走新协议 mention.humans=1，避免旧 adapter bot 因 all=1 被误唤醒。
                    if (msg.baseContentMsgModel.mentionAll == 1) {
                        mentionJson.put("all", 1);
                    }
                    // 三态 mention：humans=1 / ais=1 与 legacy all 字段并存
                    writeThreeStateMentionFlags(mentionJson, msg.baseContentMsgModel);
                    mentionJson.put("uids", jsonArray);
                    jsonObject.put("mention", mentionJson);
                }

            } else {
                if (msg.baseContentMsgModel.mentionAll == 1
                        || msg.baseContentMsgModel.mentionHumans == 1
                        || msg.baseContentMsgModel.mentionAis == 1
                        || (msg.baseContentMsgModel.mentionInfo != null
                        && (msg.baseContentMsgModel.mentionInfo.humans
                        || msg.baseContentMsgModel.mentionInfo.ais))) {
                    if (!jsonObject.has("mention")) {
                        JSONObject mentionJson = new JSONObject();
                        // 仅当 mentionAll==1 时写 all=1（避免误唤醒旧 adapter bot）
                        if (msg.baseContentMsgModel.mentionAll == 1) {
                            mentionJson.put("all", 1);
                        }
                        writeThreeStateMentionFlags(mentionJson, msg.baseContentMsgModel);
                        jsonObject.put("mention", mentionJson);
                    }
                }
            }
            // 被回复消息
            if (msg.baseContentMsgModel.reply != null) {
                jsonObject.put("reply", msg.baseContentMsgModel.reply.encodeMsg());
            }
            // 机器人ID
            if (!TextUtils.isEmpty(msg.baseContentMsgModel.robotID)) {
                jsonObject.put("robot_id", msg.baseContentMsgModel.robotID);
            }
            if (!TextUtils.isEmpty(msg.robotID)) {
                jsonObject.put("robot_id", msg.robotID);
            }
            if (WKCommonUtils.isNotEmpty(msg.baseContentMsgModel.entities)) {
                JSONArray jsonArray = new JSONArray();
                for (WKMsgEntity entity : msg.baseContentMsgModel.entities) {
                    JSONObject jo = new JSONObject();
                    jo.put("offset", entity.offset);
                    jo.put("length", entity.length);
                    jo.put("type", entity.type);
                    jo.put("value", entity.value);
                    jsonArray.put(jo);
                }
                jsonObject.put("entities", jsonArray);
            }
            if (msg.flame != 0) {
                jsonObject.put("flame_second", msg.flameSecond);
                jsonObject.put("flame", msg.flame);
            }
            // Space隔离标识
            if (!TextUtils.isEmpty(msg.baseContentMsgModel.spaceId)) {
                jsonObject.put("space_id", msg.baseContentMsgModel.spaceId);
            }
//            if (msg.baseContentMsgModel.flame != 0) {
//                jsonObject.put("flame_second", msg.baseContentMsgModel.flameSecond);
//                jsonObject.put("flame", msg.baseContentMsgModel.flame);
//            }
        } catch (JSONException e) {
            WKLoggerUtils.getInstance().e(TAG, "获取消息体错误");
        }
        return jsonObject;
    }

    /**
     * 获取发送的消息
     *
     * @param msg 本地消息
     * @return 网络消息
     */
    WKSendMsg getSendBaseMsg(WKMsg msg) {
        //发送消息
        JSONObject jsonObject = getSendPayload(msg);
        WKSendMsg sendMsg = new WKSendMsg();
        // 默认先设置clientSeq，因为有可能本条消息并不需要入库，UI上自己设置了clientSeq
        sendMsg.clientSeq = (int) msg.clientSeq;
        sendMsg.sync_once = msg.header.syncOnce;
        sendMsg.no_persist = msg.header.noPersist;
        sendMsg.red_dot = msg.header.redDot;
        sendMsg.clientMsgNo = msg.clientMsgNO;
        sendMsg.channelId = msg.channelID;
        sendMsg.channelType = msg.channelType;
        sendMsg.topicID = msg.topicID;
        sendMsg.setting = msg.setting;
        sendMsg.expire = msg.expireTime;
        if (WKMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //多媒体数据
            if (jsonObject.has("localPath")) {
                jsonObject.remove("localPath");
            }
            //视频地址
            if (jsonObject.has("videoLocalPath")) {
                jsonObject.remove("videoLocalPath");
            }
        }
        sendMsg.payload = jsonObject.toString();
        return sendMsg;
    }

    WKMsg baseMsg2WKMsg(WKBaseMsg baseMsg) {
        WKReceivedMsg receivedMsg = (WKReceivedMsg) baseMsg;
        WKMsg msg = new WKMsg();
        msg.channelType = receivedMsg.channelType;
        msg.channelID = receivedMsg.channelID;
        msg.content = receivedMsg.payload;
        msg.messageID = receivedMsg.messageID;
        msg.messageSeq = receivedMsg.messageSeq;
        msg.timestamp = receivedMsg.messageTimestamp;
        msg.fromUID = receivedMsg.fromUID;
        msg.setting = receivedMsg.setting;
        msg.clientMsgNO = receivedMsg.clientMsgNo;
        msg.status = WKSendMsgResult.send_success;
        msg.topicID = receivedMsg.topicID;
        msg.expireTime = receivedMsg.expire;
        if (msg.expireTime > 0) {
            msg.expireTimestamp = msg.expireTime + msg.timestamp;
        }
        msg.orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(msg.messageSeq, msg.channelID, msg.channelType);
        msg.isDeleted = isDelete(msg.content);
        return msg;
    }

    private int isDelete(String contentJson) {
        int isDelete = 0;
        if (!TextUtils.isEmpty(contentJson)) {
            try {
                JSONObject jsonObject = new JSONObject(contentJson);
                isDelete = WKIM.getInstance().getMsgManager().isDeletedMsg(jsonObject);
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "获取消息是否删除时发现消息体非json");
            }
        }
        return isDelete;
    }
}
