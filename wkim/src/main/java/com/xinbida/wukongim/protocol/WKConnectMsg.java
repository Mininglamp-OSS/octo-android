package com.xinbida.wukongim.protocol;


import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.utils.CryptoUtils;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

/**
 * 2019-11-11 10:22
 * 连接talk service消息
 */
public class WKConnectMsg extends WKBaseMsg {
    //设备标示(同标示同账号互踢)
    public byte deviceFlag;
    //设备唯一ID
    public String deviceID;
    //客户端当前时间戳(13位时间戳,到毫秒)
    public long clientTimestamp;
    //用户的token
    public String token;

    //协议版本号长度
    public char protocolVersionLength = 1;
    //设备标示长度
    public char deviceFlagLength = 1;
    //设备id长度
    public char deviceIDLength = 2;
    //token长度所占字节长度
    public char tokenLength = 2;
    //uid长度所占字节长度
    public char uidLength = 2;
    //ClientKey长度所占字节长度
    public char clientKeyLength = 2;
    //时间戳长度
    public char clientTimeStampLength = 8;

    public WKConnectMsg() {
        token = WKIMApplication.getInstance().getToken();
        clientTimestamp = DateUtils.getInstance().getCurrentMills();
        packetType = WKMsgType.CONNECT;
        deviceFlag = 0;
        deviceID = WKIMApplication.getInstance().getDeviceId();
        remainingLength = 1 + 1 + 8;//(协议版本号+设备标示(同标示同账号互踢)+客户端当前时间戳(13位时间戳,到毫秒))
    }

    public int getRemainingLength() {
        remainingLength = getFixedHeaderLength()
                + deviceIDLength
                + deviceID.length()
                + uidLength
                + WKIMApplication.getInstance().getUid().length()
                + tokenLength
                + WKIMApplication.getInstance().getToken().length()
                + clientTimeStampLength
                + clientKeyLength
                + CryptoUtils.getInstance().getPublicKey().length();
        return remainingLength;
    }

    public int getTotalLen() {
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(getRemainingLength());
        return 1 + remainingBytes.length
                + protocolVersionLength
                + deviceFlagLength
                + deviceIDLength
                + deviceID.length()
                + uidLength
                + WKIMApplication.getInstance().getUid().length()
                + tokenLength
                + WKIMApplication.getInstance().getToken().length()
                + clientTimeStampLength
                + clientKeyLength
                + CryptoUtils.getInstance().getPublicKey().length();
    }
}
