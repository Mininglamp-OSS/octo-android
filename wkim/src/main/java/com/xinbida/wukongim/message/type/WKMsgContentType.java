package com.xinbida.wukongim.message.type;

/**
 * 2019-11-11 09:47
 * 消息内容类型
 */
public class WKMsgContentType {
    //文本
    public static final int WK_TEXT = 1;
    //图片
    public static final int WK_IMAGE = 2;
    //GIF
    public static final int WK_GIF = 3;
    //语音
    public static final int WK_VOICE = 4;
    //视频
    public static final int WK_VIDEO = 5;
    //位置
    public static final int WK_LOCATION = 6;
    //名片
    public static final int WK_CARD = 7;
    //文件
    public static final int WK_FILE = 8;
    //合并转发消息
    public static final int WK_MULTIPLE_FORWARD = 11;
    //矢量贴图
    public static final int WK_VECTOR_STICKER = 12;
    //emoji 贴图
    public static final int WK_EMOJI_STICKER = 13;
    // content 格式错误
    public static final int WK_CONTENT_FORMAT_ERROR = 97;
    // signal 解密失败
    public static final int WK_SIGNAL_DECRYPT_ERROR = 98;
    //内部消息，无需存储到数据库
    public static final int WK_INSIDE_MSG = 99;
}
