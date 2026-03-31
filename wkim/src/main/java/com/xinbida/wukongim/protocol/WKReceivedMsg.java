package com.xinbida.wukongim.protocol;

import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.message.type.WKMsgType;

/**
 * 2019-11-11 10:37
 * 接受消息
 */
public class WKReceivedMsg extends WKBaseMsg {
    //服务端的消息ID(全局唯一)
    public String messageID;
    //服务端的消息序列号(有序递增，用户唯一)
    public int messageSeq;
    //服务器消息时间戳(10位，到秒)
    public long messageTimestamp;
    //客户端ID
    public String clientMsgNo;
    //频道ID
    public String channelID;
    //频道类型
    public byte channelType;
    //发送者ID
    public String fromUID;
    // 话题ID
    public String topicID;
    //消息内容
    public String payload;
    //消息key
    public String msgKey;
    // 消息设置
    public WKMsgSetting setting;
    public String streamNO;
    public int streamSeq;
    public int streamFlag;
    public int expire;
    private final int settingLength = 1;
    private final int msgKeyLength = 2;
    public int msgKeyContentLength = 0;
    private final int fromUIDLength = 2;
    public int fromUIDContentLength = 0;
    private final int channelTDLength = 2;
    public int channelTDContentLength = 0;
    private final int channelTypeLength = 1;
    private final int clientMsgNoLength = 2;
    public int clientMsgNoContentLength = 0;
    private final int streamNOLength = 2;
    public int streamNOContentLength = 0;
    public int streamSeqLength = 4;
    public int streamFlagLength = 1;
    private final int messageIDLength = 8;
    private final int messageSeqLength = 4;
    private final int messageTimeLength = 4;
    private final int topicIDLength = 2;
    public int topicIDContentLength = 0;

    public WKReceivedMsg() {
        packetType = WKMsgType.RECEIVED;
    }

    public int getPayloadLength(int remainingLength) {
        int length = 0;
        length += settingLength;
        length += (msgKeyLength + msgKeyContentLength);
        length += (fromUIDLength + fromUIDContentLength);
        length += (channelTDLength + channelTDContentLength);
        length += channelTypeLength;
        length += (clientMsgNoLength + clientMsgNoContentLength);
        if (setting.stream == 1) {
            length += (streamNOLength + streamNOContentLength);
            length += streamSeqLength;
            length += streamFlagLength;
        }
        length += messageIDLength;
        length += messageSeqLength;
        length += messageTimeLength;
        if (setting.topic == 1) {
            length += (topicIDLength + topicIDContentLength);
        }
        return remainingLength - length;
    }

    @Override
    public String toString() {
        return "WKReceivedMsg{" +
                "messageID='" + messageID + '\'' +
                ", messageSeq=" + messageSeq +
                ", messageTimestamp=" + messageTimestamp +
                ", clientMsgNo='" + clientMsgNo + '\'' +
                ", channelID='" + channelID + '\'' +
                ", channelType=" + channelType +
                ", fromUID='" + fromUID + '\'' +
                ", topicID='" + topicID + '\'' +
                ", payload='" + payload + '\'' +
                ", msgKey='" + msgKey + '\'' +
                ", setting=" + setting +
                ", streamNO='" + streamNO + '\'' +
                ", streamSeq=" + streamSeq +
                ", streamFlag=" + streamFlag +
                ", expire=" + expire +
                ", settingLength=" + settingLength +
                '}';
    }
}
