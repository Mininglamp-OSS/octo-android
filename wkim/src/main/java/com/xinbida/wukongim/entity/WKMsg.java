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

    /**
     * 返回已缓存的发送者成员信息，不触发数据库查询。
     * 用于主线程批量刷新场景（onRefreshChannel / onRefreshChannelMember），
     * 避免对未加载的消息发起同步 DB 查询导致 ANR。
     */
    public WKChannelMember getMemberOfFromIfCached() {
        return memberOfFrom;
    }

    public void setMemberOfFrom(WKChannelMember memberOfFrom) {
        this.memberOfFrom = memberOfFrom;
    }

    // ---------------------------------------------------------------
    // 外部群 Phase 1 — msg-level 便捷 getter（ EP1）
    //
    // 对齐 web PR #982 (from_is_external / from_source_space_name) 与
    // PR #997 (from_home_space_id / from_home_space_name) 的 MessageWrap
    // getter 语义。数据来源是当前消息发送者对应的 WKChannelMember.extraMap，
    // 在 GroupModel.serialize 阶段由 /group/members 响应写入。
    //
    // UI 层直接调这些 getter，避免在消息气泡里散落 extraMap 取值逻辑。
    // web  的静默失败教训：model 层未透传字段会导致 UI 节点数归零，
    // 所以这里只返回原始字段，不做 viewer-relative 业务判定；viewer 比较
    // 逻辑（判断当前 viewer 的 Space 与 from_home_space_id 是否一致）
    // 由后续 EP 在 UI/ViewModel 层完成。
    // ---------------------------------------------------------------

    /**
     * 消息发送者是否为外部成员（来自其他 Space）。
     *
     * @return 1 表示外部；0 表示同 Space 或信息缺失。
     */
    public int getFromIsExternal() {
        WKChannelMember m = getMemberOfFrom();
        if (m == null || m.extraMap == null) return 0;
        Object v = m.extraMap.get(WKChannelMemberExtras.isExternal);
        if (v == null) return 0;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 发送者通过哪个 Space 加入当前群的 Space 名（可能为空）。 */
    public String getFromSourceSpaceName() {
        WKChannelMember m = getMemberOfFrom();
        if (m == null || m.extraMap == null) return null;
        Object v = m.extraMap.get(WKChannelMemberExtras.sourceSpaceName);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 发送者通过哪个 Space 加入当前群的 Space ID（可能为空）。
     *
     * 说明：issue 原文 MessageWrap getter 清单是 4 个（不含 source_space_id），但
     * GroupModel.serialize 与 WKMultiForwardContent.decodeMsg 都把 source_space_id
     * 写进了 extraMap。为避免 UI 层绕过 getter 直接访问裸 key（ 静默失败
     * 模式），这里补上对称的 getter。参见  EP1 claude review round-2 P2。
     */
    public String getFromSourceSpaceID() {
        WKChannelMember m = getMemberOfFrom();
        if (m == null || m.extraMap == null) return null;
        Object v = m.extraMap.get(WKChannelMemberExtras.sourceSpaceID);
        return v == null ? null : String.valueOf(v);
    }

    /** 发送者的 Home Space ID — viewer-relative 外部判定用（ / web #997）。 */
    public String getFromHomeSpaceID() {
        WKChannelMember m = getMemberOfFrom();
        if (m == null || m.extraMap == null) return null;
        Object v = m.extraMap.get(WKChannelMemberExtras.homeSpaceID);
        return v == null ? null : String.valueOf(v);
    }

    /** 发送者的 Home Space 显示名 — 用于 @SpaceName 后缀展示（ / web #997）。 */
    public String getFromHomeSpaceName() {
        WKChannelMember m = getMemberOfFrom();
        if (m == null || m.extraMap == null) return null;
        Object v = m.extraMap.get(WKChannelMemberExtras.homeSpaceName);
        return v == null ? null : String.valueOf(v);
    }
}
