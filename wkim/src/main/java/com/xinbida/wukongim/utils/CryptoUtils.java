package com.xinbida.wukongim.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.xinbida.wukongim.WKIMApplication;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * 2/25/21 6:20 PM
 * 消息加密处理
 */
public class CryptoUtils {
    private final String TAG = "CryptoUtils";
    private byte[] privateKey, publicKey;
    private byte[] serverKey;
    private String aesKey;
    private String salt;
    private static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    private CryptoUtils() {
    }

    private static class CryptoUtilsBinder {
        private final static CryptoUtils util = new CryptoUtils();
    }

    public static CryptoUtils getInstance() {
        return CryptoUtilsBinder.util;
    }

    public void initKey() {
        Curve25519KeyPair keyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair();
        privateKey = keyPair.getPrivateKey();
        publicKey = keyPair.getPublicKey();
    }

    public String getPublicKey() {
        return Base64.encodeToString(publicKey, Base64.NO_WRAP);
    }

    /**
     * 设置服务端公钥和安全码
     *
     * @param serverKey 公钥
     * @param salt      安全码
     */
    public void setServerKeyAndSalt(String serverKey, String salt) {

        if (TextUtils.isEmpty(serverKey) || TextUtils.isEmpty(salt)) {
            this.serverKey = new byte[0];
            this.salt = "";
            return;
        }
        this.serverKey = base64Decode(serverKey);
        this.salt = salt;

        Curve25519 cipher = Curve25519.getInstance(Curve25519.BEST);
        byte[] sharedSecret = cipher.calculateAgreement(this.serverKey, privateKey);
        String key = digestMD5(base64Encode(sharedSecret));
        if (!TextUtils.isEmpty(key) && key.length() > 16) {
            aesKey = key.substring(0, 16);
        }
    }

    public byte[] aesEncrypt(String sSrc) {

        Cipher cipher;
        byte[] encrypted = null;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            byte[] raw = aesKey.getBytes(CHARSET_UTF8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            //使用CBC模式，需要一个向量iv，可增加加密算法的强度
            IvParameterSpec iv = new IvParameterSpec(salt.getBytes(CHARSET_UTF8));
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            encrypted = cipher.doFinal(sSrc.getBytes());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException |
                 BadPaddingException e) {
           WKLoggerUtils.getInstance().e(TAG,"aesEncrypt encrypt error");
           return null;
        }
        if (encrypted == null) {
            WKLoggerUtils.getInstance().e(TAG, "aesEncrypt The encrypted data is empty");
            encrypted = sSrc.getBytes();
        }
        return encrypted;
    }

    /**
     * 解密
     *
     * @param sSrc 内容
     * @return 内容
     */
    public String aesDecrypt(byte[] sSrc) {
        byte[] raw = aesKey.getBytes(CHARSET_UTF8);
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher;
        String content = "";
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec iv = new IvParameterSpec(salt.getBytes(CHARSET_UTF8));
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            //先用base64解密
            byte[] original = cipher.doFinal(sSrc);
            content = new String(original, CHARSET_UTF8);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "aesDecrypt Decryption error");
        }

        return content;
    }


    public byte[] base64Decode(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }

    /**
     * 将 字节数组 转换成 Base64 编码
     */
    public String base64Encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public String digestMD5(String password) {
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

    public boolean checkRSASign(String content, String sign) {
        try {
            String publicKey = WKIMApplication.getInstance().getRSAPublicKey();
            byte[] keyByte = base64Decode(publicKey);
            String key = new String(keyByte);
            key = key.replace("-----BEGIN PUBLIC KEY-----", "");
            key = key.replace("-----END PUBLIC KEY-----", "");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(base64Decode(key));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicK = keyFactory.generatePublic(keySpec);
            Signature signature = Signature.getInstance("MD5withRSA");
            signature.initVerify(publicK);
            signature.update(content.getBytes());
            boolean result = signature.verify(base64Decode(sign));
            return result;
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException |
                 InvalidKeySpecException e) {
            e.printStackTrace();
            return false;
        }
    }
}
