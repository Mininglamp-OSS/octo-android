package com.xinbida.wukongim.message;

import com.xinbida.wukongim.utils.WKTypeUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WKWrite {
    private final ByteBuffer buffer;
    private final byte[] bytes;

    public WKWrite(int totalLength) {
        buffer = ByteBuffer.allocate(totalLength).order(
                ByteOrder.BIG_ENDIAN);
        bytes = new byte[totalLength];
    }

    public void writeByte(byte b) {
        buffer.put(b);
    }

    public void writeBytes(byte[] bytes) {
        buffer.put(bytes);
    }

    public void writeInt(int v) {
        buffer.putInt(v);
    }

    public void writeShort(int v) {
        buffer.putShort((short) v);
    }

    public void writeLong(long v) {
        buffer.putLong(v);
    }

    public void writeString(String v) throws UnsupportedEncodingException {
        buffer.putShort((short) v.length());
        buffer.put(WKTypeUtils.getInstance().stringToByte(v));
    }

    public void writePayload(String v) throws UnsupportedEncodingException {
        byte[] contentBytes = WKTypeUtils.getInstance().stringToByte(v);
        buffer.put(contentBytes);
    }

    public byte[] getWriteBytes() {
        buffer.position(0);
        buffer.get(bytes);
        return bytes;
    }
}
