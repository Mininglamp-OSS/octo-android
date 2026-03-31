package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.manager.ChannelManager;
import com.xinbida.wukongim.manager.ChannelMembersManager;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKCommonUtils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/**
 * 2019-11-09 14:33
 * 消息体 对应 #DBMsgColumns 中字段
 */
public class WKMsg implements Parcelable {

    //服务器消息ID(全局唯一，无序)
    public String messageID;
    //服务器消息序号(有序递增)
    public int messageSeq;
    //客户端序号
    public long clientSeq;
    //消息时间10位时间戳
    public long timestamp;
    public int expireTime;
    public long expireTimestamp;
    //消息来源发送者
    public String fromUID;
    //频道id
    public String channelID;
    //频道类型
    public byte channelType;
    //消息正文类型
    public int type;
    //消息内容Json
    public String content;
    //发送状态
    public int status;
    //语音是否已读
    public int voiceStatus;
    //是否被删除
    public int isDeleted;
    //创建时间
    public String createdAt;
    //修改时间
    public String updatedAt;
    //扩展字段
    public HashMap localExtraMap;
    //搜索关键字
    public String searchableWord;
    //自定义消息实体
    public WKMessageContent baseContentMsgModel;
    //消息来源频道
    private WKChannel from;
    //会话频道
    private WKChannel channelInfo;
    //消息频道成员
    private WKChannelMember memberOfFrom;
    //客户端消息ID
    public String clientMsgNO;
    //排序编号
    public long orderSeq;
    // 是否开启阅后即焚
    public int flame;
    // 阅后即焚秒数
    public int flameSecond;
    // 是否已查看 0.未查看 1.已查看 （这个字段跟已读的区别在于是真正的查看了消息内容，比如图片消息 已读是列表滑动到图片消息位置就算已读，viewed是表示点开图片才算已查看，语音消息类似）
    public int viewed;
    // 查看时间戳
    public long viewedAt;
    public String robotID;
    // 话题ID
    public String topicID;
    //消息设置
    public WKMsgSetting setting;
    // 消息头
    public WKMsgHeader header;
    // 服务器消息扩展
    public WKMsgExtra remoteExtra;
    //消息回应
    public List<WKMsgReaction> reactionList;

    public WKMsg() {
        super();
        this.timestamp = DateUtils.getInstance().getCurrentSeconds();
        this.createdAt = DateUtils.getInstance().time2DateStr(timestamp);
        this.updatedAt = DateUtils.getInstance().time2DateStr(timestamp);
        this.messageSeq = 0;
        this.expireTime = 0;
        this.expireTimestamp = 0;
        status = WKSendMsgResult.send_loading;
        clientMsgNO = WKIM.getInstance().getMsgManager().createClientMsgNO();
        setting=new WKMsgSetting();
        header = new WKMsgHeader();
        remoteExtra = new WKMsgExtra();
    }

    protected WKMsg(Parcel in) {
//        revoke = in.readInt();
        orderSeq = in.readLong();
        isDeleted = in.readInt();
        clientMsgNO = in.readString();
        messageID = in.readString();
        messageSeq = in.readInt();
        clientSeq = in.readLong();
        timestamp = in.readLong();
        fromUID = in.readString();
        channelID = in.readString();
        channelType = in.readByte();
        type = in.readInt();
        content = in.readString();
        status = in.readInt();
        voiceStatus = in.readInt();
        createdAt = in.readString();
        updatedAt = in.readString();
        searchableWord = in.readString();
        String localExtraStr = in.readString();
        localExtraMap = WKCommonUtils.str2HashMap(localExtraStr);
        baseContentMsgModel = in.readParcelable(WKMsg.class
                .getClassLoader());
        from = in.readParcelable(WKChannel.class.getClassLoader());
        memberOfFrom = in.readParcelable(WKChannelMember.class.getClassLoader());
        channelInfo = in.readParcelable(WKChannelMember.class.getClassLoader());
        setting = in.readParcelable(WKMsgSetting.class.getClassLoader());
        header = in.readParcelable(WKMsgHeader.class.getClassLoader());
        reactionList = in.createTypedArrayList(WKMsgReaction.CREATOR);

        flame = in.readInt();
        flameSecond = in.readInt();
        viewed = in.readInt();
        viewedAt = in.readLong();
        topicID = in.readString();
        expireTime = in.readInt();
        expireTimestamp = in.readLong();
        robotID = in.readString();
    }

    public static final Creator<WKMsg> CREATOR = new Creator<WKMsg>() {
        @Override
        public WKMsg createFromParcel(Parcel in) {
            return new WKMsg(in);
        }

        @Override
        public WKMsg[] newArray(int size) {
            return new WKMsg[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
//        dest.writeInt(revoke);
        dest.writeLong(orderSeq);
        dest.writeInt(isDeleted);
        dest.writeString(clientMsgNO);
        dest.writeString(messageID);
        dest.writeInt(messageSeq);
        dest.writeLong(clientSeq);
        dest.writeLong(timestamp);
        dest.writeString(fromUID);
        dest.writeString(channelID);
        dest.writeByte(channelType);
        dest.writeInt(type);
        dest.writeString(content);
        dest.writeInt(status);
        dest.writeInt(voiceStatus);
        dest.writeString(createdAt);
        dest.writeString(updatedAt);
        dest.writeString(searchableWord);
        dest.writeString(getLocalMapExtraString());
        dest.writeParcelable(baseContentMsgModel, flags);
        dest.writeParcelable(from, flags);
        dest.writeParcelable(memberOfFrom, flags);
        dest.writeParcelable(channelInfo, flags);
        dest.writeParcelable(setting, flags);
        dest.writeParcelable(header, flags);
        dest.writeTypedList(reactionList);
        dest.writeInt(flame);
        dest.writeInt(flameSecond);
        dest.writeInt(viewed);
        dest.writeLong(viewedAt);
        dest.writeString(topicID);
        dest.writeInt(expireTime);
        dest.writeLong(expireTimestamp);
        dest.writeString(robotID);
    }

    public String getLocalMapExtraString() {
        String extras = "";
        if (localExtraMap != null && !localExtraMap.isEmpty()) {
            JSONObject jsonObject = new JSONObject(localExtraMap);
            extras = jsonObject.toString();
        }
        return extras;
    }

    public WKChannel getChannelInfo() {
        if (channelInfo == null) {
            channelInfo = ChannelManager.getInstance().getChannel(channelID, channelType);
        }
        return channelInfo;
    }

    public void setChannelInfo(WKChannel channelInfo) {
        this.channelInfo = channelInfo;
    }

    public WKChannel getFrom() {
        if (from == null)
            from = ChannelManager.getInstance().getChannel(fromUID, WKChannelType.PERSONAL);
        return from;
    }

    public void setFrom(WKChannel channelInfo) {
        from = channelInfo;
    }

    public WKChannelMember getMemberOfFrom() {
        if (memberOfFrom == null)
            memberOfFrom = ChannelMembersManager.getInstance().getMember(channelID, channelType, fromUID);
        return memberOfFrom;
    }

    public void setMemberOfFrom(WKChannelMember memberOfFrom) {
        this.memberOfFrom = memberOfFrom;
    }
}
