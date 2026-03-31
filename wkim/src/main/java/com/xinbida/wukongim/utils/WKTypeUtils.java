package com.xinbida.wukongim.utils;

import com.xinbida.wukongim.entity.WKMsgSetting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 2019-11-10 17:17
 * 数据类型转换管理
 */
public class WKTypeUtils {

    private WKTypeUtils() {
    }

    private static class TypeUtilsBinder {
        private static final WKTypeUtils typeUtils = new WKTypeUtils();
    }

    public static WKTypeUtils getInstance() {
        return TypeUtilsBinder.typeUtils;
    }

    /**
     * 获取协议的固定头
     *
     * @param packetType
     * @param no_persist
     * @param red_dot
     * @param sync_once
     * @return
     */
    public byte getHeader(short packetType, int no_persist, int red_dot, int sync_once) {
        byte s = (byte) (packetType << 4 | 0 | sync_once << 2 | red_dot << 1 | no_persist);
        return s;
    }

    //获取消息设置
    public byte getMsgSetting(WKMsgSetting setting) {
        return (byte) (setting.receipt << 7  | setting.topic << 3);
    }

    public int getHeight4(byte data) {//获取高四位
        int height;
        height = ((data & 0xf0) >> 4);
        return height;
    }

    public int getLow4(byte data) {//获取低四位
        int low;
        low = (data & 0x0f);
        return low;
    }

    /**
     * @param b b为传入的字节，
     * @param i i为第几位（范围0-7） 如要获取bit0，则i=0
     * @return
     */
    public int getBit(byte b, int i) {

        return ((b >> i) & 0x1);
    }

    /**
     * 获取剩余长度byte[]
     *
     * @param length
     * @return
     */
    public byte[] getRemainingLengthByte(int length) {
        List<Byte> list = new ArrayList<>();
        int val = length;
        do {
            byte digit = (byte) (val % 128);
            val = val / 128;
            if (val > 0)
                digit = (byte) (digit | 0x80);

            list.add(digit);
        } while (val > 0);
        byte[] bytes = new byte[list.size()];
        for (int i = 0, size = list.size(); i < size; i++) {
            bytes[i] = list.get(i);
        }
        return bytes;
    }

    public int getRemindLength(byte[] bytes) {
        int count = 0;
        int bit = -1;
        while (count < 4 && bytes.length > 0) {
            if (bytes.length - 1 < count) break;
            bit = getBit(bytes[count], 0);
            if (bit == 0) {
                break;
            }
            count++;
        }
        if (bit == 0) {
            return getRemainingLength(Arrays.copyOfRange(bytes, count, bytes.length));
        } else return -1;

    }

    public int getRemainingLength(byte[] bytes) {
        int multiplier = 1;
        int length = 0;
        int digit = 0;
        int index = 0;
        do {
            digit = bytes[index]; //一个字节的有符号或者无符号，转换转换为四个字节有符号 int类型
            length += (digit & 0x7f) * multiplier;
            multiplier *= 128;
            index++;
        } while ((digit & 0x80) != 0);

        return length;
    }

    public int bytes2Length(InputStream in) {
        int multiplier = 1;
        int length = 0;
        int digit = 0;
        do {
            try {
                digit = in.read(); //一个字节的有符号或者无符号，转换转换为四个字节有符号 int类型
            } catch (IOException e) {
                e.printStackTrace();
            }
            length += (digit & 0x7f) * multiplier;
            multiplier *= 128;
        } while ((digit & 0x80) != 0);

        return length;
    }

