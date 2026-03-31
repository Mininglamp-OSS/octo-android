package com.xinbida.wukongim.message;

import com.xinbida.wukongim.utils.BigTypeUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

public class WKRead {
    InputStream inputStream;

    WKRead(byte[] bytes) {
        this.inputStream = new ByteArrayInputStream(bytes);
    }

    public int readPacketType() throws IOException {
        byte[] header = new byte[1];
        int headerRead = inputStream.read(header);
        if (headerRead == -1) return 0;
        return WKTypeUtils.getInstance().getHeight4(header[0]);
    }

    public int readRemainingLength() {
        return WKTypeUtils.getInstance().bytes2Length(inputStream);
    }

    public long readLong() throws IOException {
        byte[] longByte = new byte[8];
        int read = inputStream.read(longByte);
        if (read == -1) return 0;
        return BigTypeUtils.getInstance().bytesToLong(longByte);
    }

    public int readInt() throws IOException {
        byte[] intByte = new byte[4];
        int read = inputStream.read(intByte);
        if (read == -1) return 0;
        return BigTypeUtils.getInstance().bytesToInt(intByte);
    }

    public byte readByte() throws IOException {
        byte[] b = new byte[1];
        int read = inputStream.read(b);
        if (read == -1) return 0;
        return b[0];
    }

    public String readString() throws IOException {
        byte[] strLengthByte = new byte[2];
        int read = inputStream.read(strLengthByte);
        if (read == -1) return "";
        int strLength = BigTypeUtils.getInstance().byteToShort(strLengthByte);
        byte[] strByte = new byte[strLength];
        read = inputStream.read(strByte);
        if (read == -1) return "";
        return WKTypeUtils.getInstance().bytesToString(strByte);
    }

    public String readMsgID() throws IOException {
        String messageID;
        byte[] messageIdByte = new byte[8];
        int read = inputStream.read(messageIdByte);
        if (read == -1) return "";
        BigInteger bigInteger = new BigInteger(messageIdByte);
        if (bigInteger.toString().startsWith("-")) {
            BigInteger temp = new BigInteger("18446744073709551616");
            messageID = temp.add(bigInteger).toString();
        } else
            messageID = bigInteger.toString();
        return messageID;
    }

    public String readPayload() throws IOException {
        int payloadLength = inputStream.available();
        byte[] payload = new byte[payloadLength];
        int read = inputStream.read(payload);
        if (read == -1) return "";
        return WKTypeUtils.getInstance().bytesToString(payload);
    }
}
