package com.chat.base.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import java.security.MessageDigest;

/**
 * 2020-09-07 17:37
 */
public class WKCommonUtils {
    private WKCommonUtils() {
    }

    private static class CommonUtilsBinder {
        final static WKCommonUtils utils = new WKCommonUtils();
    }

    public static WKCommonUtils getInstance() {
        return CommonUtilsBinder.utils;
    }


    public synchronized String getPackageName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);
            return packageInfo.packageName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 将 Base64 字符串 解码成 字节数组
     */
    public  byte[] base64Decode(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }

    /**
     * 将 字节数组 转换成 Base64 编码
     */
    public  String base64Encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static String digest(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int c = b & 0xff; //负数转换成正数
                String result = Integer.toHexString(c); //把十进制的数转换成十六进制的书
                if (result.length() < 2) {
                    sb.append(0); //让十六进制全部都是两位数
                }
                sb.append(result);
            }
            return sb.toString(); //返回加密后的密文
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }
}