    /**
     * short到字节数组的转换.
     */
    public byte[] shortToByte(short number) {
        int temp = number;
        byte[] b = new byte[2];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Integer(temp & 0xff).byteValue();// 将最低位保存在最低位
            temp = temp >> 8;// 向右移8位
        }
        return b;
    }

    public WKMsgSetting getMsgSetting(byte setting) {
        WKMsgSetting msgSetting = new WKMsgSetting();
        msgSetting.receipt = getBit(setting, 7);
        msgSetting.topic = getBit(setting, 3);
        msgSetting.stream = getBit(setting, 2);
        return msgSetting;
    }

    public String bytes2HexString(byte[] b) {
        String ret = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            ret += hex.toUpperCase();
        }
        return ret;
    }

    /**
     * 字节数组到short的转换.
     */
    public short byteToShort(byte[] b) {
        short s = 0;
        short s0 = (short) (b[0] & 0xff);// 最低位
        short s1 = (short) (b[1] & 0xff);
        s1 <<= 8;
        s = (short) (s0 | s1);
        return s;
    }

    /**
     * int到字节数组的转换.
     */
    public byte[] intToByte(int number) {
        int temp = number;
        byte[] b = new byte[4];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Integer(temp & 0xff).byteValue();// 将最低位保存在最低位
            temp = temp >> 8;// 向右移8位
        }
        return b;
    }

    /**
     * 字节数组到int的转换.
     */
    public int byteToInt(byte[] b) {
        int s = 0;
        int s0 = b[0] & 0xff;// 最低位
        int s1 = b[1] & 0xff;
        int s2 = b[2] & 0xff;
        int s3 = b[3] & 0xff;
        s3 <<= 24;
        s2 <<= 16;
        s1 <<= 8;
        s = s0 | s1 | s2 | s3;
        return s;
    }

    /**
     * long类型转成byte数组
     */
    public byte[] longToByte(long number) {
        long temp = number;
        byte[] b = new byte[8];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Long(temp & 0xff).byteValue();// 将最低位保存在最低位 temp = temp
            // >> 8;// 向右移8位
        }
        return b;
    }

    /**
     * 字节数组到long的转换.
     */
    public long byteToLong(byte[] b) {
        long s = 0;
        long s0 = b[0] & 0xff;// 最低位
        long s1 = b[1] & 0xff;
        long s2 = b[2] & 0xff;
        long s3 = b[3] & 0xff;
        long s4 = b[4] & 0xff;// 最低位
        long s5 = b[5] & 0xff;
        long s6 = b[6] & 0xff;
        long s7 = b[7] & 0xff;

        // s0不变
        s1 <<= 8;
        s2 <<= 16;
        s3 <<= 24;
        s4 <<= 8 * 4;
        s5 <<= 8 * 5;
        s6 <<= 8 * 6;
        s7 <<= 8 * 7;
        s = s0 | s1 | s2 | s3 | s4 | s5 | s6 | s7;
        return s;
    }

    /**
     * double到字节数组的转换.
     */
    public byte[] doubleToByte(double num) {
        byte[] b = new byte[8];
        long l = Double.doubleToLongBits(num);
        for (int i = 0; i < 8; i++) {
            b[i] = new Long(l).byteValue();
            l = l >> 8;
        }
        return b;
    }

    /**
     * 字节数组到double的转换.
     */
    public double getDouble(byte[] b) {
        long m;
        m = b[0];
        m &= 0xff;
        m |= ((long) b[1] << 8);
        m &= 0xffff;
        m |= ((long) b[2] << 16);
        m &= 0xffffff;
        m |= ((long) b[3] << 24);
        m &= 0xffffffffl;
        m |= ((long) b[4] << 32);
        m &= 0xffffffffffl;
        m |= ((long) b[5] << 40);
        m &= 0xffffffffffffl;
        m |= ((long) b[6] << 48);
        m &= 0xffffffffffffffl;
        m |= ((long) b[7] << 56);
        return Double.longBitsToDouble(m);
    }

    /**
     * float到字节数组的转换.
     */
    public void floatToByte(float x) {
        // 先用 Float.floatToIntBits(f)转换成int
    }

    /**
     * 字节数组到float的转换.
     */
    public float getFloat(byte[] b) {
        // 4 bytes
        int accum = 0;
        for (int shiftBy = 0; shiftBy < 4; shiftBy++) {
            accum |= (b[shiftBy] & 0xff) << shiftBy * 8;
        }
        return Float.intBitsToFloat(accum);
    }

    /**
     * char到字节数组的转换.
     */
    public byte[] charToByte(char c) {
        byte[] b = new byte[2];
        b[0] = (byte) ((c & 0xFF00) >> 8);
        b[1] = (byte) (c & 0xFF);
        return b;
    }

    /**
     * 字节数组到char的转换.
     */
    public char byteToChar(byte[] b) {
        char c = (char) (((b[0] & 0xFF) << 8) | (b[1] & 0xFF));
        return c;
    }

    /**
     * string到字节数组的转换.
     */
    public byte[] stringToByte(String str)
            throws UnsupportedEncodingException {
        byte[] bytes;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            bytes = str.getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = str.getBytes("utf-8");
        }

        return bytes;
    }

    /**
     * 字节数组到String的转换.
     */
    public String bytesToString(byte[] str) {
        String keyword = null;
        try {
            keyword = new String(str, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return keyword;
    }

    // 字节数组到object的转换.
    public Object ByteToObject(byte[] bytes) {
        Object obj = null;
        try {
            // bytearray to object
            ByteArrayInputStream bi = new ByteArrayInputStream(bytes);
            ObjectInputStream oi = new ObjectInputStream(bi);

            obj = oi.readObject();

            bi.close();
            oi.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }

    // object到字节数组的转换
    public byte[] ObjectToByte(Object obj) {
        byte[] bytes = null;
        try {
            // object to bytearray
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.writeObject(obj);

            bytes = bo.toByteArray();

            bo.close();
            oo.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytes;
    }
}
