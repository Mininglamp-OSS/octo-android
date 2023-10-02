package com.chat.base.entity;

/**
 * 2020-06-30 16:41
 * 用户信息
 */
public class UserInfoEntity {
    public String token;
    public String uid;
    public String username;
    public String name;
    public String im_token;
    public String short_no;//显示的id号
    public int short_status;//是否已经设置ID
    public int sex;
    public String zone;//区号
    public String phone;//手机号
    public String avatar;
    public int server_id;
    public String chat_pwd;//聊天密码
    public String lock_screen_pwd;//锁屏密码
    public int lock_after_minute;
    public String rsa_public_key;
    public int msg_expire_second;
    public UserInfoSetting setting;

}
